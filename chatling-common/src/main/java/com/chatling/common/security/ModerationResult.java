package com.chatling.common.security;

import java.io.Serializable;

public class ModerationResult implements Serializable {
    private final boolean pass;
    private final String riskLabel; // political, porn, violent, abuse, contraband
    private final String riskReason;
    private final double riskScore;

    public ModerationResult(boolean pass, String riskLabel, String riskReason, double riskScore) {
        this.pass = pass;
        this.riskLabel = riskLabel;
        this.riskReason = riskReason;
        this.riskScore = riskScore;
    }

    public static ModerationResult pass() {
        return new ModerationResult(true, null, "合规通过", 0.0);
    }

    public static ModerationResult reject(String riskLabel, String riskReason, double riskScore) {
        return new ModerationResult(false, riskLabel, riskReason, riskScore);
    }

    public boolean isPass() { return pass; }
    public String getRiskLabel() { return riskLabel; }
    public String getRiskReason() { return riskReason; }
    public double getRiskScore() { return riskScore; }
}
