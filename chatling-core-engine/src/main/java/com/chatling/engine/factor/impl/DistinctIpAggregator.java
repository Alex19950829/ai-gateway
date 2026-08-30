package com.chatling.engine.factor.impl;

import com.chatling.common.factor.IFactorAggregator;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component("DistinctIpAggregator")
public class DistinctIpAggregator implements IFactorAggregator {
    private final Map<String, Set<String>> consumerIpMap = new ConcurrentHashMap<>();

    @Override
    public String getAggregatorCode() {
        return "DistinctIpAggregator";
    }

    @Override
    public Object extractValue(String factorCode, Map<String, Object> context) {
        String consumer = (String) context.getOrDefault("f_consumer_id", "default_consumer");
        return (long) consumerIpMap.computeIfAbsent(consumer, k -> ConcurrentHashMap.newKeySet()).size();
    }

    @Override
    public void asyncUpdate(String factorCode, Map<String, Object> context, long tokenUsage) {
        String consumer = (String) context.getOrDefault("f_consumer_id", "default_consumer");
        String ip = (String) context.getOrDefault("f_client_ip", "127.0.0.1");
        consumerIpMap.computeIfAbsent(consumer, k -> ConcurrentHashMap.newKeySet()).add(ip);
    }
}
