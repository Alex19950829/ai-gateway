package com.chatling.engine.factor.impl;

import com.chatling.common.factor.IFactorAggregator;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

@Component("SlidingWindowTokenAggregator")
public class SlidingWindowTokenAggregator implements IFactorAggregator {
    private static class TokenRecord {
        long timestamp;
        long tokens;
        TokenRecord(long timestamp, long tokens) {
            this.timestamp = timestamp;
            this.tokens = tokens;
        }
    }

    private final Map<String, ConcurrentLinkedDeque<TokenRecord>> windowMap = new ConcurrentHashMap<>();

    @Override
    public String getAggregatorCode() {
        return "SlidingWindowTokenAggregator";
    }

    @Override
    public Object extractValue(String factorCode, Map<String, Object> context) {
        String key = (String) context.getOrDefault("f_consumer_id", "default_consumer");
        long windowStart = System.currentTimeMillis() - 60_000L;

        ConcurrentLinkedDeque<TokenRecord> deque = windowMap.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());
        while (!deque.isEmpty() && deque.peekFirst().timestamp < windowStart) {
            deque.pollFirst();
        }
        long sum = 0L;
        for (TokenRecord record : deque) {
            sum += record.tokens;
        }
        return sum;
    }

    @Override
    public void asyncUpdate(String factorCode, Map<String, Object> context, long tokenUsage) {
        if (tokenUsage <= 0) return;
        String key = (String) context.getOrDefault("f_consumer_id", "default_consumer");
        ConcurrentLinkedDeque<TokenRecord> deque = windowMap.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());
        deque.addLast(new TokenRecord(System.currentTimeMillis(), tokenUsage));
    }
}
