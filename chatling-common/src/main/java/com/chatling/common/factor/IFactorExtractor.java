package com.chatling.common.factor;

import java.util.Map;

public interface IFactorExtractor {
    Object extract(Map<String, Object> context);
}
