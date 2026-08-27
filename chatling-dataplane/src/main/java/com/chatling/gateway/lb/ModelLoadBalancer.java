package com.chatling.gateway.lb;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 多推理节点智能负载均衡器与熔断器 (Weighted Round-Robin & Circuit Breaker)
 */
@Slf4j
@Component
public class ModelLoadBalancer {

    // 轮询计数器: modelName -> counter
    private final Map<String, AtomicInteger> positionMap = new ConcurrentHashMap<>();

    // 节点连续失败次数计数器: "modelName:url" -> failCount
    private final Map<String, AtomicInteger> failureCountMap = new ConcurrentHashMap<>();

    // 节点熔断截止时间戳: "modelName:url" -> circuitOpenUntilTimestamp
    private final Map<String, Long> breakerDeadlines = new ConcurrentHashMap<>();
    // 连续失败3次触发熔断
    private static final int MAX_CONSECUTIVE_FAILURES = 3;
    // 熔断拉黑 30 秒
    private static final long CIRCUIT_BREAK_DURATION_MS = 30000;

    /**
     * 从配置的逗号分隔 BaseURL 列表中，选出一个健康的可用节点 (支持多实例加权轮询)
     */
    public String selectTargetUrl(String modelName, String configuredUrls) {
        if (configuredUrls == null || configuredUrls.trim().isEmpty()) {
            return configuredUrls;
        }

        String[] rawList = configuredUrls.split(",");
        List<String> activeNodes = new ArrayList<>();
        long now = System.currentTimeMillis();

        for (String url : rawList) {
            String trimmed = url.trim();
            if (trimmed.isEmpty()) continue;
            String key = modelName + ":" + trimmed;
            Long deadline = breakerDeadlines.get(key);
            if (deadline != null && now < deadline) {
                // 仍处于熔断状态
                log.warn("Node {} for model {} is in CIRCUIT_OPEN state, skipping...", trimmed, modelName);
            } else {
                activeNodes.add(trimmed);
            }
        }

        if (activeNodes.isEmpty()) {
            // 如果所有节点都被熔断了，做半开尝试，放行第一个节点
            log.error("All nodes for model {} are circuit-broken! Force trying first node: {}", modelName, rawList[0]);
            return rawList[0].trim();
        }

        AtomicInteger counter = positionMap.computeIfAbsent(modelName, k -> new AtomicInteger(0));
        int index = Math.abs(counter.getAndIncrement() % activeNodes.size());
        String selected = activeNodes.get(index);
        log.info("LoadBalancer routed model [{}] to node: {} (active count: {})", modelName, selected, activeNodes.size());
        return selected;
    }

    /**
     * 上报节点调用成功，重置失败计数与熔断状态
     */
    public void recordSuccess(String modelName, String targetUrl) {
        String key = modelName + ":" + targetUrl;
        failureCountMap.remove(key);
        breakerDeadlines.remove(key);
    }

    /**
     * 上报节点调用失败，累计达到阈值触发熔断
     */
    public void recordFailure(String modelName, String targetUrl) {
        String key = modelName + ":" + targetUrl;
        AtomicInteger counter = failureCountMap.computeIfAbsent(key, k -> new AtomicInteger(0));
        int fails = counter.incrementAndGet();
        if (fails >= MAX_CONSECUTIVE_FAILURES) {
            long openUntil = System.currentTimeMillis() + CIRCUIT_BREAK_DURATION_MS;
            breakerDeadlines.put(key, openUntil);
            log.error("Node {} for model {} failed {} times continuously, TRIPPED CIRCUIT BREAKER for 30s!", targetUrl, modelName, fails);
        }
    }
}
