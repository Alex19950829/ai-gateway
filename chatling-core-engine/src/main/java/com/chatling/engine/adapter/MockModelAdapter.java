package com.chatling.engine.adapter;

import com.chatling.common.dto.OpenAiDto;
import com.chatling.common.model.ModelConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
public class MockModelAdapter implements ModelAdapter {

    @Override
    public boolean supports(String providerType) {
        return "mock".equalsIgnoreCase(providerType);
    }

    @Override
    public Flux<OpenAiDto.ChatCompletionChunk> streamChat(ModelConfig config, OpenAiDto.ChatCompletionRequest request) {
        String userPrompt = extractLastUserMessage(request);
        String mockResponse = "【灵犀 AI 网关模拟响应 (" + config.getModelName() + ")】\n\n" +
                "您好！我是灵犀大模型服务助手。您刚才提问的内容是：\"" + userPrompt + "\"。\n\n" +
                "• **网关特性**：高并发响应式 WebFlux、流式 Token 计量、智能 Fallback 容灾。\n" +
                "• **状态**：当前请求已成功通过 API Key 鉴权与 TPM 令牌桶限流。\n\n" +
                "祝您体验愉快！";

        String reqId = "chatcmpl-mock-" + UUID.randomUUID().toString().substring(0, 8);
        long created = System.currentTimeMillis() / 1000;

        // 拆词模拟打字机流式输出 (采用 fromIterable + delayElements 确保完美的响应式背压)
        List<String> words = splitIntoWords(mockResponse);
        List<OpenAiDto.ChatCompletionChunk> chunks = new ArrayList<>();
        for (int i = 0; i < words.size(); i++) {
            boolean isLast = (i == words.size() - 1);
            chunks.add(OpenAiDto.ChatCompletionChunk.builder()
                    .id(reqId)
                    .object("chat.completion.chunk")
                    .created(created)
                    .model(config.getModelName())
                    .choices(Collections.singletonList(
                            OpenAiDto.ChunkChoice.builder()
                                    .index(0)
                                    .delta(OpenAiDto.Delta.builder().content(words.get(i)).build())
                                    .finishReason(isLast ? "stop" : null)
                                    .build()
                    ))
                    .build());
        }

        return Flux.fromIterable(chunks)
                .delayElements(Duration.ofMillis(30))
                .onBackpressureBuffer();
    }

    @Override
    public Mono<OpenAiDto.ChatCompletionResponse> syncChat(ModelConfig config, OpenAiDto.ChatCompletionRequest request) {
        String userPrompt = extractLastUserMessage(request);
        String mockResponse = "【灵犀 AI 同步响应】您刚才的提问是：" + userPrompt;
        String reqId = "chatcmpl-mock-" + UUID.randomUUID().toString().substring(0, 8);
        long created = System.currentTimeMillis() / 1000;

        return Mono.just(OpenAiDto.ChatCompletionResponse.builder()
                .id(reqId)
                .object("chat.completion")
                .created(created)
                .model(config.getModelName())
                .choices(Collections.singletonList(
                        OpenAiDto.Choice.builder()
                                .index(0)
                                .message(OpenAiDto.ChatMessage.builder().role("assistant").content(mockResponse).build())
                                .finishReason("stop")
                                .build()
                ))
                .usage(OpenAiDto.Usage.builder().promptTokens(10).completionTokens(30).totalTokens(40).build())
                .build());
    }

    private String extractLastUserMessage(OpenAiDto.ChatCompletionRequest request) {
        if (request.getMessages() == null || request.getMessages().isEmpty()) {
            return "Hello";
        }
        for (int i = request.getMessages().size() - 1; i >= 0; i--) {
            if ("user".equalsIgnoreCase(request.getMessages().get(i).getRole())) {
                return request.getMessages().get(i).getContent();
            }
        }
        return request.getMessages().get(0).getContent();
    }

    private List<String> splitIntoWords(String text) {
        List<String> list = new ArrayList<>();
        int len = text.length();
        int step = 2;
        for (int i = 0; i < len; i += step) {
            int end = Math.min(i + step, len);
            list.add(text.substring(i, end));
        }
        return list;
    }
}
