package com.yuan.msyuanbackend.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class SignInCalendarVO implements Serializable {
    /**
     * 月份，格式 2026-10
     */
    private String month;

    /**
     * 这个月哪几天签到了，例如 [1, 3, 5]，前端直接按这个画日历
     */
    private List<Integer> signedDays;

    /**
     * 本月签到天数
     */
    private Integer signedCount;

    /**
     * 连续签到天数（截止到今天）
     */
    private Integer continuousDays;

    /**
     * 今天是否已经签到
     */
    private Boolean todaySigned;

    private static final long serialVersionUID = 1L;
}
