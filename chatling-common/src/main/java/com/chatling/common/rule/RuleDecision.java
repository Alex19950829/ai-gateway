package com.chatling.common.rule;

import java.io.Serializable;

public class RuleDecision implements Serializable {
    public enum Action {
        PASS,
        REJECT,
        FALLBACK,
        MASK,
        REWRITE
    }

    private final Action action;
    private final String message;
    private final String fallbackModel;
    private final String rewrittenContent;

    private RuleDecision(Action action, String message, String fallbackModel, String rewrittenContent) {
        this.action = action;
        this.message = message;
        this.fallbackModel = fallbackModel;
        this.rewrittenContent = rewrittenContent;
    }

    public static RuleDecision pass() {
        return new RuleDecision(Action.PASS, null, null, null);
    }

    public static RuleDecision reject(String message) {
        return new RuleDecision(Action.REJECT, message, null, null);
    }

    public static RuleDecision fallbackTo(String targetModel) {
        return new RuleDecision(Action.FALLBACK, null, targetModel, null);
    }

    public static RuleDecision mask(String maskedContent, String message) {
        return new RuleDecision(Action.MASK, message, null, maskedContent);
    }

    public static RuleDecision rewrite(String rewrittenContent, String message) {
        return new RuleDecision(Action.REWRITE, message, null, rewrittenContent);
    }

    public Action getAction() { return action; }
    public String getMessage() { return message; }
    public String getFallbackModel() { return fallbackModel; }
    public String getRewrittenContent() { return rewrittenContent; }
    public boolean isPass() { return action == Action.PASS; }
    public boolean isReject() { return action == Action.REJECT; }
    public boolean isFallback() { return action == Action.FALLBACK; }
    public boolean isMask() { return action == Action.MASK; }
    public boolean isRewrite() { return action == Action.REWRITE; }
}
