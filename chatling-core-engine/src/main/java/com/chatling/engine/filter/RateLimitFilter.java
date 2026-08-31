package com.chatling.engine.filter;

import com.chatling.common.model.ModelPolicyConfig;
import com.chatling.common.policy.PolicyPipelineResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 5. 模型专属动态 QPM 滑动窗口限流过滤器 (Order: 400)
 */
@Slf4j
@Component
public class RateLimitFilter implements PolicyFilter {

    private final ConcurrentHashMap<String, WindowCounter> windowMap = new ConcurrentHashMap<>();

    private static class WindowCounter {
        long windowStartMs;
        AtomicInteger counter = new AtomicInteger(0);

        WindowCounter(long start) {
            this.windowStartMs = start;
        }
    }

    @Override
    public int getOrder() {
        return 400;
    }

    @Override
    public boolean isEnabled(ModelPolicyConfig config) {
        return config != null && Boolean.TRUE.equals(config.getEnableRateLimit());
    }

    @Override
    public PolicyPipelineResult filter(RequestContext ctx, ModelPolicyConfig config) {
        if (!isEnabled(config)) {
            return PolicyPipelineResult.pass();
        }

        int maxQpm = (config.getCustomQpmLimit() != null && config.getCustomQpmLimit() > 0)
                ? config.getCustomQpmLimit()
                : 60;

        String key = ctx.getApiKey() != null ? ctx.getApiKey() + ":" + ctx.getModelName() : ctx.getModelName();
        long now = System.currentTimeMillis();

        WindowCounter counter = windowMap.compute(key, (k, v) -> {
            if (v == null || now - v.windowStartMs >= 60_000) {
                WindowCounter newCounter = new WindowCounter(now);
                newCounter.counter.set(1);
                return newCounter;
            }
            v.counter.incrementAndGet();
            return v;
        });

        if (counter.counter.get() > maxQpm) {
            log.warn("[-] [Filter: RateLimit Exceeded] model={}, currentCount={}, maxQpm={}",
                    ctx.getModelName(), counter.counter.get(), maxQpm);
            return PolicyPipelineResult.reject(429, "Too Many Requests: 模型专属 QPM 限额 [" + maxQpm + "] 已耗尽，请稍后重试！", "plugin_rate_limiter");
        }

        return PolicyPipelineResult.pass();
    }
}
