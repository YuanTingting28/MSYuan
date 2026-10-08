package com.yuan.msyuanbackend.service;

import com.yuan.msyuanbackend.model.vo.SignInCalendarVO;
import com.yuan.msyuanbackend.model.vo.SignInResultVO;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 刷题签到服务
 */
public interface SignInService {
    /**
     * 签到（幂等：今天签过了再点不会重复加天数）
     */
    SignInResultVO signIn(HttpServletRequest request);
    /**
     * 查刷题日历
     *
     * @param month 月份，格式 2026-10，不传就是本月
     */
    SignInCalendarVO getSignCalendar(String month, HttpServletRequest request);
}
