package com.chatling.engine.factor.impl;

import com.chatling.common.factor.IFactorAggregator;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component("TokenQuotaDecrementAggregator")
public class TokenQuotaDecrementAggregator implements IFactorAggregator {
    // 默认初始预算 5,000,000 Token
    private final Map<String, AtomicLong> quotaMap = new ConcurrentHashMap<>();

    @Override
    public String getAggregatorCode() {
        return "TokenQuotaDecrementAggregator";
    }

    @Override
    public Object extractValue(String factorCode, Map<String, Object> context) {
        String key = (String) context.getOrDefault("f_consumer_id", "default_consumer");
        return quotaMap.computeIfAbsent(key, k -> new AtomicLong(5_000_000L)).get();
    }

    @Override
    public void asyncUpdate(String factorCode, Map<String, Object> context, long tokenUsage) {
        if (tokenUsage <= 0) return;
        String key = (String) context.getOrDefault("f_consumer_id", "default_consumer");
        quotaMap.computeIfAbsent(key, k -> new AtomicLong(5_000_000L)).addAndGet(-tokenUsage);
    }
}
