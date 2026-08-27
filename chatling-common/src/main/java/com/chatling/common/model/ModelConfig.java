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
public class ModelConfig {
    private Long id;
    private String modelName;
    private String displayName;
    private String providerType; // vllm, ollama, openai, dashscope, qianfan
    private String baseUrl;
    private String apiSecret;
    private String fallbackModel;
    private Integer timeoutMs;
    private Integer status; // 1: 在线, 0: 下线
    private String description;
    private Date createdTime;
}
