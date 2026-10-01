package com.yuan.msyuanbackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.IService;
import com.yuan.msyuanbackend.model.dto.question.QuestionQueryRequest;
import com.yuan.msyuanbackend.model.entity.Question;
import com.yuan.msyuanbackend.model.vo.QuestionVO;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

public interface QuestionService extends IService<Question>  {
    /**
     * 校验数据
     */
    void validQuestion(Question question,boolean add);

    /**
     * 获取查询条件
     * @param questionQueryRequest
     * @return
     */
    QueryWrapper<Question> getQueryWrapper(QuestionQueryRequest questionQueryRequest);

    /**
     * 获取题目封装
     * @param question
     * @param request
     * @return
     */
    QuestionVO getQuestionVO(Question question, HttpServletRequest request);
    /**
     * 分页获取题目列表
     *
     * @param questionPage
     * @return
     */
    Page<QuestionVO> getQuestionVOPage(Page<Question> questionPage, HttpServletRequest request);
    /**
     * 分页获取题目列表
     *
     * @param questionQueryRequest
     * @return
     */
    Page<Question> listQuestionByPage(QuestionQueryRequest questionQueryRequest);

    /**
     * 从 ES 查询题目
     *
     * @param questionQueryRequest
     * @return
     */
    Page<Question> searchFromEs(QuestionQueryRequest questionQueryRequest);

    /**
     * 批量删除题目
     *
     * @param questionIdList
     */

    void batchDeleteQuestions(List<Long> questionIdList);
}
