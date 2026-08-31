package com.chatling.common.policy;

import java.io.Serializable;

public class PolicyPipelineResult implements Serializable {
    public enum Status {
        PASS,
        REJECTED,
        FALLBACK,
        MASKED,
        CACHE_HIT
    }

    private final Status status;
    private final int rejectCode;
    private final String message;
    private final String fallbackModel;
    private final String hitRuleCode;
    private final String modifiedPrompt;
    private final String cachedContent;
    private final boolean isModified;

    private PolicyPipelineResult(Status status, int rejectCode, String message, String fallbackModel, String hitRuleCode, String modifiedPrompt, String cachedContent, boolean isModified) {
        this.status = status;
        this.rejectCode = rejectCode;
        this.message = message;
        this.fallbackModel = fallbackModel;
        this.hitRuleCode = hitRuleCode;
        this.modifiedPrompt = modifiedPrompt;
        this.cachedContent = cachedContent;
        this.isModified = isModified;
    }

    public static PolicyPipelineResult pass() {
        return new PolicyPipelineResult(Status.PASS, 200, "PASS", null, null, null, null, false);
    }

    public static PolicyPipelineResult pass(String modifiedPrompt) {
        return new PolicyPipelineResult(Status.PASS, 200, "PASS", null, null, modifiedPrompt, null, modifiedPrompt != null);
    }

    public static PolicyPipelineResult reject(int rejectCode, String message, String hitRuleCode) {
        return new PolicyPipelineResult(Status.REJECTED, rejectCode, message, null, hitRuleCode, null, null, false);
    }

    public static PolicyPipelineResult fallback(String fallbackModel, String hitRuleCode) {
        return new PolicyPipelineResult(Status.FALLBACK, 200, "FALLBACK", fallbackModel, hitRuleCode, null, null, false);
    }

    public static PolicyPipelineResult mask(String modifiedPrompt, String hitRuleCode, String message) {
        return new PolicyPipelineResult(Status.MASKED, 200, message != null ? message : "MASKED", null, hitRuleCode, modifiedPrompt, null, true);
    }

    public static PolicyPipelineResult cacheHit(String cachedContent, String hitRuleCode, String message) {
        return new PolicyPipelineResult(Status.CACHE_HIT, 200, message != null ? message : "CACHE_HIT", null, hitRuleCode, null, cachedContent, false);
    }

    public Status getStatus() { return status; }
    public int getRejectCode() { return rejectCode; }
    public String getMessage() { return message; }
    public String getFallbackModel() { return fallbackModel; }
    public String getHitRuleCode() { return hitRuleCode; }
    public String getModifiedPrompt() { return modifiedPrompt; }
    public String getCachedContent() { return cachedContent; }
    public boolean isModified() { return isModified; }
    public boolean isPass() { return status == Status.PASS || status == Status.MASKED; }
    public boolean isRejected() { return status == Status.REJECTED; }
    public boolean isFallback() { return status == Status.FALLBACK; }
    public boolean isMasked() { return status == Status.MASKED || isModified; }
    public boolean isCacheHit() { return status == Status.CACHE_HIT; }
}
