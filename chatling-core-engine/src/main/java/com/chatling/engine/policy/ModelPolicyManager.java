package com.chatling.engine.policy;

import com.chatling.common.policy.ModelPolicyDefinition;
import com.chatling.common.policy.ModelPolicyRule;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ModelPolicyManager {
    // Key 为 modelName (如 "deepseek-chat" 或 "ALL")
    private final Map<String, ModelPolicyDefinition> policyMap = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        // 预置 DeepSeek 专属防护策略
        List<ModelPolicyRule> deepseekRules = new ArrayList<>();
        deepseekRules.add(new ModelPolicyRule(1000, "rule_dynamic_qpm_limit", "租户定制化QPM限流规则", "REJECT", 429, "Too Many Requests: 您的专属 QPM 限额已耗尽！"));
        deepseekRules.add(new ModelPolicyRule(800, "rule_prompt_jailbreak_security", "Prompt 越狱与恶意注入拦截", "REJECT", 403, "Security Guard: 输入存在合规风险！"));

        registerPolicy(new ModelPolicyDefinition(
                "deepseek-chat",
                "DeepSeek 生产级流控与安全策略",
                "REQUEST_PRE",
                deepseekRules,
                1
        ));
    }

    public void registerPolicy(ModelPolicyDefinition policy) {
        policy.sortRules();
        policyMap.put(policy.getModelName(), policy);
    }

    public ModelPolicyDefinition getPolicy(String modelName) {
        ModelPolicyDefinition policy = policyMap.get(modelName);
        if (policy == null) {
            policy = policyMap.get("ALL"); // 全局兜底策略
        }
        return policy;
    }

    public Collection<ModelPolicyDefinition> getAllPolicies() {
        return policyMap.values();
    }

    public void removePolicy(String modelName) {
        policyMap.remove(modelName);
    }
}
