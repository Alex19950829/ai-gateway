package com.chatling.common.factor;

import java.util.Map;

public interface IFactorAggregator {
    String getAggregatorCode();
    Object extractValue(String factorCode, Map<String, Object> context);
    void asyncUpdate(String factorCode, Map<String, Object> context, long tokenUsage);
}
