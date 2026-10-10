package com.yuan.msyuanbackend.sentinel;

import com.alibaba.csp.sentinel.annotation.aspectj.SentinelResourceAspect;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Sentinel 配置
 * <p>
 * Boot 3/4 下 spring-cloud-starter-alibaba-sentinel 用不了，
 * 所以我们手动把 @SentinelResource 的切面注册进来。
 * <p>
 * 注意：Sentinel 默认**不会**把 Spring MVC 的请求自动变成资源，
 * 要自动统计所有接口得额外接 sentinel-spring-webmvc-adapter。
 * 这里我们只用「手动埋点 + 注解」两种方式，够用了。
 */
@Configuration
public class SentinelConfig {
    @Bean
    public SentinelResourceAspect sentinelResourceAspect()
    {
        return new SentinelResourceAspect();
    }

}
