package com.yuan.msyuanbackend.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.yuan.msyuanbackend.common.BaseResponse;
import com.yuan.msyuanbackend.common.ResultUtils;
import com.yuan.msyuanbackend.model.vo.SignInCalendarVO;
import com.yuan.msyuanbackend.model.vo.SignInResultVO;
import com.yuan.msyuanbackend.service.SignInService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 刷题签到接口
 */
@RestController
@RequestMapping("/sign")
@Slf4j
public class SignInController {
    @Resource
    private SignInService signInService;
    /**
     * 刷题签到（幂等：今天签过了再点也只返回一次结果，不会重复加天数）
     */
    @PostMapping("/in")
    public BaseResponse<SignInResultVO> signIn(HttpServletRequest request) {
        // 先显式检查登录：没登录时抛出 NotLoginException，被全局异常处理器转成 40100，前端会自动跳登录页
        StpUtil.checkLogin();
        return ResultUtils.success(signInService.signIn(request));
    }

    /**
     * 刷题日历
     *
     * @param month 月份，格式 yyyy-MM，不传就是本月
     */
    @GetMapping("/calendar")
    public BaseResponse<SignInCalendarVO> getSignCalendar(@RequestParam(required = false) String month,
                                                          HttpServletRequest request) {
        StpUtil.checkLogin();
        return ResultUtils.success(signInService.getSignCalendar(month, request));
    }
}
