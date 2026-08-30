package com.chatling.engine.factor.impl;

import com.chatling.common.factor.IFactorAggregator;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component("AtomicCounterAggregator")
public class AtomicCounterAggregator implements IFactorAggregator {
    private final Map<String, AtomicLong> counterMap = new ConcurrentHashMap<>();

    @Override
    public String getAggregatorCode() {
        return "AtomicCounterAggregator";
    }

    @Override
    public Object extractValue(String factorCode, Map<String, Object> context) {
        String key = (String) context.getOrDefault("f_consumer_id", "default_consumer");
        return counterMap.computeIfAbsent(key, k -> new AtomicLong(0L)).get();
    }

    @Override
    public void asyncUpdate(String factorCode, Map<String, Object> context, long tokenUsage) {
        String key = (String) context.getOrDefault("f_consumer_id", "default_consumer");
        counterMap.computeIfAbsent(key, k -> new AtomicLong(0L)).incrementAndGet();
    }
}
