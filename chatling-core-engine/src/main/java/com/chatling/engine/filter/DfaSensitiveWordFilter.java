package com.chatling.engine.filter;

import com.chatling.common.model.ModelPolicyConfig;
import com.chatling.common.policy.PolicyPipelineResult;
import com.chatling.engine.security.DfaSensitiveWordService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 2. 本地 DFA 敏感词微秒级硬拦截过滤器 (Order: 200)
 */
@Slf4j
@Component
public class DfaSensitiveWordFilter implements PolicyFilter {

    @Autowired(required = false)
    private DfaSensitiveWordService dfaSensitiveWordService;

    @Override
    public int getOrder() {
        return 200;
    }

    @Override
    public boolean isEnabled(ModelPolicyConfig config) {
        return config != null && Boolean.TRUE.equals(config.getEnableSensitiveFilter());
    }

    @Override
    public PolicyPipelineResult filter(RequestContext ctx, ModelPolicyConfig config) {
        if (!isEnabled(config) || dfaSensitiveWordService == null) {
            return PolicyPipelineResult.pass();
        }

        String prompt = ctx.getCurrentPrompt();
        if (prompt == null || prompt.isEmpty()) {
            return PolicyPipelineResult.pass();
        }

        String hitWord = dfaSensitiveWordService.checkSensitiveWord(prompt);
        if (hitWord != null && !hitWord.isEmpty()) {
            log.warn("[-] [Filter: DFA Blocked] model={}, hitWord={}", ctx.getModelName(), hitWord);
            return PolicyPipelineResult.reject(400, "安全拦截：输入包含违规违禁词汇 [" + hitWord + "]", "plugin_dfa_sensitive");
        }

        return PolicyPipelineResult.pass();
    }
}
