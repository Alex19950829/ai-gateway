package com.chatling.common.policy;

import java.io.Serializable;

public class ModelPolicyRule implements Serializable, Comparable<ModelPolicyRule> {
    private int priority;           // 优先级，越大越先执行 (如 1000, 800, 400)
    private String ruleCode;        // 关联的 Groovy 规则编码
    private String ruleName;        // 规则名称
    private String action;          // REJECT (阻断), FALLBACK (降级), PASS (放行)
    private int rejectCode;         // 阻断 HTTP 状态码 (如 429, 403, 401)
    private String rejectMsg;       // 阻断提示文案

    public ModelPolicyRule() {}

    public ModelPolicyRule(int priority, String ruleCode, String ruleName, String action, int rejectCode, String rejectMsg) {
        this.priority = priority;
        this.ruleCode = ruleCode;
        this.ruleName = ruleName;
        this.action = action;
        this.rejectCode = rejectCode;
        this.rejectMsg = rejectMsg;
    }

    @Override
    public int compareTo(ModelPolicyRule o) {
        return Integer.compare(o.priority, this.priority); // 降序排序
    }

    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }
    public String getRuleCode() { return ruleCode; }
    public void setRuleCode(String ruleCode) { this.ruleCode = ruleCode; }
    public String getRuleName() { return ruleName; }
    public void setRuleName(String ruleName) { this.ruleName = ruleName; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public int getRejectCode() { return rejectCode; }
    public void setRejectCode(int rejectCode) { this.rejectCode = rejectCode; }
    public String getRejectMsg() { return rejectMsg; }
    public void setRejectMsg(String rejectMsg) { this.rejectMsg = rejectMsg; }
}
