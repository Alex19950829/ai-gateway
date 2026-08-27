package com.chatling.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsageDaily {
    private Long id;
    private String statDate;
    private String ownerName;
    private String department;
    private String apiKey;
    private String modelName;
    private Integer requestCount;
    private Long promptTokens;
    private Long completionTokens;
    private Long totalTokens;
}
