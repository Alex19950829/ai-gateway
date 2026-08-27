package com.chatling.gateway.controller;

import com.alibaba.fastjson2.JSON;
import com.chatling.common.dto.OpenAiDto;
import com.chatling.common.model.ApiKey;
import com.chatling.common.model.ModelConfig;
import com.chatling.engine.service.ModelEngineService;
import com.chatling.gateway.cache.PromptCacheService;
import com.chatling.gateway.filter.ContentGuardrailFilter;
import com.chatling.gateway.lb.ModelLoadBalancer;
import com.chatling.gateway.service.GatewayService;
import com.chatling.gateway.service.TokenRateLimiterService;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@RestController
@NoArgsConstructor
@AllArgsConstructor
@RequestMapping("/v1")
public class GatewayChatController {

    @Resource
    private GatewayService gatewayService;
    
    @Resource
    private ModelEngineService modelEngineService;
    
    @Resource
    private TokenRateLimiterService rateLimiterService;
    
    @Resource
    private ContentGuardrailFilter contentGuardrailFilter;
    
    @Resource
    private PromptCacheService promptCacheService;
    
    @Resource
    private ModelLoadBalancer modelLoadBalancer;
    
    @Resource
    private org.springframework.core.env.Environment environment;

    /**
     * 标准 OpenAI 聊天补全接口 (完整网关流水线: 鉴权 -> 敏感词过滤 -> 缓存加速 -> 限流 -> 负载均衡 -> 计量落盘)
     */
    @PostMapping(value = "/chat/completions", produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_EVENT_STREAM_VALUE})
    public Mono<ResponseEntity<?>> chatCompletions(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @RequestBody OpenAiDto.ChatCompletionRequest request) {

        long startTime = System.currentTimeMillis();
        String requestId = "req-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String targetModelName = (request.getModel() == null || request.getModel().isEmpty()) ? "chatling-turbo" : request.getModel();

        // 1. 鉴权校验 (提取 Bearer sk-chatling-xxx)
        String apiKeyStr = extractApiKey(authHeader);
        if (apiKeyStr == null) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(buildErrorMap("InvalidORmiss API key")));
        }
        log.info("requestId={}, apiKey={}, model={}, isStream={}", requestId, apiKeyStr, targetModelName, request.getStream());
        // 1. 凭证合法性校验
        Optional<ApiKey> keyOpt = gatewayService.findByApiKey(apiKeyStr);
        if (keyOpt.isEmpty() || keyOpt.get().getStatus() != 1) {
            log.warn("[-] [Auth Failed] Invalid or disabled API Key: {}", apiKeyStr);
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(buildErrorMap("Invalid API Key: " + apiKeyStr)));
        }
        ApiKey apiKey = keyOpt.get();

        // 2. 细粒度模型白名单权限校验
        if (!isModelAllowed(apiKey.getAllowedModels(), targetModelName)) {
            log.warn("[-] [RBAC Forbidden] API Key {} has no permission for model: {}", apiKeyStr, targetModelName);
            return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).body(buildErrorMap("API Key no permission: " + targetModelName)));
        }

        if (apiKey.getTotalQuota() > 0 && apiKey.getUsedQuota() >= apiKey.getTotalQuota()) {
            return Mono.just(ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(buildErrorMap("Total quota exceeded for this API key")));
        }

        // 3. 敏感词合规安全拦截 (Guardrails)
        String sensitiveWord = checkRequestSensitive(request);
        if (sensitiveWord != null) {
            log.warn("Prompt rejected due to sensitive keyword: [{}] for key: {}", sensitiveWord, apiKeyStr);
            gatewayService.recordSensitiveBlockedAudit(requestId, apiKeyStr, apiKey.getOwnerName(), targetModelName, sensitiveWord);
            return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(buildErrorMap("包含合规违规敏感词 [" + sensitiveWord + "]，已被拦截")));
        }

        // 4. Prompt 精准哈希缓存检索 (Exact Cache)
        String promptHash = promptCacheService.calculateHash(targetModelName, request.getMessages());
        PromptCacheService.CachedResponse cachedResp = promptCacheService.get(promptHash);
        boolean isStream = Boolean.TRUE.equals(request.getStream());

        if (cachedResp != null) {
            log.info("Prompt cache HIT for hash: {}, model: {} (Instant replay 0 token)", promptHash.substring(0, 8), targetModelName);
            long ttft = 15;
            long totalCost = 30;

            gatewayService.recordCacheHitAudit(requestId, apiKeyStr, apiKey.getOwnerName(), targetModelName, ttft, totalCost);

            if (isStream) {
                Flux<String> cachedFlux = promptCacheService.createCachedStream(targetModelName, cachedResp.getFullText())
                        .map(chunk -> JSON.toJSONString(chunk))
                        .concatWith(Flux.just("[DONE]"));
                return Mono.just(ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_EVENT_STREAM_VALUE)
                        .header("X-Request-Id", requestId)
                        .header("X-Cache-Status", "HIT")
                        .body(cachedFlux));
            } else {
                OpenAiDto.ChatCompletionResponse syncResp = buildCachedSyncResponse(targetModelName, cachedResp.getFullText());
                return Mono.just(ResponseEntity.ok().header("X-Request-Id", requestId).header("X-Cache-Status", "HIT").body(syncResp));
            }
        }

        // 5. TPM 令牌桶与 QPS 限流校验
        int estimatedPromptTokens = estimateTokens(request);
        boolean allowed = rateLimiterService.tryAcquire(apiKeyStr, estimatedPromptTokens, apiKey.getTpmLimit(), apiKey.getQpsLimit());
        if (!allowed) {
            return Mono.just(ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(buildErrorMap("Rate limit exceeded (TPM or QPS) for model: " + targetModelName)));
        }

        // 6. 查找模型配置与负载均衡路由 (LB)
        ModelConfig activeConfig = resolveActiveModelConfig(targetModelName);

        log.info("==> [Engine Dispatch] Route model: [{}] -> Provider: [{}], BaseURL: [{}], SecretLen: [{}], isStream: [{}]",
                targetModelName, activeConfig.getProviderType(), activeConfig.getBaseUrl(),
                (activeConfig.getApiSecret() != null ? activeConfig.getApiSecret().length() : 0), isStream);

        // 7. 发起大模型流式调用与后置计量
        if (isStream) {
            AtomicLong firstTokenTime = new AtomicLong(0);
            AtomicInteger completionTokens = new AtomicInteger(0);
            AtomicBoolean recorded = new AtomicBoolean(false);
            StringBuilder fullGeneratedText = new StringBuilder();

            Flux<String> streamFlux = modelEngineService.streamChat(activeConfig, request)
                    .map(chunk -> {
                        if (firstTokenTime.get() == 0) {
                            firstTokenTime.set(System.currentTimeMillis());
                        }
                        if (chunk.getChoices() != null && !chunk.getChoices().isEmpty()) {
                            OpenAiDto.Delta delta = chunk.getChoices().get(0).getDelta();
                            if (delta != null && delta.getContent() != null) {
                                fullGeneratedText.append(delta.getContent());
                                completionTokens.addAndGet(Math.max(1, delta.getContent().length() / 2));
                                log.debug("[SSE Push] <= {}", delta.getContent());
                            }
                        }
                        return JSON.toJSONString(chunk);
                    })
                    .concatWith(Flux.just("[DONE]"))
                    .doOnComplete(() -> {
                        modelLoadBalancer.recordSuccess(targetModelName, activeConfig.getBaseUrl());
                        log.info("==> [Stream Success] Finished streaming model: [{}], totalChars: [{}]",
                                targetModelName, fullGeneratedText.length());
                    })
                    .doOnError(e -> {
                        modelLoadBalancer.recordFailure(targetModelName, activeConfig.getBaseUrl());
                        log.error("==> [Stream Error] Streaming failed on model: [{}], error: {}",
                                targetModelName, e.getMessage(), e);
                    })
                    .doFinally(signalType -> {
                        if (recorded.compareAndSet(false, true)) {
                            long totalCost = System.currentTimeMillis() - startTime;
                            long ttft = (firstTokenTime.get() > 0) ? (firstTokenTime.get() - startTime) : totalCost;
                            int compToks = completionTokens.get();

                            // 写入 Prompt 缓存
                            if (fullGeneratedText.length() > 0) {
                                promptCacheService.put(promptHash, fullGeneratedText.toString(), estimatedPromptTokens, compToks);
                            }

                            // 异步解耦落盘
                            gatewayService.recordChatSuccessAsync(requestId, apiKey, targetModelName, ttft, totalCost, estimatedPromptTokens, compToks);
                        }
                    });

            return Mono.just(ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_EVENT_STREAM_VALUE)
                    .header("X-Request-Id", requestId)
                    .header("X-Cache-Status", "MISS")
                    .body(streamFlux));
        } else {
            return modelEngineService.syncChat(activeConfig, request)
                    .map(response -> {
                        modelLoadBalancer.recordSuccess(targetModelName, activeConfig.getBaseUrl());
                        long totalCost = System.currentTimeMillis() - startTime;
                        int compToks = (response.getUsage() != null) ? response.getUsage().getCompletionTokens() : 20;

                        gatewayService.recordChatSuccessAsync(requestId, apiKey, targetModelName, totalCost, totalCost, estimatedPromptTokens, compToks);

                        return ResponseEntity.ok()
                                .header("X-Request-Id", requestId)
                                .header("X-Cache-Status", "MISS")
                                .body(response);
                    });
        }
    }

    /**
     * 获取可用模型列表 (兼容 OpenAI /v1/models)
     */
    @GetMapping("/models")
    public Mono<ResponseEntity<?>> listModels() {
        List<ModelConfig> list = gatewayService.listAvailableModels();
        List<Map<String, Object>> data = new ArrayList<>();
        for (ModelConfig m : list) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", m.getModelName());
            item.put("object", "model");
            item.put("created", System.currentTimeMillis() / 1000);
            item.put("owned_by", "chatling");
            item.put("display_name", m.getDisplayName());
            data.add(item);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("object", "list");
        result.put("data", data);
        return Mono.just(ResponseEntity.ok(result));
    }

    private String checkRequestSensitive(OpenAiDto.ChatCompletionRequest req) {
        if (req.getMessages() == null) {
            return null;
        }
        for (OpenAiDto.ChatMessage msg : req.getMessages()) {
            if (msg.getContent() != null) {
                String hit = contentGuardrailFilter.checkSensitiveWord(msg.getContent());
                if (hit != null) {
                    return hit;
                }
            }
        }
        return null;
    }

    private String extractApiKey(String authHeader) {
        if (authHeader == null || authHeader.trim().isEmpty()) {
            return null;
        }
        String header = authHeader.trim();
        if (header.startsWith("Bearer ")) {
            return header.substring(7).trim();
        }
        return header;
    }

    private boolean isModelAllowed(String allowedModels, String targetModel) {
        if (allowedModels == null || "*".equals(allowedModels.trim()) || allowedModels.trim().isEmpty()) {
            return true;
        }
        String[] arr = allowedModels.split(",");
        for (String m : arr) {
            if (m.trim().equalsIgnoreCase(targetModel.trim())) {
                return true;
            }
        }
        return false;
    }

    private int estimateTokens(OpenAiDto.ChatCompletionRequest req) {
        if (req.getMessages() == null || req.getMessages().isEmpty()) {
            return 10;
        }
        int totalChars = 0;
        for (OpenAiDto.ChatMessage msg : req.getMessages()) {
            if (msg.getContent() != null) {
                totalChars += msg.getContent().length();
            }
        }
        return Math.max(8, totalChars / 2);
    }

    private Map<String, Object> buildErrorMap(String msg) {
        Map<String, Object> error = new HashMap<>();
        error.put("message", msg);
        error.put("type", "invalid_request_error");
        error.put("code", 400);

        Map<String, Object> res = new HashMap<>();
        res.put("error", error);
        return res;
    }

    private OpenAiDto.ChatCompletionResponse buildCachedSyncResponse(String modelName, String content) {
        return OpenAiDto.ChatCompletionResponse.builder()
                .id("chatcmpl-cache-" + UUID.randomUUID().toString().substring(0, 8))
                .object("chat.completion")
                .created(System.currentTimeMillis() / 1000)
                .model(modelName)
                .choices(Collections.singletonList(
                        OpenAiDto.Choice.builder().index(0)
                                .message(OpenAiDto.ChatMessage.builder().role("assistant").content(content).build())
                                .finishReason("stop").build()
                ))
                .usage(OpenAiDto.Usage.builder().promptTokens(0).completionTokens(0).totalTokens(0).build())
                .build();
    }

    private ModelConfig resolveActiveModelConfig(String targetModelName) {
        ModelConfig modelConfig = gatewayService.getEffectiveModelConfig(targetModelName);
        String routedUrl = modelLoadBalancer.selectTargetUrl(targetModelName, modelConfig.getBaseUrl());

        String effectiveSecret = modelConfig.getApiSecret();
        if (effectiveSecret == null || effectiveSecret.contains("your_") || effectiveSecret.trim().isEmpty()) {
            String configSecret = environment.getProperty("chatling.gateway.secrets." + targetModelName);
            if (configSecret != null && !configSecret.trim().isEmpty()) {
                effectiveSecret = configSecret.trim();
            }
        }

        return ModelConfig.builder()
                .id(modelConfig.getId())
                .modelName(modelConfig.getModelName())
                .displayName(modelConfig.getDisplayName())
                .providerType(modelConfig.getProviderType())
                .baseUrl(routedUrl)
                .apiSecret(effectiveSecret)
                .fallbackModel(modelConfig.getFallbackModel())
                .timeoutMs(modelConfig.getTimeoutMs())
                .status(modelConfig.getStatus())
                .build();
    }
}
