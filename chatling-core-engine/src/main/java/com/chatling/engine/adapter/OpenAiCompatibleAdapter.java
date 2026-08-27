package com.chatling.engine.adapter;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.chatling.common.dto.OpenAiDto;
import com.chatling.common.model.ModelConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Collections;

@Slf4j
@Component
public class OpenAiCompatibleAdapter implements ModelAdapter {

    private final WebClient.Builder webClientBuilder;

    public OpenAiCompatibleAdapter(WebClient.Builder webClientBuilder) {
        this.webClientBuilder = webClientBuilder;
    }

    @Override
    public boolean supports(String providerType) {
        return "openai".equalsIgnoreCase(providerType)
                || "vllm".equalsIgnoreCase(providerType)
                || "sglang".equalsIgnoreCase(providerType)
                || "ollama".equalsIgnoreCase(providerType)
                || "dashscope".equalsIgnoreCase(providerType)
                || "deepseek".equalsIgnoreCase(providerType)
                || "volcengine".equalsIgnoreCase(providerType)
                || "ark".equalsIgnoreCase(providerType)
                || "gemini".equalsIgnoreCase(providerType)
                || "google".equalsIgnoreCase(providerType)
                || "zhipu".equalsIgnoreCase(providerType)
                || "glm".equalsIgnoreCase(providerType);
    }

    @Override
    public Flux<OpenAiDto.ChatCompletionChunk> streamChat(ModelConfig config, OpenAiDto.ChatCompletionRequest request) {
        WebClient client = buildClient(config);
        request.setStream(true);
        // 如果是火山方舟，将 model 字段替换为配置的 Endpoint ID
        if (config.getModelName() != null && !config.getModelName().isEmpty()) {
            request.setModel(config.getModelName());
        }

        log.info("Sending REAL SSE request to upstream: {}/chat/completions (model={})", config.getBaseUrl(), config.getModelName());

        return client.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .retrieve()
                .onStatus(HttpStatus::isError, clientResponse ->
                        clientResponse.bodyToMono(String.class)
                                .flatMap(errorBody -> {
                                    log.error("[-] Upstream HTTP {} Error from {}: {}",
                                            clientResponse.statusCode(), config.getBaseUrl(), errorBody);
                                    return Mono.error(new RuntimeException("Upstream Error (" + clientResponse.statusCode() + "): " + errorBody));
                                })
                )
                .bodyToFlux(String.class)
                .doOnNext(rawChunk -> log.info("[Upstream SSE Chunk] <= {}", rawChunk))
                .timeout(Duration.ofSeconds(60))
                .flatMap(chunk -> {
                    if (chunk == null || chunk.trim().isEmpty()) {
                        return Flux.empty();
                    }
                    String[] lines = chunk.split("\n");
                    return Flux.fromArray(lines)
                            .map(String::trim)
                            .filter(l -> l.startsWith("data:") || l.startsWith("{"))
                            .map(l -> l.startsWith("data:") ? l.substring(5).trim() : l)
                            .filter(l -> !"[DONE]".equals(l) && !l.isEmpty())
                            .flatMap(jsonStr -> {
                                try {
                                    com.alibaba.fastjson2.JSONObject root = JSON.parseObject(jsonStr);
                                    if (root != null && root.containsKey("choices")) {
                                        com.alibaba.fastjson2.JSONArray choicesArr = root.getJSONArray("choices");
                                        if (choicesArr != null && !choicesArr.isEmpty()) {
                                            com.alibaba.fastjson2.JSONObject firstChoice = choicesArr.getJSONObject(0);
                                            com.alibaba.fastjson2.JSONObject deltaObj = firstChoice.getJSONObject("delta");
                                            if (deltaObj != null) {
                                                String content = deltaObj.getString("content");
                                                String reasoning = deltaObj.getString("reasoning_content");
                                                String effectiveText = (content != null && !content.isEmpty()) ? content : reasoning;
                                                if (effectiveText != null && !effectiveText.isEmpty()) {
                                                    return Flux.just(OpenAiDto.ChatCompletionChunk.builder()
                                                            .id(root.getString("id"))
                                                            .model(root.getString("model"))
                                                            .choices(Collections.singletonList(
                                                                    OpenAiDto.ChunkChoice.builder()
                                                                            .index(0)
                                                                            .delta(OpenAiDto.Delta.builder().content(effectiveText).build())
                                                                            .build()
                                                            ))
                                                            .build());
                                                }
                                            }
                                        }
                                    }
                                } catch (Exception ex) {
                                    log.debug("Skip parse error for: {}", jsonStr);
                                }
                                return Flux.empty();
                            });
                })
                .onErrorResume(e -> {
                    log.error("Real stream call failed on model: {}, provider: {}, err: {}",
                            config.getModelName(), config.getProviderType(), e.getMessage());
                    return Flux.error(new RuntimeException("Upstream LLM Error (" + config.getModelName() + "): " + e.getMessage(), e));
                });
    }

    @Override
    public Mono<OpenAiDto.ChatCompletionResponse> syncChat(ModelConfig config, OpenAiDto.ChatCompletionRequest request) {
        WebClient client = buildClient(config);
        request.setStream(false);

        log.info("Sending REAL SYNC request to upstream: {}/chat/completions (model={})", config.getBaseUrl(), config.getModelName());

        return client.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(60))
                .map(jsonStr -> JSON.parseObject(jsonStr, OpenAiDto.ChatCompletionResponse.class))
                .onErrorResume(e -> {
                    log.error("Real sync call failed on model: {}, err: {}", config.getModelName(), e.getMessage());
                    return Mono.error(new RuntimeException("Upstream LLM Sync Error: " + e.getMessage(), e));
                });
    }

    /**
     * 自动提取厂商真实 API Key 并注入 Header
     */
    private WebClient buildClient(ModelConfig config) {
        String baseUrl = config.getBaseUrl();
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            baseUrl = "https://api.deepseek.com/v1";
        }
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }

        WebClient.Builder builder = webClientBuilder.clone().baseUrl(baseUrl);

        // 动态注入真实商业密钥
        String secret = config.getApiSecret();
        if (secret != null && !secret.trim().isEmpty()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + secret.trim());
            builder.defaultHeader("x-goog-api-key", secret.trim());
        }

        return builder.build();
    }

    private String cleanSseData(String line) {
        line = line.trim();
        if (line.startsWith("data:")) {
            line = line.substring(5).trim();
        }
        return line;
    }
}
