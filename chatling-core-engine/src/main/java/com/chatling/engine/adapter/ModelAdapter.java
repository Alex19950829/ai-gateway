package com.chatling.engine.adapter;

import com.chatling.common.dto.OpenAiDto;
import com.chatling.common.model.ModelConfig;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ModelAdapter {
    /**
     * 判断是否支持该 Provider 类型
     */
    boolean supports(String providerType);

    /**
     * 发起流式对话
     */
    Flux<OpenAiDto.ChatCompletionChunk> streamChat(ModelConfig config, OpenAiDto.ChatCompletionRequest request);

    /**
     * 发起同步对话
     */
    Mono<OpenAiDto.ChatCompletionResponse> syncChat(ModelConfig config, OpenAiDto.ChatCompletionRequest request);
}
