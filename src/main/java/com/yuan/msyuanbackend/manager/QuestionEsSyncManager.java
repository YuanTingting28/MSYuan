package com.yuan.msyuanbackend.manager;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yuan.msyuanbackend.esdao.QuestionEsDao;
import com.yuan.msyuanbackend.mapper.QuestionMapper;
import com.yuan.msyuanbackend.model.entity.Question;
import com.yuan.msyuanbackend.service.QuestionService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 题目ES同步Manager
 * 负责编排：数据库-》ES 的全量同步、增量同步 以及单条实时同步
 * 只管流程 不写ES的细节
 */

@Slf4j
@Service
public class QuestionEsSyncManager {
    /**
     * 每批同步多少条
     */
    @Value("${es.sync.batch-size:500}")
    private int batchSize;

    @Resource
    private QuestionService questionService;
    @Resource
    private QuestionMapper questionMapper;
    @Resource
    private QuestionEsDao questionEsDao;
    /**
     * 建索引（已存在就跳过 含ik分词mapping
     */
    public boolean initIndex()
    {
        questionEsDao.createIndexIfAbsent();
        return questionEsDao.indexExists();
    }
    /**
     * 全量同步 把question表里没删除的题目全部刷进 ES
     * 用primary key分页 避免一次把所有数据读进内存
     * return 同步条数
     */
    public int fullSync()
    {
        questionEsDao.createIndexIfAbsent();
        long pageNo = 1;
        int total = 0;
        while (true)
        {
            Page<Question> page = questionService.page(new Page<>(pageNo,batchSize),
                    Wrappers.<Question>lambdaQuery().orderByAsc(Question::getId));
            List<Question> records = page.getRecords();
            if (CollUtil.isEmpty(records)) {
                break;
            }
            total += questionEsDao.bulkIndex(records);
            if (records.size() < batchSize) {
                break;
            }
            pageNo++;
        }
        log.info("题目全量同步到 ES 完成，共同步 {} 条", total);
        return total;
    }

    /**
     * 重建索引：先删掉索引再全量，mapping 改过之后用它
     */
    public int rebuildIndex() {
        questionEsDao.deleteIndex();
        return fullSync();
    }

    /**
     * 增量同步
     * 水位线直接取ES里最大的updateTime 不用额外的同步记录表
     * ES自己就是进度表 服务重启、换机器都不会丢进度，坏处是依赖ES的聚合查询
     * return 本次处理的条数
     */
    public int incrSync()
    {
        if(!questionEsDao.indexExists())
        {
            log.info("ES 索引不存在，转为全量同步");
            return fullSync();
        }
        Long maxUpdateTime = questionEsDao.getMaxUpdateTime();
        if (maxUpdateTime == null)
        {
            log.info("ES 索引里还没有数据，转为全量同步");
            return fullSync();
        }
        //水位线回退 1 秒，防止 datetime 秒级精度导致的漏数据（重复同步是幂等的，不怕）
        int changed = 0;
        long cursorTime = maxUpdateTime - 1000L;
        long cursorId = 0L;
        while(true)
        {
            List<Question> list = questionMapper.listChangeAfter(new Date(cursorTime),cursorId,batchSize);
            if(CollUtil.isEmpty(list))
            {
                break;
            }
            questionEsDao.bulkIndex(list);
            changed += list.size();
            Question last = list.get(list.size()-1);
            cursorTime = last.getUpdateTime().getTime();
            cursorId = last.getId();
            if(list.size()<batchSize)
            {
                break;
            }
        }
        // 逻辑删除的题目，从 ES 里同步删掉
        int deleted = 0;
        long delCursorTime = maxUpdateTime-1000L;
        long delCursorId = 0L;
        while (true)
        {
            List<Question> list = questionMapper.listDeletedAfter(new Date(delCursorTime), delCursorId, batchSize);
            if (CollUtil.isEmpty(list)) {
                break;
            }
            List<Long> idList = list.stream().map(Question::getId).collect(Collectors.toList());
            questionEsDao.bulkDelete(idList);
            deleted += idList.size();
            Question last = list.get(list.size() - 1);
            delCursorTime = last.getUpdateTime().getTime();
            delCursorId = last.getId();
            if (list.size() < batchSize) {
                break;
            }
        }
        if (changed > 0 || deleted > 0) {
            log.info("题目增量同步到 ES 完成，新增/修改 {} 条，删除 {} 条", changed, deleted);
        }
        return changed + deleted;
    }
    /**
     * 单条实时同步：题目新增 / 修改 / 删除后立刻调它，搜索就能马上看到变化
     * <p>
     * 注意：查不到说明被逻辑删除了，那就把 ES 里的文档删掉
     */
    public void syncById(Long questionId)
    {
        if(questionId==null)
        {
            return;
        }
        try{
            Question question = questionService.getById(questionId);
            if (question == null) {
                questionEsDao.bulkDelete(Collections.singletonList(questionId));
                return;
            }
            questionEsDao.bulkIndex(Collections.singletonList(question)); //刷新进搜索结果
        } catch (Exception e) {
            // 搜索是"锦上添花"：ES 挂了不能让新增/修改题目失败。
            // 记一条日志，等 ES 恢复后，定时增量任务会把数据补上（幂等，重复同步没关系）
            log.error("单条同步题目 [{}] 到 ES 失败，不影响主流程", questionId, e);
        }
    }
    /**
     * 批量实时同步，删除场景专用
     * 逻辑删除的题目在MySql里查不到了，直接按id从ES里批量删除，一次请求搞定
     */
    public void syncBatchDelete(List<Long> questionIdList)
    {
        if(CollUtil.isEmpty(questionIdList))
        {
            return;
        }
        try{
            questionEsDao.bulkDelete(questionIdList);
        } catch (Exception e) {
            log.error("批量同步删除题目到 ES 失败，条数 {}，不影响主流程", questionIdList.size(), e);
        }
    }
}
