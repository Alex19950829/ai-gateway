package com.chatling.engine.governance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 租户/API Key 最大活跃并发长连接管理器 (Max Concurrency Controller)
 * 控制每个租户/凭证同时正在 In-Flight / Streaming 传输中的长连接总数，
 * 避免单个长耗时请求将网关与下游大模型的连接池/显存占满。
 */
@Component
public class ConcurrencyControlManager {
    private static final Logger log = LoggerFactory.getLogger(ConcurrencyControlManager.class);

    // 记录每个 API Key 当前正在活跃连接数
    private final ConcurrentHashMap<String, AtomicInteger> activeConcurrencyMap = new ConcurrentHashMap<>();

    /**
     * 尝试获取一个并发槽位
     * @param apiKey 租户凭证
     * @param maxLimit 最大允许并发数 (若 <= 0 则按默认值 5)
     * @return true 获取成功, false 超限被拒绝
     */
    public boolean acquire(String apiKey, int maxLimit) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return true;
        }
        int effectiveLimit = maxLimit > 0 ? maxLimit : 5;
        AtomicInteger counter = activeConcurrencyMap.computeIfAbsent(apiKey, k -> new AtomicInteger(0));
        
        while (true) {
            int current = counter.get();
            if (current >= effectiveLimit) {
                log.warn("[-] [Concurrency Limit Exceeded] apiKey={}, active={}, limit={}", apiKey, current, effectiveLimit);
                return false;
            }
            if (counter.compareAndSet(current, current + 1)) {
                log.debug("[+] [Concurrency Acquired] apiKey={}, current={}/{}", apiKey, current + 1, effectiveLimit);
                return true;
            }
        }
    }

    /**
     * 释放一个并发槽位 (必须在 doFinally 中保证触发)
     */
    public void release(String apiKey) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return;
        }
        AtomicInteger counter = activeConcurrencyMap.get(apiKey);
        if (counter != null) {
            int updated = counter.decrementAndGet();
            if (updated < 0) {
                counter.set(0);
            }
            log.debug("[-] [Concurrency Released] apiKey={}, remaining={}", apiKey, Math.max(0, updated));
        }
    }

    /**
     * 获取当前活跃并发数
     */
    public int getActiveCount(String apiKey) {
        if (apiKey == null) return 0;
        AtomicInteger counter = activeConcurrencyMap.get(apiKey);
        return counter != null ? Math.max(0, counter.get()) : 0;
    }
}
