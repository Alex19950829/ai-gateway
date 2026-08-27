package com.chatling.gateway.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class TokenRateLimiterService {

    // 缓存每分钟的 Token 消耗: key -> "apiKey:minuteTimestamp" -> count
    private final Cache<String, AtomicInteger> tpmCache = Caffeine.newBuilder()
            .expireAfterWrite(2, TimeUnit.MINUTES)
            .maximumSize(50000)
            .build();

    // 缓存每秒的请求次数: key -> "apiKey:secondTimestamp" -> count
    private final Cache<String, AtomicInteger> qpsCache = Caffeine.newBuilder()
            .expireAfterWrite(5, TimeUnit.SECONDS)
            .maximumSize(50000)
            .build();

    /**
     * 校验并尝试消耗 TPM (Tokens Per Minute) 和 QPS 限流
     * @return true 放行, false 拦截
     */
    public boolean tryAcquire(String apiKey, int estimatedTokens, int tpmLimit, int qpsLimit) {
        long currentSec = System.currentTimeMillis() / 1000;
        long currentMin = currentSec / 60;

        // 1. 检查 QPS
        String qpsKey = apiKey + ":" + currentSec;
        AtomicInteger qpsCounter = qpsCache.get(qpsKey, k -> new AtomicInteger(0));
        if (qpsCounter != null && qpsCounter.incrementAndGet() > qpsLimit) {
            log.warn("Rate limit exceeded QPS for key: {}, limit: {}", apiKey, qpsLimit);
            return false;
        }

        // 2. 检查 TPM
        String tpmKey = apiKey + ":" + currentMin;
        AtomicInteger tpmCounter = tpmCache.get(tpmKey, k -> new AtomicInteger(0));
        if (tpmCounter != null) {
            int current = tpmCounter.addAndGet(estimatedTokens);
            if (current > tpmLimit) {
                log.warn("Rate limit exceeded TPM for key: {}, current: {}, limit: {}", apiKey, current, tpmLimit);
                return false;
            }
        }

        return true;
    }
}
