package com.yuan.msyuanbackend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

/**
 * Redis 配置 刷题签到用
 * 只做两件事
 * 1.声明 StringRedisTemplate: key/value 都用字符串 取redis-cli 里getbit看数据最直观
 * 2. 把Lua脚本注册成Bean 签到要：写一位 统计连续天数 统计本月天数
 *   放在一个Lua里执行  = 一次网络往返 + 天然原子 + 不会出现并发点两次加两天
 */
@Configuration
public class RedisConfig {
    /**
     * 签到 BitMap 的 key 前缀：msyuan:sign:{userId}
     */
    public static final String SIGN_IN_KEY_PREFIX = "msyuan:sign:";
    /**
     * 签到脚本（会写数据）
     * <p>
     * 返回值是三个数：[旧值, 连续签到天数, 本月位图]
     * 旧值 = 0 说明这一位原来没被点亮，也就是"今天第一次签到"
     */
    public static final String SIGN_IN_LUA = """
            -- KEYS[1] = msyuan:sign:{userId}
            -- ARGV[1] = 今天的 bit 偏移   ARGV[2] = 本月 1 号的 bit 偏移   ARGV[3] = 本月天数
            local key = KEYS[1]
            local offset = tonumber(ARGV[1])
            local monthStart = tonumber(ARGV[2])
            local days = tonumber(ARGV[3])

            -- SETBIT 会返回这一位原来的值：0 表示原来没签过，也就是今天第一次签
            local old = redis.call('SETBIT', key, offset, 1)

            -- 从今天往前逐位读，数连续签到天数（上限 4000 天，防止极端数据把脚本卡住）
            local continuous = 0
            local i = offset
            while i >= 0 and continuous < 4000 do
                if redis.call('GETBIT', key, i) == 1 then
                    continuous = continuous + 1
                    i = i - 1
                else
                    break
                end
            end

            -- 一次把本月整段位取出来（u31 就能装下 31 天），比 31 次 GETBIT 省 30 次网络往返
            local monthBits = redis.call('BITFIELD', key, 'GET', string.format('u%d', days), monthStart)[1]
            if monthBits == false then
                monthBits = 0
            end

            -- 每次签到把 key 的过期时间刷新成 5 年：既保留历史，又不让不活跃用户的 key 永远占内存
            redis.call('EXPIRE', key, 5 * 365 * 24 * 3600)

            return {old, continuous, monthBits}
            """;
    /**
     * 只读版：查签到状态时不写数据（日历、连续天数都用它）
     * <p>
     * 返回值：[连续签到天数, 本月位图]
     */
    public static final String SIGN_IN_QUERY_LUA = """
            -- KEYS[1] = msyuan:sign:{userId}
            -- ARGV[1] = 今天的 bit 偏移   ARGV[2] = 本月 1 号的 bit 偏移   ARGV[3] = 本月天数
            local key = KEYS[1]
            local offset = tonumber(ARGV[1])
            local monthStart = tonumber(ARGV[2])
            local days = tonumber(ARGV[3])

            local continuous = 0
            local i = offset
            while i >= 0 and continuous < 4000 do
                if redis.call('GETBIT', key, i) == 1 then
                    continuous = continuous + 1
                    i = i - 1
                else
                    break
                end
            end

            local monthBits = redis.call('BITFIELD', key, 'GET', string.format('u%d', days), monthStart)[1]
            if monthBits == false then
                monthBits = 0
            end

            return {continuous, monthBits}
            """;
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory)
    {
        return new StringRedisTemplate(connectionFactory);
    }
    @Bean
    public RedisScript<List> signInScript()
    {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setScriptText(SIGN_IN_LUA);
        script.setResultType(List.class);
        return script;
    }
    @Bean
    public RedisScript<List> signInQueryScript() {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setScriptText(SIGN_IN_QUERY_LUA);
        script.setResultType(List.class);
        return script;
    }
}
