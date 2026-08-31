package com.chatling.engine.filter;

import com.chatling.common.model.ModelPolicyConfig;
import com.chatling.common.policy.PolicyPipelineResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * 3. Prompt 越狱与恶意注入防御过滤器 (Order: 250)
 */
@Slf4j
@Component
public class JailbreakFilter implements PolicyFilter {

    private static final Pattern[] JAILBREAK_PATTERNS = new Pattern[] {
            Pattern.compile("(?i)ignore\\s+(?:all\\s+|the\\s+)?previous\\s+instructions"),
            Pattern.compile("(?i)ignore\\s+the\\s+above\\s+instructions"),
            Pattern.compile("忽略.*(?:之前|上述|前置).*(?:指令|设定|规则)"),
            Pattern.compile("(?i)(?:DAN\\s*mode|DAN模式|越狱模式|开发者模式|无限制模式)"),
            Pattern.compile("从现在开始你是一个(?:不受道德限制|没有底线)的AI"),
            Pattern.compile("(?i)repeat\\s+all\\s+text\\s+above")
    };

    @Override
    public int getOrder() {
        return 250;
    }

    @Override
    public boolean isEnabled(ModelPolicyConfig config) {
        return config != null && Boolean.TRUE.equals(config.getEnableJailbreakFilter());
    }

    @Override
    public PolicyPipelineResult filter(RequestContext ctx, ModelPolicyConfig config) {
        if (!isEnabled(config)) {
            return PolicyPipelineResult.pass();
        }

        String prompt = ctx.getCurrentPrompt();
        if (prompt == null || prompt.isEmpty()) {
            return PolicyPipelineResult.pass();
        }

        for (Pattern p : JAILBREAK_PATTERNS) {
            if (p.matcher(prompt).find()) {
                log.warn("[-] [Filter: Jailbreak Blocked] model={}, matchedPattern={}", ctx.getModelName(), p.pattern());
                return PolicyPipelineResult.reject(403, "安全拦截：检测到恶意 Prompt 注入或越狱攻击意图！", "plugin_jailbreak_defense");
            }
        }

        return PolicyPipelineResult.pass();
    }
}
