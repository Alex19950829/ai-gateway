package com.chatling.common.rule;

import java.io.Serializable;
import java.util.List;

public class RuleDefinition implements Serializable {
    private String ruleCode;
    private String ruleName;
    private List<String> boundFactors;
    private String groovyScript;
    private String owner;
    private int status; // 1: 启用, 0: 停用

    public RuleDefinition() {}

    public RuleDefinition(String ruleCode, String ruleName, List<String> boundFactors, String groovyScript, String owner, int status) {
        this.ruleCode = ruleCode;
        this.ruleName = ruleName;
        this.boundFactors = boundFactors;
        this.groovyScript = groovyScript;
        this.owner = owner;
        this.status = status;
    }

    public String getRuleCode() { return ruleCode; }
    public void setRuleCode(String ruleCode) { this.ruleCode = ruleCode; }
    public String getRuleName() { return ruleName; }
    public void setRuleName(String ruleName) { this.ruleName = ruleName; }
    public List<String> getBoundFactors() { return boundFactors; }
    public void setBoundFactors(List<String> boundFactors) { this.boundFactors = boundFactors; }
    public String getGroovyScript() { return groovyScript; }
    public void setGroovyScript(String groovyScript) { this.groovyScript = groovyScript; }
    public String getOwner() { return owner; }
    public void setOwner(String owner) { this.owner = owner; }
    public int getStatus() { return status; }
    public void setStatus(int status) { this.status = status; }
}
