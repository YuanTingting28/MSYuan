package com.yuan.msyuanbackend.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.yuan.msyuanbackend.common.ErrorCode;
import com.yuan.msyuanbackend.constant.CommonConstant;
import com.yuan.msyuanbackend.exception.ThrowUtils;
import com.yuan.msyuanbackend.mapper.QuestionMapper;
import com.yuan.msyuanbackend.model.dto.question.QuestionQueryRequest;
import com.yuan.msyuanbackend.model.entity.Question;
import com.yuan.msyuanbackend.model.entity.QuestionBankQuestion;
import com.yuan.msyuanbackend.model.entity.User;
import com.yuan.msyuanbackend.model.vo.QuestionVO;
import com.yuan.msyuanbackend.model.vo.UserVO;
import com.yuan.msyuanbackend.service.QuestionBankQuestionService;
import com.yuan.msyuanbackend.service.QuestionService;
import com.yuan.msyuanbackend.service.UserService;
import com.yuan.msyuanbackend.utils.SqlUtils;
import io.micrometer.common.util.StringUtils;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 题目服务实现
 */
@Service
@Slf4j
public class QuestionServiceImpl extends ServiceImpl<QuestionMapper, Question> implements QuestionService {

    @Resource
    private UserService userService;
    @Resource
    private QuestionBankQuestionService questionBankQuestionService;
    /**
     * 校验数据
     * @param question
     * @param add 对创建的数据进行校验
     */
    @Override
    public void validQuestion(Question question, boolean add) {
        ThrowUtils.throwIf(question==null, ErrorCode.PARAMS_ERROR);
        //从对象中取值
        String title = question.getTitle();
        String content = question.getContent();
        //创建数据时参数不能为空
        if(add)
        {
            ThrowUtils.throwIf(StringUtils.isBlank(title),ErrorCode.PARAMS_ERROR);
        }
        if(StringUtils.isNotBlank(title))
        {
            ThrowUtils.throwIf(title.length()>80,ErrorCode.PARAMS_ERROR,"标题过长");
        }
        if(StringUtils.isNotBlank(content))
        {
            ThrowUtils.throwIf(content.length()>10240,ErrorCode.PARAMS_ERROR,"内容过长");
        }
    }

    /**
     * 获取查询条件 根据前端传来的各种查询参数 动态拼接SQL的WHERE 和 ORDER BY 条件 最终生成一个 QueryWrapper<Question>对象，供后续查询数据库使用
     * @param questionQueryRequest
     * @return
     */
    @Override
    public QueryWrapper<Question> getQueryWrapper(QuestionQueryRequest questionQueryRequest) {
        QueryWrapper<Question> queryWrapper = new QueryWrapper<>();
        if (questionQueryRequest==null)
        {
            return queryWrapper;
        }
        //从对象中取值
        Long id = questionQueryRequest.getId();
        Long notId = questionQueryRequest.getNotId();
        String searchText = questionQueryRequest.getSearchText();
        String title = questionQueryRequest.getTitle();
        String content = questionQueryRequest.getContent();
        List<String> tagList = questionQueryRequest.getTags();
        String answer = questionQueryRequest.getAnswer();
        Long userId = questionQueryRequest.getUserId();
        String sortField = questionQueryRequest.getSortField();
        String sortOrder = questionQueryRequest.getSortOrder();
        //补充需要的查询条件
        if (StringUtils.isNotBlank(searchText))
        {
            queryWrapper.and(qw->qw.like("title",searchText).or().like("content",searchText));
        }
        queryWrapper.like(org.apache.commons.lang3.StringUtils.isNotBlank(title), "title", title);
        queryWrapper.like(org.apache.commons.lang3.StringUtils.isNotBlank(content), "content", content);
        queryWrapper.like(org.apache.commons.lang3.StringUtils.isNotBlank(answer), "answer", answer);
        if(CollUtil.isNotEmpty(tagList))
        {
            for (String tag : tagList) {
                queryWrapper.like("tags", "\"" + tag + "\"");
            }
        }
        //数值上能够精确查询的
        queryWrapper.ne(ObjectUtils.isNotEmpty(notId), "id", notId);
        queryWrapper.eq(ObjectUtils.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjectUtils.isNotEmpty(userId), "userId", userId);
        //首先通过第一个字段校验排序字段是否合法 然后根据sortOrder判断是升序还是降 最后拼接
        queryWrapper.orderBy(SqlUtils.validSortField(sortField),sortOrder.equals(CommonConstant.SORT_ORDER_ASC),sortField);
        return queryWrapper;
    }
    /**
     * 获取题目封装
     *
     * @param question
     * @param request
     * @return
     */
    @Override
    public QuestionVO getQuestionVO(Question question, HttpServletRequest request) {
        //对象转封装类
        QuestionVO questionVO = QuestionVO.ObjToVo(question);
        //1.关联查询用户信息
        Long userId = question.getUserId();
        User user = null;
        if(userId!=null && userId>0)
        {
            user = userService.getById(userId);
        }
        UserVO userVO = userService.getUserVO(user);
        questionVO.setUser(userVO);
        return questionVO;
    }

    @Override
    public Page<QuestionVO> getQuestionVOPage(Page<Question> questionPage, HttpServletRequest request) {
        List<Question> questionList = questionPage.getRecords();
        Page<QuestionVO> questionVOPage = new Page<>(questionPage.getCurrent(), questionPage.getSize(), questionPage.getTotal());
        if(CollUtil.isEmpty(questionList))
        {
            return questionVOPage;
        }
        //对象列表 ->封装对象列表
        List<QuestionVO> questionVOList = questionList.stream().map(question -> {
            return QuestionVO.ObjToVo(question);
        }).collect(Collectors.toList());

        //1.关联查询用户信息
        Set<Long> userIdSet = questionList.stream().map(Question::getUserId).collect(Collectors.toSet());
        Map<Long,List<User>> userIdUserListMap = userService.listByIds(userIdSet).stream().collect(Collectors.groupingBy(User::getId));
        questionVOList.forEach(questionVO -> {
            Long userId = questionVO.getUserId();
            User user = null;
            if (userIdUserListMap.containsKey(userId)) {
                user = userIdUserListMap.get(userId).get(0);
            }
            questionVO.setUser(userService.getUserVO(user));
        });
        questionVOPage.setRecords(questionVOList);
        return questionVOPage;
    }

    /**
     * 分页获取题目列表
     * @param questionQueryRequest
     * @return
     */
    @Override
    public Page<Question> listQuestionByPage(QuestionQueryRequest questionQueryRequest) {
        int current = questionQueryRequest.getCurrent();
        int size = questionQueryRequest.getPageSize();
        //题目表的查询条件
        QueryWrapper<Question> queryWrapper = this.getQueryWrapper(questionQueryRequest);
        //根据题库 查询题目列表 接口
        Long questionBankId = questionQueryRequest.getQuestionBankId();
        if (questionBankId != null)
        {
            //查询题库内的题目id
            LambdaQueryWrapper<QuestionBankQuestion> lambdaQueryWrapper = Wrappers.lambdaQuery(QuestionBankQuestion.class)
                    .select(QuestionBankQuestion::getQuestionId) //挑选题目的id
                    .eq(QuestionBankQuestion::getQuestionBankId,questionBankId);         //匹配此时题库的id啊
            List<QuestionBankQuestion> questionList = questionBankQuestionService.list(lambdaQueryWrapper);
            if (CollUtil.isNotEmpty(questionList))
            {
                //取出题目id集合
                Set<Long> questionIdSet = questionList.stream()
                        .map(QuestionBankQuestion::getQuestionId)
                        .collect(Collectors.toSet());
                //复用原有题目表的查询条件
                queryWrapper.in("id",questionIdSet);
            }else{
                return new Page<>(current,size,0);
            }
        }
        Page<Question> questionPage = this.page(new Page<>(current,size),queryWrapper);
        return questionPage;
    }

    @Override
    public Page<Question> searchFromEs(QuestionQueryRequest questionQueryRequest) {
        return null;
    }

    /**
     * 批量删除题目
     *
     * @param questionIdList
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void batchDeleteQuestions(List<Long> questionIdList) {
        ThrowUtils.throwIf(CollUtil.isEmpty(questionIdList),ErrorCode.PARAMS_ERROR,"要删除的题目列表不能为空");
        for (Long questionId : questionIdList) {
            boolean result = this.removeById(questionId);
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "删除题目失败");
            // 移除题目题库关系
            //构造查询
            LambdaQueryWrapper<QuestionBankQuestion> lambdaQueryWrapper = Wrappers.lambdaQuery(QuestionBankQuestion.class)
                    .eq(QuestionBankQuestion::getQuestionId, questionId);
            questionBankQuestionService.remove(lambdaQueryWrapper);
        }
    }
}
