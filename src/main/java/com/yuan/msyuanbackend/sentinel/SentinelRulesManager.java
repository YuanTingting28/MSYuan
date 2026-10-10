package com.yuan.msyuanbackend.sentinel;

import cn.hutool.core.io.FileUtil;
import com.alibaba.csp.sentinel.datasource.*;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.slots.block.degrade.circuitbreaker.CircuitBreakerStrategy;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRuleManager;
import com.alibaba.csp.sentinel.transport.util.WritableDataSourceRegistry;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 启动时把限流规则、熔断规则加载进内容
 * 把规则持久化到本地文件 sentinel/ParamFlowRule.json sentinel/DegradeRule.json
 * 监听文件变化，改了文件规则自动生效
 生产上更常见的是存 Nacos / Apollo，本地文件是最小可用版本。
 */
@Slf4j
@Component
public class SentinelRulesManager {
    @PostConstruct
    public void initRules() throws Exception{
        String rootPath = System.getProperty("user.dir");
        File sentinelDir = new File(rootPath,"sentinel");
        if(!FileUtil.exist(sentinelDir))
        {
            FileUtil.mkdir(sentinelDir);
        }
        //文件不存在就先写入默认规则
        File paramFile = new File(sentinelDir,"ParamFlowRule.json");
        File degradeFile = new File(sentinelDir,"DegradeRule.json");
        if(!paramFile.exists())
        {
            FileUtil.writeUtf8String(encodeJson(buildDefaultParamFlowRules()),paramFile);
        }
        if(!degradeFile.exists())
        {
            FileUtil.writeUtf8String(encodeJson(buildDefaultDegradeRules()),degradeFile);
        }
        listenRules();
        initFlowRules();
        initDegradeRules();
    }
    /**
     * 限流规则：每IP每分钟60次
     * ParamFlowRule是【热点参数限流】可以按方法的某个参数的值分别去统计。
     * 这里paramIdx = 0 也就是按第0个参数 我们传的是IP分别限流
     */
    public void initFlowRules() {
        ParamFlowRuleManager.loadRules(buildDefaultParamFlowRules());
    }

    /**
     * 统计30秒内，超过3秒的的请求占比 > 20% 熔断60秒
     * 异常比例：统计30秒内，异常占比>10%
     */
    public void initDegradeRules() {
        DegradeRuleManager.loadRules(buildDefaultDegradeRules());
    }
    private List<ParamFlowRule> buildDefaultParamFlowRules() {
        ParamFlowRule rule = new ParamFlowRule(SentinelConstant.listQuestionVOByPage)
                .setParamIdx(0)          // 第 0 个参数就是 IP
                .setCount(60)            // 每个 IP 每分钟 60 次
                .setDurationInSec(60);
        return Collections.singletonList(rule);
    }

    private List<DegradeRule> buildDefaultDegradeRules() {
        DegradeRule slowCallRule = new DegradeRule(SentinelConstant.listQuestionVOByPage)
                .setGrade(CircuitBreakerStrategy.SLOW_REQUEST_RATIO.getType())
                .setCount(3000)              // ★ RT 阈值：超过 3000ms 算慢调用
                .setSlowRatioThreshold(0.2)  // ★ 慢调用比例超过 20% 就熔断
                .setTimeWindow(60)
                .setStatIntervalMs(30 * 1000)
                .setMinRequestAmount(10);

        DegradeRule errorRateRule = new DegradeRule(SentinelConstant.listQuestionVOByPage)
                .setGrade(CircuitBreakerStrategy.ERROR_RATIO.getType())
                .setCount(0.1)               // 异常比例超过 10% 熔断
                .setTimeWindow(60)
                .setStatIntervalMs(30 * 1000)
                .setMinRequestAmount(10);

        return Arrays.asList(slowCallRule, errorRateRule);
    }
    /**
     * 规则持久化到本地文件+文件变化自动刷新
     */
    public void listenRules() throws Exception{
        String rootPath = System.getProperty("user.dir");
        File sentinelDir = new File(rootPath,"sentinel");
        if(!FileUtil.exist(sentinelDir))
        {
            FileUtil.mkdir(sentinelDir);
        }
        String paramFlowRulePath = new File(sentinelDir,"ParamFlowRule.json").getAbsolutePath();
        String degradeRulePath = new File(sentinelDir,"DegradeRule.json").getAbsolutePath();
        // ===== 热点参数限流规则 =====
        // 只做「可读」：文件改了规则自动生效（热更新）
        ReadableDataSource<String, List<ParamFlowRule>> paramFlowRuleDataSource =
                new FileRefreshableDataSource<>(paramFlowRulePath, paramFlowRuleListParser);
        ParamFlowRuleManager.register2Property(paramFlowRuleDataSource.getProperty());

        // ===== 熔断规则 =====
        // 可读：文件变了自动重载
        ReadableDataSource<String, List<DegradeRule>> degradeRuleDataSource =
                new FileRefreshableDataSource<>(degradeRulePath, degradeRuleListParser);
        DegradeRuleManager.register2Property(degradeRuleDataSource.getProperty());
        // 可写：控制台改了规则，写回文件持久化
        WritableDataSource<List<DegradeRule>> degradeWds =
                new FileWritableDataSource<>(degradeRulePath, this::encodeJson);
        WritableDataSourceRegistry.registerDegradeDataSource(degradeWds);
    }
    private final Converter<String, List<ParamFlowRule>> paramFlowRuleListParser =
            source -> JSON.parseObject(source, new TypeReference<List<ParamFlowRule>>() {});

    private final Converter<String, List<DegradeRule>> degradeRuleListParser =
            source -> JSON.parseObject(source, new TypeReference<List<DegradeRule>>() {});

    private <T> String encodeJson(T t) {
        return JSON.toJSONString(t);
    }

}
