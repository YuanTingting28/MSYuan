package com.yuan.msyuanbackend.manager;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 简易热点key管理器
 * 探测：redis计数器统计 分钟级 访问量，超过阈值就认定为热key
 * 缓存：热key的值放本地 Guava Cache （JVM)
 * 和真 HotKey 的差距：没有 etcd 推送、没有滑动窗口精细统计、没有集群同步。
 *  * 但“按热度分层缓存”这个核心思想是完整的。
 */
@Slf4j
@Component
public class HotKeyManager {
}
