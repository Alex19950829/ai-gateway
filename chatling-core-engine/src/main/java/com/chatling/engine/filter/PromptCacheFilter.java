package com.chatling.engine.filter;

import com.chatling.common.model.ModelPolicyConfig;
import com.chatling.common.policy.PolicyPipelineResult;
import com.chatling.engine.cache.PromptCacheService;
import com.chatling.engine.cache.PromptCacheService.CachedResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 1. Prompt 精确哈希缓存加速过滤器 (Order: 100)
 */
@Slf4j
@Component
public class PromptCacheFilter implements PolicyFilter {

    @Autowired(required = false)
    private PromptCacheService promptCacheService;

    @Override
    public int getOrder() {
        return 100;
    }

    @Override
    public boolean isEnabled(ModelPolicyConfig config) {
        return config != null && Boolean.TRUE.equals(config.getEnableCache());
    }

    @Override
    public PolicyPipelineResult filter(RequestContext ctx, ModelPolicyConfig config) {
        if (!isEnabled(config) || promptCacheService == null) {
            return PolicyPipelineResult.pass();
        }

        String prompt = ctx.getCurrentPrompt();
        if (prompt == null || prompt.isEmpty()) {
            return PolicyPipelineResult.pass();
        }

        String promptHash = promptCacheService.calculateHash(ctx.getModelName(), prompt);

        CachedResponse cached = promptCacheService.get(promptHash);
        if (cached != null && cached.getFullText() != null && !cached.getFullText().isEmpty()) {
            int savedTokens = cached.getPromptTokens() + cached.getCompletionTokens();
            log.info("[*] [Filter: Cache HIT] model={}, hash={}, savedTokens={}",
                    ctx.getModelName(), promptHash, savedTokens);
            return PolicyPipelineResult.cacheHit(cached.getFullText(), promptHash, "Prompt精确哈希缓存命中 (节省 " + savedTokens + " Tokens)");
        }

        return PolicyPipelineResult.pass();
    }
}
