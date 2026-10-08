package com.yuan.msyuanbackend.manager;

import cn.hutool.core.collection.CollUtil;
import com.yuan.msyuanbackend.config.RedisConfig;
import jakarta.annotation.Resource;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 刷题签到 Manager（BitMap 核心逻辑）
 * <p>
 * 为什么用 BitMap 而不是数据库记一条"签到流水"？
 * - 一个用户一年 365 天只需要 365 个 bit ≈ 46 字节，100 万用户一年也才 45 MB 左右；
 * - 数据库存流水的话，100 万用户一年就是 3.65 亿行，光索引就撑不住；
 * - BITCOUNT / GETBIT / SETBIT 都是位运算，天然快。
 * <p>
 * 存储设计：
 * - key    = msyuan:sign:{userId}     （一个用户一个 BitMap，不按年拆，方便跨年统计连续天数）
 * - offset = LocalDate.toEpochDay()   （1970-01-01 起第几天，全球唯一，不用自己算闰年）
 * - 值     = 1 表示这天签到过，0 表示没签
 * <p>
 * 实现上刻意只用了三样最稳定的 API：StringRedisTemplate、Lua 脚本、脚本返回值解析。
 * 没用 BitFieldSubCommands 那套链式 API（不同大版本之间改过写法），避免以后升级踩坑。
 */
@Slf4j
@Component
public class SignInManager {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource(name="signInScript")
    private RedisScript<List> signInScript;
    @Resource(name="signInQueryScript")
    private RedisScript<List> signInQueryScript;

    /**
     * 签到统计结果
     */
    @Data
    public static class SignInStat{
        /** 本次是不是"今天第一次签"，重复签到为 false（接口是幂等的） */
        private boolean firstSign;
        /** 本月签到天数 */
        private int signedCount;
        /** 连续签到天数（截止到今天） */
        private int continuousDays;
        /** 今天是否已签到 */
        private boolean todaySigned;
    }

    /**
     * 今天签到（幂等：今天签过了再点，不会重复加天数）
     */
    public SignInStat addSignInToday(long userId)
    {
        LocalDate today = LocalDate.now(); //这里取参数
        YearMonth month = YearMonth.from(today);
        List<?> result = execute(signInScript,userId,today,month);
        SignInStat stat = new SignInStat();
        if (result.size() < 3) {
            log.error("签到 Lua 返回结果异常：userId={}, result={}", userId, result);
            return stat;
        }
        stat.setFirstSign(toLong(result.get(0))==0L);
        stat.setSignedCount(Long.bitCount(toLong(result.get(2))));
        stat.setContinuousDays((int) toLong(result.get(1)));
        stat.setTodaySigned(true);
        return stat;
    }
    /**
     * 查某个月的签到统计（连续天数、本月天数、今天是否已签）
     *
     * @param month 要统计的月份；"连续天数"和"今天是否已签"始终相对"今天"，与 month 无关
     */
    public SignInStat getMonthStat(long userId,YearMonth month)
    {
        LocalDate today = LocalDate.now();
        List<?> result = execute(signInQueryScript,userId,today,month);
        SignInStat stat = new SignInStat();
        if(result.size()>=2)
        {
            stat.setContinuousDays((int) toLong(result.get(0)));
            stat.setSignedCount(Long.bitCount(toLong(result.get(1))));
        }
        // 连续天数是从"今天"开始往回数的：今天没签，连续天数必然是 0
        stat.setTodaySigned(stat.getContinuousDays() > 0);
        return stat;
    }
    /**
     * 某个月签到的是哪几天，返回 [1, 3, 5] 这种日号列表，前端直接照着画日历
     */
    public List<Integer> getMonthSignedDays(long userId,YearMonth month)
    {
        long bits = getMonthBits(userId,month);
        int length = month.lengthOfMonth();
        List<Integer> days = new ArrayList<>();
        for (int day = 1; day <=length ; day++) {
            //redis的bit是高位在前 偏移最小的位权重最大
            //所以一号对那个的最高位 第length-1 位 最后一天对应最低位
            if(((bits >>> (length-day)) &1L)==1L)
            {
                days.add(day);
            }
        }
        return days;
    }
    /**
     * 某一年的签到记录：返回"这一年的第几天"，例如 [1, 2, 90]
     */
    public List<Integer> getYearSignedRecord(long userId,int year)
    {
        List<Integer> record = new ArrayList<>();
        //遍历月
        for (int month = 1; month <=12; month++) {
            YearMonth yearMonth = YearMonth.of(year,month); //找到哪一年的月
            for (Integer day : getMonthSignedDays(userId, yearMonth)) {//遍历月的天 再找到
                record.add(LocalDate.of(year,month,day).getDayOfYear());//记录是哪天
            }
        }
        return record;
    }

    //私有工具
    /**
     * 一次取出某个月整段位图的十进制值
     * 复用只读脚本 脚本本来接收 “月份起始bit+天数” 查里历史月份也能用
     * 脚本连续天数计算对历史月份是多余的，但是一次网络往返能拿到位图
     */
    private long getMonthBits(long userId,YearMonth month)
    {
        LocalDate today = LocalDate.now();
        List<?> result = execute(signInQueryScript,userId,today,month);
        if (result.size() < 2) {
            return 0L;
        }
        return toLong(result.get(1));
    }
    private List<?> execute(RedisScript<List> script,long userId,LocalDate today,YearMonth month){
        List<?> result = stringRedisTemplate.execute(script,
                Collections.singletonList(buildKey(userId)),
                String.valueOf(today.toEpochDay()),
                String.valueOf(month.atDay(1).toEpochDay()),
                String.valueOf(month.lengthOfMonth()));
        if (CollUtil.isEmpty(result)) {
            log.error("签到脚本返回空结果：userId={}, month={}", userId, month);
            return Collections.emptyList();
        }
        return result;
    }
    private String buildKey(long userId) {
        return RedisConfig.SIGN_IN_KEY_PREFIX + userId;
    }
    /**
     * 兜底解析：Lua 返回的数字正常会解析成 Long；
     * 万一被反序列化成字符串（序列化器配置不同），也能兜住，不至于线上直接崩。
     */
    private static long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
