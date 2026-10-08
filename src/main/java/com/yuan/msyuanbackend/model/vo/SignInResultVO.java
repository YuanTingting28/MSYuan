package com.yuan.msyuanbackend.model.vo;

import lombok.Data;
/**
 * 签到结果
 */
import java.io.Serializable;
@Data
public class SignInResultVO implements Serializable {
    /**
     * 本次是否属于第一次签到 重复签到返回False
     */
    private Boolean firstSign;
    /**
     * 本月累计签到天数
     */
    private Integer signedCount;
    /**
     * 连续签到天数
     */
    private Integer continuousDays;
    private static final long serialVersionUID = 1L;

}
