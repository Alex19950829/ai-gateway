package com.chatling.engine.filter;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * 策略过滤器执行上下文 (Request Context)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequestContext {
    private String apiKey;
    private String ownerName;
    private String clientIp;
    private String modelName;
    private String originalPrompt;
    private String modifiedPrompt;
    private int activeConcurrency;
    private long requestTime;

    @Builder.Default
    private Map<String, Object> attributes = new HashMap<>();

    public String getCurrentPrompt() {
        return modifiedPrompt != null ? modifiedPrompt : (originalPrompt != null ? originalPrompt : "");
    }
}
