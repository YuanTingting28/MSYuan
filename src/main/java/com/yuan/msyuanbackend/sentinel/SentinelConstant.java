package com.yuan.msyuanbackend.sentinel;

/**
 * Sentinel 资源名常量
 * 资源名就是被保护的这块逻辑的名字 限流/熔断都是挂在资源名上的
 */
public interface SentinelConstant {
    /**
     * 分页获取题目列表接口
     */
    String listQuestionVOByPage = "listQuestionVOByPage";
    /**
     * 分页获取题库列表接口
     */
    String listQuestionBankVOByPage = "listQuestionBankVOByPage";
}
