package com.yuan.msyuanbackend.esdao;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yuan.msyuanbackend.common.ErrorCode;
import com.yuan.msyuanbackend.exception.BusinessException;
import com.yuan.msyuanbackend.model.dto.question.QuestionEsDTO;
import com.yuan.msyuanbackend.model.dto.question.QuestionQueryRequest;
import com.yuan.msyuanbackend.model.entity.Question;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 幂等性：用数据库主键作为 ES 文档 id，重复写入即覆盖，增量同步可重放。
 *
 * 水位线机制：getMaxUpdateTime() 让 ES 自身充当同步进度表，无需额外存储。
 *
 * 容错性：几乎每个外部调用都有异常捕获和日志，不会因单点失败拖垮整个流程。
 *
 * 中文检索优化：IK 分词器 + 大小写过滤器 + 标题权重提升。
 *
 * 双 Mapper 分离：JSON_MAPPER 只用于 tags 的 JSON 转换，与 ES 客户端内部 mapper 无关，职责清晰。
 */
@Slf4j
@Repository
public class QuestionEsDao {
    /**
     * 只在「数据库 json 字符串」和「List&lt;String&gt;」之间转换时用，和 ES 客户端自己的 mapper 无关
     */
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();
    /**
     * 超过 10000 条就是 ES 的深分页限制了，交给数据库兜底
     */
    public static final int MAX_RESULT_WINDOW = 10000;
    /**
     * 题目索引的 settings + mappings
     * <p>
     *     关键点:
     *     1.ik_max_word 建立索引 细粒度，尽量多切词 提高召回
     *     2.加lowercase过滤器，让Java/java/JAVA 都能搜索到
     *     3.tags用keyword 标签是精确值，不需要分词，还能做聚合
     *     4.时间段用date+毫秒时间戳写入，避开失去问题
     * </p>
     */
    private static final String INDEX_CREATE_BODY = """
            {
            "settings": {
                "number_of_shards": 1,
                "number_of_replicas": 0,
                "analysis": {
                  "analyzer": {
                    "ik_index_analyzer": {
                      "type": "custom",
                      "tokenizer": "ik_max_word",
                      "filter": ["lowercase"]
                    },
                    "ik_search_analyzer": {
                      "type": "custom",
                      "tokenizer": "ik_smart",
                      "filter": ["lowercase"]
                    }
                  }
                }
              },
              "mappings": {
                "properties": {
                  "id": { "type": "long" },
                  "title": {
                    "type": "text",
                    "analyzer": "ik_index_analyzer",
                    "search_analyzer": "ik_search_analyzer",
                    "fields": { "keyword": { "type": "keyword", "ignore_above": 256 } }
                  },
                  "content": {
                    "type": "text",
                    "analyzer": "ik_index_analyzer",
                    "search_analyzer": "ik_search_analyzer"
                  },
                  "answer": {
                    "type": "text",
                    "analyzer": "ik_index_analyzer",
                    "search_analyzer": "ik_search_analyzer"
                  },
                  "tags": { "type": "keyword" },
                  "userId": { "type": "long" },
                  "createTime": { "type": "date" },
                  "editTime": { "type": "date" },
                  "updateTime": { "type": "date" }
                }
              }
            }
            """;
    @Resource
    private ElasticsearchClient elasticsearchClient;

    @Value("${es.question-index:question}")
    private String indexName;
//返回当前使用的ES索引名
    public String getIndexName(){return indexName;}
    //region索引管理 判断索引是否存在
    public boolean indexExists() {
        try {
            return elasticsearchClient.indices().exists(e -> e.index(indexName)).value();
        } catch (Exception e) {
            log.error("查询 ES 索引[{}]是否存在失败，请检查 ES 是否启动: {}", indexName, e.getMessage());
            throw new RuntimeException("ES 连接失败，无法查询索引[" + indexName + "]", e);
        }
    }
    /**
     * 索引不存在时创建幂等
     * 若索引已存在，直接返回。
     * 否则用 withJson 直接提交 INDEX_CREATE_BODY（包含 settings 和 mappings）。
     */
    public void createIndexIfAbsent()
    {
        if (indexExists()){
            return;
        }
        try{
            elasticsearchClient.indices().create(c-> c
                            .index(indexName)
                            .withJson(new StringReader(INDEX_CREATE_BODY)));
        } catch (IOException e) {
            throw new RuntimeException("创建ES索引失败:"+indexName,e);
        }
    }
    /**
     * 删除索引（重建索引时用）
     */
    public void deleteIndex()
    {
        if (!indexExists())
        {
            return;
        }
        try{
            elasticsearchClient.indices().delete(d -> d.index(indexName));
            log.info("ES索引[{}]已删除",indexName);
        } catch (IOException e) {
            throw new RuntimeException("删除ES索引失败:"+indexName,e);
        }
    }

    //end region
    //region 文档同步
    /**
     * 批量写入 同id会覆盖，天然幂等，所以增量同步可以放心重放
     * 空列表直接返回 0。
     * 遍历 Question，通过 toDoc() 转成 QuestionEsDTO。
     * 每个文档构造一个 BulkOperation.index 操作，用数据库主键 id 作为 ES 文档 id。
     * 幂等性：同 id 会覆盖，所以增量同步可放心重放。
     * 最终交给 doBulk() 执行。
     * @return 提交条目
     */
    public int bulkIndex(List<Question> questionList)
    {
        if(CollUtil.isEmpty(questionList))
        {
            return 0;
        }
        List<BulkOperation> operations = new ArrayList<>(questionList.size());
        for (Question question : questionList) {
            QuestionEsDTO doc = toDoc(question);
            operations.add(BulkOperation.of(o->o.index(i->i
                    .index(indexName)
                    .id(String.valueOf(question.getId()))
                    .document(doc))));
        }
        return doBulk(operations);
    }
    public int bulkDelete(List<Long> idList)
    {
        if(CollUtil.isEmpty(idList))
        {
            return 0;
        }
        List<BulkOperation> operations = new ArrayList<>(idList.size());
        for (Long id : idList) {
            operations.add(BulkOperation.of(o -> o.delete(d->d
                    .index(indexName)
                    .id(String.valueOf(id)))));
        }
        return doBulk(operations);
    }
    /**
     * 数据库实体 -》ES文档
     * 数据库实体 → ES 文档：
     * id 为 null 时兜底为 0L。
     * 文本字段用 StrUtil.nullToEmpty 避免 null。
     * tags 通过 parseTags() 从 JSON 字符串转成 List<String>。
     * 三个时间字段通过 toMillis() 转成毫秒时间戳（Long）。
     * 用 Lombok @Builder 构造 QuestionEsDTO。
     */
    public QuestionEsDTO toDoc(Question question)
    {
        return QuestionEsDTO.builder()
                .id(question.getId()==null?0L:question.getId())
                .title(StrUtil.nullToEmpty(question.getTitle()))
                .content(StrUtil.nullToEmpty(question.getContent()))
                .answer(StrUtil.nullToEmpty(question.getAnswer()))
                .tags(parseTags(question.getTags()))
                .userId(question.getUserId() == null ? 0L : question.getUserId())
                .createTime(toMillis(question.getCreateTime()))
                .editTime(toMillis(question.getEditTime()))
                .updateTime(toMillis(question.getUpdateTime()))
                .build();
    }
    //endregion
    //region 检索

    public Page<Question> search(QuestionQueryRequest questionQueryRequest)
    {

        int current = questionQueryRequest.getCurrent();
        int size = questionQueryRequest.getPageSize();
        // 深分页保护：from + size 不能超过 ES 的 max_result_window
        if ((long) current * size > MAX_RESULT_WINDOW) {
            throw new BusinessException(
                    ErrorCode.PARAMS_ERROR,
                    "查询结果超过 " + MAX_RESULT_WINDOW + " 条，请缩小范围或使用更精确的筛选条件");
        }
        int from = (current-1) * size;
//这里是所有查询的过滤
        /**
         * 构造 must 查询条件（都放进 List<Query> must）：
         * 条件	查询类型	说明
         * searchText	multiMatch	跨 title^3、content、answer，标题权重 3 倍
         * title	match	标题单独匹配
         * id	term	精确匹配
         * userId	term	精确匹配
         * tags	terms	标签集合匹配
         * 无过滤条件时：显式用 matchAll，比空 bool.must 更稳。
         */
        List<Query> must = new ArrayList<>();
        String searchText = questionQueryRequest.getSearchText();
        if(StrUtil.isNotBlank(searchText)) //有直接查的就直接查
        {
            must.add(Query.of(q -> q.multiMatch(m->m.query(searchText)
                    .fields("title^3","content","answer"))));
        }
        if(StrUtil.isNotBlank(questionQueryRequest.getTitle())){
            must.add(Query.of(q->q.match(m->m
                    .field("title")
                    .query(questionQueryRequest.getTitle()))));
        }
        Long id = questionQueryRequest.getId();
        if(id!=null)
        {
            long idValue = id;
            must.add(Query.of(q->q.term(t->t.field("id").value(idValue))));
        }
        Long userId = questionQueryRequest.getUserId();
        if(userId !=null)
        {
            long userIdValue = userId;
            must.add(Query.of(q->q.term(t->t.field("userId").value(userIdValue))));
        }
        if (CollUtil.isNotEmpty(questionQueryRequest.getTags())) {
            List<FieldValue> tagValues = new ArrayList<>();
            for (String tag : questionQueryRequest.getTags()) {
                tagValues.add(FieldValue.of(tag));
            }
            must.add(Query.of(q -> q.terms(t -> t.field("tags")
                    .terms(f -> f.value(tagValues)))));
        }
        // 没有任何过滤条件时（比如"看最新题目"）显式用 match_all，
        // 比塞一个空的 bool.must 更稳，各版本 ES 都认。
        Query query = must.isEmpty()
                ? Query.of(q -> q.matchAll(m->m))
                : Query.of(q->q.bool(b->b.must(must)));

        // 排序：先按相关度，再按创建时间倒序（相关度相同时新题在前）
        /**
         * 排序：
         *
         * 先按 _score 降序（相关度）。
         *
         * 再按 createTime 降序（相关度相同时新题在前）。
         */
        List<SortOptions> sorts = new ArrayList<>();
        sorts.add(SortOptions.of(s -> s.score(sc -> sc.order(SortOrder.Desc))));
        sorts.add(SortOptions.of(s->s.field(f->f.field("createTime").order(SortOrder.Desc))));

        try{
            SearchResponse<QuestionEsDTO> response = elasticsearchClient.search(s->s
                    .index(indexName)
                    .query(query)
                    .from(from)
                    .size(size)
                    .trackTotalHits(t->t.enabled(true))
                    .sort(sorts),QuestionEsDTO.class);
            long total = response.hits().total() == null ? 0L :response.hits().total().value(); //让total更加精确
            List<Question> records = new ArrayList<>();
            for (Hit<QuestionEsDTO> hit : response.hits().hits()) {
                QuestionEsDTO source = hit.source();
                if(source!=null)
                {
                    records.add(toQuestion(source)); //遍历 hits，把 QuestionEsDTO 通过 toQuestion() 转回 Question。
                }
            }
            Page<Question> questionPage = new Page<>(current, size, total);
            questionPage.setRecords(records);
            return questionPage;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    /**
     * 索引里最大的 updateTime（毫秒时间戳），索引为空返回 null
     * <p>
     * 增量同步就是靠它当水位线：不用额外存同步记录，ES 自己就是"进度表"，重启也不会丢进度。
     * <p>
     * 实现上直接按 updateTime 倒序取第一条，比用 max 聚合少一个 API、也不用管聚合结果的空值。
     */
    public Long getMaxUpdateTime() {
        try {
            SearchResponse<QuestionEsDTO> response = elasticsearchClient.search(s -> s
                    .index(indexName)
                    .size(1)
                    .query(q -> q.matchAll(m -> m))
                    .sort(sort -> sort.field(f -> f
                            .field("updateTime")
                            .order(SortOrder.Desc))), QuestionEsDTO.class);

            List<Hit<QuestionEsDTO>> hits = response.hits().hits();
            if (CollUtil.isEmpty(hits)) {
                return null;
            }
            QuestionEsDTO source = hits.get(0).source();
            return source == null ? null : source.getUpdateTime();
        } catch (IOException e) {
            log.error("获取 ES 最大 updateTime 失败", e);
            return null;
        }
    }

    //私有工具方法

    /**
     * 10. doBulk(List<BulkOperation> operations)
     * 执行 bulk 请求的公共逻辑：
     * 空列表返回 0。
     * 调用 elasticsearchClient.bulk()。
     * 若 response.errors() 为 true，遍历每个 item，对 item.error() != null 的记录错误日志（id、status、原因）。
     * 返回 operations.size()（注意：这是提交条数，不是成功条数，即使部分失败也返回提交总数）。
     * IOException 时记录日志返回 0。
     * @param operations
     * @return
     */
    private int doBulk(List<BulkOperation> operations)
    {
        if (CollUtil.isEmpty(operations))
        {
            return 0;
        }
        try{
            BulkResponse response = elasticsearchClient.bulk(b->b.operations(operations));
            if(response.errors())
            {
                int failed = 0;
                for (BulkResponseItem item : response.items()) {
                    if(item.error()!=null)
                    {
                        failed++;
                        log.error("ES bulk 单条失败，id={},status={},原因={}",item.id(),item.status(),item.error().reason());
                    }
                }
                int success = operations.size()-failed;
                log.warn("ES bulk 部分失败：成功 {} 条，失败 {} 条", success, failed);
                return success;
            }
            return operations.size();
        } catch (IOException e){
            log.error("ES buld 请求失败",e);
            return 0;
        }
    }
    private Question toQuestion(QuestionEsDTO doc){
        Question question = new Question();
        question.setId(doc.getId());
        question.setTitle(doc.getTitle());
        question.setContent(doc.getContent());
        question.setTags(toTagsJson(doc.getTags()));
        question.setAnswer(doc.getAnswer());
        question.setUserId(doc.getUserId());
        question.setEditTime(toDate(doc.getEditTime()));
        question.setCreateTime(toDate(doc.getCreateTime()));
        question.setUpdateTime(toDate(doc.getUpdateTime()));
        question.setIsDelete(0);
        return question;
    }
    private List<String> parseTags(String tagsJson)
    {
        List<String> tags = new ArrayList<>();
        if (StrUtil.isBlank(tagsJson)){
            return tags;
        }
        try{
            JsonNode tagNode = JSON_MAPPER.readTree(tagsJson);
            if (tagNode != null && tagNode.isArray()) {
                tagNode.forEach(tag -> tags.add(tag.asText()));
            }
        }catch (Exception e) {
            log.warn("tags 不是合法的 json 数组，已忽略：{}", tagsJson);
        }
        return tags;
    }
    private String toTagsJson(List<String> tags)
    {
        if(CollUtil.isEmpty(tags))
        {
            return "[]";
        }
        try{
            return JSON_MAPPER.writeValueAsString(tags);
        }catch (Exception e)
        {
            log.warn("tags序列化失败，已忽略：{}",tags);
            return "[]";
        }
    }
    private static Date toDate(Long millis) {
        return millis == null ? null : new Date(millis);
    }

    private static Long toMillis(Date date) {
        return date == null ? null : date.getTime();
    }



}
