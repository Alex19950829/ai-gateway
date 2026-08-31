package com.chatling.engine.policy;

import com.chatling.common.model.ModelPolicyConfig;
import com.chatling.common.policy.PolicyPipelineResult;
import com.chatling.engine.filter.PolicyFilter;
import com.chatling.engine.filter.RequestContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 策略流水线调度执行器 (Policy Pipeline Executor)
 * 纯 Java 原生责任链架构，根据请求模型的专属配置动态组装活跃插件
 */
@Slf4j
@Service
public class PolicyPipelineExecutor {

    @Autowired
    private ModelPolicyManager policyManager;

    @Autowired(required = false)
    private List<PolicyFilter> filters = new ArrayList<>();

    @PostConstruct
    public void init() {
        if (filters != null) {
            filters.sort(Comparator.comparingInt(PolicyFilter::getOrder));
            log.info("[*] [PolicyPipelineExecutor] Registered {} standard policy filter plugins in container: {}",
                    filters.size(), filters.stream().map(f -> f.getClass().getSimpleName()).toList());
        }
    }

    /**
     * 按模型配置动态装配并执行声明式插件责任链
     */
    public PolicyPipelineResult executePipeline(RequestContext ctx) {
        String modelName = ctx.getModelName();
        ModelPolicyConfig config = policyManager.getPolicy(modelName);

        // 如果该模型未配置任何策略，或策略状态为禁用，则不挂载任何插件直接放行
        if (config == null || (config.getStatus() != null && config.getStatus() == 0)) {
            log.info("[*] [Pipeline Bypass] model [{}] has NO policy configured (0 plugins active), straight through.", modelName);
            return PolicyPipelineResult.pass();
        }

        // 根据该模型具体配置，动态过滤出实际已开启的插件流水线
        List<PolicyFilter> activeFilters = (filters != null)
                ? filters.stream().filter(f -> f.isEnabled(config)).toList()
                : List.of();

        if (activeFilters.isEmpty()) {
            log.info("[*] [Pipeline Bypass] model [{}] policy configured but all plugins disabled (0 active), straight through.", modelName);
            return PolicyPipelineResult.pass();
        }

        log.info("[*] [Pipeline Execute] model [{}] dynamically assembled {} active plugins: {}",
                modelName, activeFilters.size(),
                activeFilters.stream().map(f -> f.getClass().getSimpleName()).toList());

        boolean isModified = false;
        String lastHitPlugin = null;

        for (PolicyFilter filter : activeFilters) {
            PolicyPipelineResult result = filter.filter(ctx, config);

            if (result == null || result.getStatus() == null) {
                continue;
            }

            // 1. 缓存命中极速短路直回
            if (result.isCacheHit()) {
                log.info("[*] [Pipeline Short-Circuit: CACHE_HIT] model={}, plugin={}", modelName, result.getHitRuleCode());
                return result;
            }

            // 2. 违规/超限短路阻断
            if (result.isRejected()) {
                log.warn("[-] [Pipeline Blocked: REJECTED] model={}, plugin={}, reason={}",
                        modelName, result.getHitRuleCode(), result.getMessage());
                return result;
            }

            // 3. 容灾降级
            if (result.isFallback()) {
                log.info("[*] [Pipeline Fallback] model={}, target={}, plugin={}",
                        modelName, result.getFallbackModel(), result.getHitRuleCode());
                return result;
            }

            // 4. 内容改写/隐私脱敏
            if (result.isMasked()) {
                isModified = true;
                lastHitPlugin = result.getHitRuleCode();
                ctx.setModifiedPrompt(result.getModifiedPrompt());
            }
        }

        if (isModified) {
            return PolicyPipelineResult.mask(ctx.getCurrentPrompt(), lastHitPlugin, "已完成请求内容动态脱敏与改写");
        }

        return PolicyPipelineResult.pass();
    }
}
