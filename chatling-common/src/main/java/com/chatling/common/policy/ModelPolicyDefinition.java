package com.chatling.common.policy;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ModelPolicyDefinition implements Serializable {
    private String modelName;               // 目标模型 (如 deepseek-chat, ALL)
    private String policyName;              // 策略名称
    private String stage;                   // REQUEST_PRE, RESPONSE_POST, ON_ERROR
    private List<ModelPolicyRule> rules = new ArrayList<>();
    private int status;                     // 1: 启用, 0: 停用

    public ModelPolicyDefinition() {}

    public ModelPolicyDefinition(String modelName, String policyName, String stage, List<ModelPolicyRule> rules, int status) {
        this.modelName = modelName;
        this.policyName = policyName;
        this.stage = stage;
        this.rules = rules != null ? rules : new ArrayList<>();
        this.status = status;
        sortRules();
    }

    public void sortRules() {
        if (rules != null) {
            Collections.sort(rules);
        }
    }

    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }
    public String getPolicyName() { return policyName; }
    public void setPolicyName(String policyName) { this.policyName = policyName; }
    public String getStage() { return stage; }
    public void setStage(String stage) { this.stage = stage; }
    public List<ModelPolicyRule> getRules() { return rules; }
    public void setRules(List<ModelPolicyRule> rules) {
        this.rules = rules;
        sortRules();
    }
    public int getStatus() { return status; }
    public void setStatus(int status) { this.status = status; }
}
