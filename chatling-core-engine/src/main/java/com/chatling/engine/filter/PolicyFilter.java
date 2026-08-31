package com.chatling.engine.filter;

import com.chatling.common.model.ModelPolicyConfig;
import com.chatling.common.policy.PolicyPipelineResult;

/**
 * 标准策略过滤器接口 (Policy Filter)
 * 采用责任链模式执行各功能的声明式治理逻辑
 */
public interface PolicyFilter {
    /**
     * 过滤器执行顺序 (数值越小越优先执行)
     */
    int getOrder();

    /**
     * 判断当前插件在指定模型策略中是否已开启
     */
    boolean isEnabled(ModelPolicyConfig config);

    /**
     * 执行策略过滤逻辑
     * @param ctx 请求上下文
     * @param config 模型声明式策略配置
     * @return 过滤结果 (PASS, REJECTED, CACHE_HIT, MASKED, FALLBACK)
     */
    PolicyPipelineResult filter(RequestContext ctx, ModelPolicyConfig config);
}
