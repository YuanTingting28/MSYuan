package com.yuan.msyuanbackend.service.impl;

import cn.hutool.core.util.StrUtil;
import com.yuan.msyuanbackend.common.ErrorCode;
import com.yuan.msyuanbackend.exception.BusinessException;
import com.yuan.msyuanbackend.exception.ThrowUtils;
import com.yuan.msyuanbackend.manager.SignInManager;
import com.yuan.msyuanbackend.model.entity.User;
import com.yuan.msyuanbackend.model.vo.SignInCalendarVO;
import com.yuan.msyuanbackend.model.vo.SignInResultVO;
import com.yuan.msyuanbackend.service.SignInService;
import com.yuan.msyuanbackend.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * 刷题签到服务实现
 */
@Slf4j
@Service
public class SignInServiceImpl implements SignInService {
    /**
     * 月份格式：和前端约定好的
     */
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    @Resource
    private SignInManager signInManager;

    @Resource
    private UserService userService;

    @Override
    public SignInResultVO signIn(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        SignInManager.SignInStat stat = signInManager.addSignInToday(loginUser.getId());
        SignInResultVO vo = new SignInResultVO();
        vo.setFirstSign(stat.isFirstSign());
        vo.setSignedCount(stat.getSignedCount());
        vo.setContinuousDays(stat.getContinuousDays());
        return vo;
    }

    @Override
    public SignInCalendarVO getSignCalendar(String month, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        YearMonth yearMonth = parseMonth(month);
        List<Integer> signedDays = signInManager.getMonthSignedDays(loginUser.getId(),yearMonth);
        SignInManager.SignInStat stat = signInManager.getMonthStat(loginUser.getId(), yearMonth);
        SignInCalendarVO vo = new SignInCalendarVO();
        vo.setMonth(yearMonth.format(MONTH_FORMATTER));
        vo.setSignedDays(signedDays);
        vo.setSignedCount(signedDays.size());
        vo.setContinuousDays(stat.getContinuousDays());
        vo.setTodaySigned(stat.isTodaySigned());
        return vo;
    }
    /**
     * 解析 yyyy-MM，不传就是本月
     */
    private YearMonth parseMonth(String month) {
        if (StrUtil.isBlank(month)) {
            return YearMonth.now();
        }
        YearMonth yearMonth;
        try {
            yearMonth = YearMonth.parse(month, MONTH_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "月份格式必须是 yyyy-MM，例如 2026-10");
        }
        // 防呆：不允许查未来的月份，省得前端传错参数还以为没数据
        ThrowUtils.throwIf(yearMonth.isAfter(YearMonth.now()), ErrorCode.PARAMS_ERROR, "不能查询未来的月份");
        return yearMonth;
    }
}
