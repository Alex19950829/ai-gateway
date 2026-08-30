package com.chatling.engine.policy;

import com.chatling.common.policy.ModelPolicyDefinition;
import com.chatling.common.policy.ModelPolicyRule;
import com.chatling.common.policy.PolicyPipelineResult;
import com.chatling.common.rule.RuleDecision;
import com.chatling.common.rule.RuleDefinition;
import com.chatling.engine.factor.FactorEngine;
import com.chatling.engine.rule.RuleExecutorManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class PolicyPipelineExecutor {
    private static final Logger log = LoggerFactory.getLogger(PolicyPipelineExecutor.class);

    @Autowired
    private ModelPolicyManager policyManager;

    @Autowired
    private FactorEngine factorEngine;

    @Autowired
    private RuleExecutorManager ruleExecutorManager;

    /**
     * 执行模型前置/后置策略规则流水线 (按 Priority 降序排队调度)
     */
    public PolicyPipelineResult executePipeline(String modelName, Map<String, Object> context) {
        ModelPolicyDefinition policy = policyManager.getPolicy(modelName);
        if (policy == null || policy.getStatus() == 0 || policy.getRules().isEmpty()) {
            return PolicyPipelineResult.pass();
        }

        String currentPrompt = (String) context.getOrDefault("f_user_prompt", "");
        boolean isModified = false;
        String lastHitRuleCode = null;

        List<ModelPolicyRule> rules = policy.getRules();
        for (ModelPolicyRule policyRule : rules) {
            String ruleCode = policyRule.getRuleCode();
            RuleDefinition ruleDef = ruleExecutorManager.getRule(ruleCode);
            if (ruleDef == null || ruleDef.getStatus() == 0) {
                continue;
            }

            // 1. 动态提取当前规则绑定的特征变量字典 (支持惰性求值)
            Map<String, Object> factors = factorEngine.extractFactors(ruleDef.getBoundFactors(), context);

            // 2. 执行 Groovy 规则逻辑
            RuleDecision decision = ruleExecutorManager.executeRule(ruleCode, factors);

            // 3. 处理短路阻断与降级动作
            if (decision.isReject()) {
                log.warn("[-] [Policy Blocked] model={}, rule={}, msg={}", modelName, ruleCode, decision.getMessage());
                String msg = policyRule.getRejectMsg() != null ? policyRule.getRejectMsg() : decision.getMessage();
                return PolicyPipelineResult.reject(policyRule.getRejectCode(), msg, ruleCode);
            } else if (decision.isFallback()) {
                log.info("[*] [Policy Fallback] model={}, target={}, rule={}", modelName, decision.getFallbackModel(), ruleCode);
                return PolicyPipelineResult.fallback(decision.getFallbackModel(), ruleCode);
            } else if (decision.isMask() || decision.isRewrite()) {
                String rewritten = decision.getRewrittenContent();
                if (rewritten != null && !rewritten.isEmpty()) {
                    currentPrompt = rewritten;
                    isModified = true;
                    lastHitRuleCode = ruleCode;
                    context.put("f_user_prompt", currentPrompt);
                    context.put("f_masked_prompt", currentPrompt);
                    log.info("[*] [Policy Masked] model={}, rule={}, prompt rewritten successfully", modelName, ruleCode);
                }
            }
        }

        if (isModified) {
            return PolicyPipelineResult.mask(currentPrompt, lastHitRuleCode, "MASKED");
        }
        return PolicyPipelineResult.pass();
    }
}
