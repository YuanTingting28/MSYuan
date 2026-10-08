package com.yuan.msyuanbackend.job;

import com.yuan.msyuanbackend.manager.QuestionEsSyncManager;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 全量同步题目到ES 启动时执行一次，默认关闭
 * 想开的话 在application.yaml 里加：
 * es:
 *   sync:
 *   full-on-startup:true
 */
@Slf4j
@Component
public class FullSyncQuestionToEsJob implements ApplicationRunner {
    @Value("${es.sync.full-on-startup:false}")
    private boolean fullOnStartup;
    @Resource
    private QuestionEsSyncManager questionEsSyncManager;
    @Override
    public void run(ApplicationArguments args) throws Exception {
        if(!fullOnStartup)
        {
            return;
        }
        try{
            questionEsSyncManager.fullSync();
        } catch (Exception e) {
            log.error("启动时全量同步题目到 ES 失败，不影响服务启动", e);
        }
    }
}
