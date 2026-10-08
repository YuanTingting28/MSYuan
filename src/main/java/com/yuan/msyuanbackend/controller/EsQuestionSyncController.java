package com.yuan.msyuanbackend.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.yuan.msyuanbackend.common.BaseResponse;
import com.yuan.msyuanbackend.common.ResultUtils;
import com.yuan.msyuanbackend.constant.UserConstant;
import com.yuan.msyuanbackend.manager.QuestionEsSyncManager;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/es/question")
@Slf4j
public class EsQuestionSyncController {
    @Resource
    private QuestionEsSyncManager questionEsSyncManager;

    /**
     * 创建索引（含 ik 分词 mapping，已存在就跳过）
     */
    @PostMapping("/init")
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> initIndex() {
        return ResultUtils.success(questionEsSyncManager.initIndex());
    }

    /**
     * 全量同步（幂等，可以反复调）
     */
    @PostMapping("/sync/full")
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    public BaseResponse<Integer> fullSync() {
        return ResultUtils.success(questionEsSyncManager.fullSync());
    }

    /**
     * 增量同步（定时任务调的就是它，手动调一般是为了联调）
     */
    @PostMapping("/sync/incr")
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    public BaseResponse<Integer> incrSync() {
        return ResultUtils.success(questionEsSyncManager.incrSync());
    }

    /**
     * 重建索引（先删索引再全量，改过 mapping 之后用它）
     */
    @PostMapping("/rebuild")
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    public BaseResponse<Integer> rebuildIndex() {
        return ResultUtils.success(questionEsSyncManager.rebuildIndex());
    }
}
