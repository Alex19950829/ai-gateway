package com.chatling.engine.factor.impl;

import com.chatling.common.factor.IFactorAggregator;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

@Component("SlidingWindowCountAggregator")
public class SlidingWindowCountAggregator implements IFactorAggregator {
    // 内存/Redis 模拟滑动窗口（支持单机与集群）
    private final Map<String, ConcurrentLinkedDeque<Long>> windowMap = new ConcurrentHashMap<>();

    @Override
    public String getAggregatorCode() {
        return "SlidingWindowCountAggregator";
    }

    @Override
    public Object extractValue(String factorCode, Map<String, Object> context) {
        String key = (String) context.getOrDefault("f_consumer_id", "default_consumer");
        long now = System.currentTimeMillis();
        long windowStart = now - 60_000L; // 60s 窗口

        ConcurrentLinkedDeque<Long> deque = windowMap.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());
        while (!deque.isEmpty() && deque.peekFirst() < windowStart) {
            deque.pollFirst();
        }
        return (long) deque.size();
    }

    @Override
    public void asyncUpdate(String factorCode, Map<String, Object> context, long tokenUsage) {
        String key = (String) context.getOrDefault("f_consumer_id", "default_consumer");
        ConcurrentLinkedDeque<Long> deque = windowMap.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());
        deque.addLast(System.currentTimeMillis());
    }
}
