package com.yuan.msyuanbackend.job;

import com.yuan.msyuanbackend.manager.QuestionEsSyncManager;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 增量同步题目到 ES（定时任务）
 * <p>
 * 每 5 分钟跑一次：只同步"上次同步之后被改过的题目"和"被删掉的题目"。
 * 单机这样足够了；如果以后部署多台机器，记得加分布式锁（Redisson / ShedLock），
 * 否则每台机器都会跑一遍（虽然幂等不会脏数据，但浪费资源）。
 */
@Slf4j
@Component
public class IncSyncQuestionToEsJob {
    @Resource
    private QuestionEsSyncManager questionEsSyncManager;

    @Scheduled(cron = "0 0/5 * * * ?")
    public void run() {
        long start = System.currentTimeMillis();
        try {
            questionEsSyncManager.incrSync();
        } catch (Exception e) {
            // 定时任务里一定要把异常吃掉，否则一次失败整个调度就停了
            log.error("增量同步题目到 ES 失败", e);
        }
        log.info("增量同步题目到 ES 结束，耗时 {} ms", System.currentTimeMillis() - start);
    }
}
