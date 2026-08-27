package com.chatling.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatAudit {
    private Long id;
    private String requestId;
    private String apiKey;
    private String ownerName;
    private String modelName;
    private Integer ttftMs;
    private Integer totalCostMs;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer httpStatus;
    private String errorMsg;
    private Date createdTime;
}
