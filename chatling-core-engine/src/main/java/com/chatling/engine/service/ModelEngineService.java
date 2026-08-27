package com.chatling.engine.service;

import com.chatling.common.dto.OpenAiDto;
import com.chatling.common.model.ModelConfig;
import com.chatling.engine.adapter.MockModelAdapter;
import com.chatling.engine.adapter.ModelAdapter;
import com.chatling.engine.template.PromptTemplateEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@Service
public class ModelEngineService {

    private final List<ModelAdapter> adapters;
    private final MockModelAdapter mockAdapter;
    private final PromptTemplateEngine promptTemplateEngine;

    public ModelEngineService(List<ModelAdapter> adapters,
                              MockModelAdapter mockAdapter,
                              PromptTemplateEngine promptTemplateEngine) {
        this.adapters = adapters;
        this.mockAdapter = mockAdapter;
        this.promptTemplateEngine = promptTemplateEngine;
    }

    /**
     * 发起统一大模型流式调用
     */
    public Flux<OpenAiDto.ChatCompletionChunk> streamChat(ModelConfig config, OpenAiDto.ChatCompletionRequest request) {
        ModelAdapter adapter = selectAdapter(config);
        log.info("Engine dispatching STREAM to model: {}, provider: {}, baseUrl: {}",
                config.getModelName(), config.getProviderType(), config.getBaseUrl());

        return adapter.streamChat(config, request)
                .onErrorResume(e -> {
                    log.warn("Model {} failed with error: [{}], checking fallback...", config.getModelName(), e.getMessage());
                    // 如果存在配置的备用模型或主力失败，降级调用 Mock/Fallback
                    return mockAdapter.streamChat(config, request);
                });
    }

    /**
     * 发起统一大模型同步调用
     */
    public Mono<OpenAiDto.ChatCompletionResponse> syncChat(ModelConfig config, OpenAiDto.ChatCompletionRequest request) {
        ModelAdapter adapter = selectAdapter(config);
        log.info("Engine dispatching SYNC to model: {}, provider: {}", config.getModelName(), config.getProviderType());

        return adapter.syncChat(config, request)
                .onErrorResume(e -> {
                    log.warn("Model {} sync failed with error: {}, fallback to mock", config.getModelName(), e.getMessage());
                    return mockAdapter.syncChat(config, request);
                });
    }

    /**
     * 针对指定模型发起 1 Token 的轻量探活连通性测试
     */
    public Mono<Boolean> testModelConnectivity(ModelConfig config) {
        if ("mock".equalsIgnoreCase(config.getProviderType()) || config.getBaseUrl() == null || config.getBaseUrl().isEmpty()) {
            return Mono.just(true);
        }
        ModelAdapter adapter = selectAdapter(config);
        OpenAiDto.ChatCompletionRequest pingReq = OpenAiDto.ChatCompletionRequest.builder()
                .model(config.getModelName())
                .messages(java.util.Collections.singletonList(
                        OpenAiDto.ChatMessage.builder().role("user").content("ping").build()
                ))
                .maxTokens(1)
                .stream(false)
                .build();

        return adapter.syncChat(config, pingReq)
                .map(res -> res != null && res.getChoices() != null && !res.getChoices().isEmpty())
                .onErrorReturn(false);
    }

    public PromptTemplateEngine getPromptTemplateEngine() {
        return promptTemplateEngine;
    }

    private ModelAdapter selectAdapter(ModelConfig config) {
        if (config == null || "mock".equalsIgnoreCase(config.getProviderType()) 
                || config.getBaseUrl() == null || config.getBaseUrl().trim().isEmpty()) {
            return mockAdapter;
        }
        for (ModelAdapter adapter : adapters) {
            if (adapter.supports(config.getProviderType())) {
                return adapter;
            }
        }
        return mockAdapter;
    }
}
