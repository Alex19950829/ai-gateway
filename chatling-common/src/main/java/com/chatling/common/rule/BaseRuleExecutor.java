package com.chatling.common.rule;

import java.util.Map;

public abstract class BaseRuleExecutor {
    /**
     * 核心规则执行方法
     * @param factor 提取出来的特征变量字典
     * @return RuleDecision 决策结果 (PASS, REJECT, FALLBACK)
     */
    public abstract RuleDecision executeRule(Map<String, Object> factor);
}
