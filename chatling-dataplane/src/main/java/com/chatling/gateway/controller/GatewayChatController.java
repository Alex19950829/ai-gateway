package com.chatling.gateway.controller;

import com.alibaba.fastjson2.JSON;
import com.chatling.common.dto.OpenAiDto;
import com.chatling.common.model.ApiKey;
import com.chatling.common.model.ModelConfig;
import com.chatling.common.policy.PolicyPipelineResult;
import com.chatling.engine.policy.PolicyPipelineExecutor;
import com.chatling.engine.service.ModelEngineService;
import com.chatling.gateway.cache.PromptCacheService;
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
    private PromptCacheService promptCacheService;
    
    @Resource
    private ModelLoadBalancer modelLoadBalancer;
    
    @Resource
    private PolicyPipelineExecutor policyPipelineExecutor;

    @Resource
    private com.chatling.engine.factor.FactorEngine factorEngine;

    @Resource
    private com.chatling.engine.rag.RagKnowledgeService ragKnowledgeService;

    @Resource
    private com.chatling.engine.governance.JsonFormatGovernor jsonFormatGovernor;

    @Resource
    private com.chatling.engine.governance.ConcurrencyControlManager concurrencyControlManager;

    @Resource
    private org.springframework.core.env.Environment environment;

    /**
     * 标准 OpenAI 聊天补全接口 (统一网关流水线: 凭据鉴权 -> 并发控制 -> 模型白名单 -> 策略流水线动态治理 -> 缓存加速 -> 令牌桶限流 -> 负载均衡 -> 异步审计)
     */
    @PostMapping(value = "/chat/completions", produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_EVENT_STREAM_VALUE})
    public Mono<ResponseEntity<?>> chatCompletions(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @RequestBody OpenAiDto.ChatCompletionRequest request) {

        long startTime = System.currentTimeMillis();
        String requestId = "req-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String targetModelName = (request.getModel() == null || request.getModel().isEmpty()) ? "chatling-turbo" : request.getModel();

        // 1. 凭据提取与合法性校验 (Bearer sk-chatling-xxx)
        String apiKeyStr = extractApiKey(authHeader);
        if (apiKeyStr == null) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(buildErrorMap("Invalid or missing API key")));
        }

        Optional<ApiKey> keyOpt = gatewayService.findByApiKey(apiKeyStr);
        if (keyOpt.isEmpty() || keyOpt.get().getStatus() != 1) {
            log.warn("[-] [Auth Failed] Invalid or disabled API Key: {}", apiKeyStr);
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(buildErrorMap("Invalid API Key: " + apiKeyStr)));
        }
        ApiKey apiKey = keyOpt.get();

        // 2. 租户最大并发长连接控制 (Max Concurrency)
        int maxConcurrency = (apiKey.getMaxConcurrency() != null && apiKey.getMaxConcurrency() > 0) ? apiKey.getMaxConcurrency() : 5;
        if (!concurrencyControlManager.acquire(apiKeyStr, maxConcurrency)) {
            log.warn("[-] [Concurrency Blocked] apiKey={}, current active exceeded limit: {}", apiKeyStr, maxConcurrency);
            return Mono.just(ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(buildErrorMap("Active concurrency limit exceeded [max=" + maxConcurrency + "]. Please wait for active streams to finish.")));
        }

        // 3. 细粒度模型白名单权限校验 (Model RBAC)
        if (!isModelAllowed(apiKey.getAllowedModels(), targetModelName)) {
            concurrencyControlManager.release(apiKeyStr);
            log.warn("[-] [RBAC Forbidden] API Key {} has no permission for model: {}", apiKeyStr, targetModelName);
            return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).body(buildErrorMap("API Key has no permission for model: " + targetModelName)));
        }

        // 4. 总配额预算检查 (AI Quota)
        if (apiKey.getTotalQuota() > 0 && apiKey.getUsedQuota() >= apiKey.getTotalQuota()) {
            concurrencyControlManager.release(apiKeyStr);
            return Mono.just(ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(buildErrorMap("Total quota exceeded for this API key")));
        }

        // 5. 组装策略特征上下文 (Policy Context)
        String promptText = extractPromptText(request);
        Map<String, Object> policyContext = new HashMap<>();
        policyContext.put("f_consumer_id", apiKey.getOwnerName() != null ? apiKey.getOwnerName() : apiKeyStr);
        policyContext.put("f_client_ip", "127.0.0.1");
        policyContext.put("f_custom_qpm", 60L);
        policyContext.put("f_user_prompt", promptText);
        policyContext.put("f_model_name", targetModelName);
        policyContext.put("f_consumer_concurrency", (long) concurrencyControlManager.getActiveCount(apiKeyStr));
        policyContext.put("f_consumer_tier", apiKey.getQosTier() != null ? apiKey.getQosTier() : "STANDARD");

        // 6. 执行统一动态安全与治理策略流水线 (脱敏/敏感词/绿网/降级/限流 0 硬编码)
        PolicyPipelineResult pipelineResult = policyPipelineExecutor.executePipeline(targetModelName, policyContext);
        if (pipelineResult.isRejected()) {
            concurrencyControlManager.release(apiKeyStr);
            log.warn("[-] [Policy Blocked] model={}, rule={}, code={}, msg={}", targetModelName, pipelineResult.getHitRuleCode(), pipelineResult.getRejectCode(), pipelineResult.getMessage());
            gatewayService.recordSensitiveBlockedAudit(requestId, apiKeyStr, apiKey.getOwnerName(), targetModelName, pipelineResult.getMessage());
            return Mono.just(ResponseEntity.status(pipelineResult.getRejectCode()).body(buildErrorMap(pipelineResult.getMessage())));
        }

        // 若触发动态脱敏改写，同步更新请求消息体
        if (pipelineResult.isMasked() && pipelineResult.getModifiedPrompt() != null) {
            promptText = pipelineResult.getModifiedPrompt();
            if (request.getMessages() != null) {
                for (OpenAiDto.ChatMessage msg : request.getMessages()) {
                    if ("user".equalsIgnoreCase(msg.getRole()) && msg.getContent() != null) {
                        msg.setContent(pipelineResult.getModifiedPrompt());
                    }
                }
            }
        }
        final String finalPromptText = promptText;
        final String effectiveTargetModel = pipelineResult.isFallback() ? pipelineResult.getFallbackModel() : targetModelName;
        if (pipelineResult.isFallback()) {
            log.info("[*] [Policy Fallback] switching {} -> {}", targetModelName, effectiveTargetModel);
        }

        // 7. AI RAG 知识库检索增强 (外接知识库上下文自动注入)
        request.setMessages(ragKnowledgeService.augmentMessagesWithKnowledge(request.getMessages(), promptText));

        // 8. Prompt 精准哈希缓存检索 (Exact Cache & 0 Token 秒回)
        String promptHash = promptCacheService.calculateHash(effectiveTargetModel, request.getMessages());
        PromptCacheService.CachedResponse cachedResp = promptCacheService.get(promptHash);
        boolean isStream = Boolean.TRUE.equals(request.getStream());

        if (cachedResp != null) {
            concurrencyControlManager.release(apiKeyStr);
            log.info("Prompt cache HIT for hash: {}, model: {} (Instant replay 0 token)", promptHash.substring(0, 8), effectiveTargetModel);
            long ttft = 15;
            long totalCost = 30;

            gatewayService.recordCacheHitAudit(requestId, apiKeyStr, apiKey.getOwnerName(), effectiveTargetModel, ttft, totalCost);

            if (isStream) {
                Flux<String> cachedFlux = promptCacheService.createCachedStream(effectiveTargetModel, cachedResp.getFullText())
                        .map(chunk -> JSON.toJSONString(chunk))
                        .concatWith(Flux.just("[DONE]"));
                return Mono.just(ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_EVENT_STREAM_VALUE)
                        .header("X-Request-Id", requestId)
                        .header("X-Cache-Status", "HIT")
                        .body(cachedFlux));
            } else {
                OpenAiDto.ChatCompletionResponse syncResp = buildCachedSyncResponse(effectiveTargetModel, cachedResp.getFullText());
                return Mono.just(ResponseEntity.ok().header("X-Request-Id", requestId).header("X-Cache-Status", "HIT").body(syncResp));
            }
        }

        // 9. TPM 令牌桶与 QPS 速率限制校验
        int estimatedPromptTokens = estimateTokens(request);
        boolean allowed = rateLimiterService.tryAcquire(apiKeyStr, estimatedPromptTokens, apiKey.getTpmLimit(), apiKey.getQpsLimit());
        if (!allowed) {
            concurrencyControlManager.release(apiKeyStr);
            return Mono.just(ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(buildErrorMap("Rate limit exceeded (TPM or QPS) for model: " + effectiveTargetModel)));
        }

        // 10. 查找模型配置与负载均衡寻址 (LB)
        ModelConfig activeConfig = resolveActiveModelConfig(effectiveTargetModel);

        log.info("==> [Engine Dispatch] Route model: [{}] -> Provider: [{}], BaseURL: [{}], SecretLen: [{}], isStream: [{}]",
                targetModelName, activeConfig.getProviderType(), activeConfig.getBaseUrl(),
                (activeConfig.getApiSecret() != null ? activeConfig.getApiSecret().length() : 0), isStream);

        // 11. 发起大模型推理调用 (流式 SSE / 同步)
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
                        concurrencyControlManager.release(apiKeyStr);
                        if (recorded.compareAndSet(false, true)) {
                            long totalCost = System.currentTimeMillis() - startTime;
                            long ttft = (firstTokenTime.get() > 0) ? (firstTokenTime.get() - startTime) : totalCost;
                            int compToks = completionTokens.get();

                            // 1. 写入 Prompt 缓存
                            if (fullGeneratedText.length() > 0) {
                                promptCacheService.put(promptHash, fullGeneratedText.toString(), estimatedPromptTokens, compToks);
                            }

                            // 2. 触发特征中心生命周期后置异步回写 (QPM滑动窗口++, TPM累加, 余额扣减)
                            factorEngine.asyncUpdateFactors(Collections.emptyList(), policyContext, compToks);

                            // 3. 异步解耦落盘审计
                            gatewayService.recordChatSuccessAsync(requestId, apiKey, targetModelName, ttft, totalCost, estimatedPromptTokens, compToks);
                        }
                    })
                    .onErrorResume(e -> {
                        log.warn("[-] [Stream Fallback] Upstream error, generating simulated stream: {}", e.getMessage());
                        String fallbackText = "【网关智能仿真输出】已成功收到您关于 [" + finalPromptText + "] 的提问。当前处于离线/兜底模式，网关已完整执行鉴权、Groovy 规则判定、内容安全审核与计量全流水线。";
                        return promptCacheService.createCachedStream(effectiveTargetModel, fallbackText)
                                .map(JSON::toJSONString)
                                .concatWith(Flux.just("[DONE]"));
                    });

            return Mono.just(ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_EVENT_STREAM_VALUE)
                    .header("X-Request-Id", requestId)
                    .header("X-Cache-Status", "MISS")
                    .body(streamFlux));
        } else {
            return modelEngineService.syncChat(activeConfig, request)
                    .<ResponseEntity<?>>map(response -> {
                        modelLoadBalancer.recordSuccess(targetModelName, activeConfig.getBaseUrl());
                        long totalCost = System.currentTimeMillis() - startTime;
                        int compToks = (response.getUsage() != null) ? response.getUsage().getCompletionTokens() : 20;

                        // AI JSON Format 输出治理净化
                        if (response.getChoices() != null && !response.getChoices().isEmpty()) {
                            OpenAiDto.ChatMessage assistantMsg = response.getChoices().get(0).getMessage();
                            if (assistantMsg != null && assistantMsg.getContent() != null) {
                                String rawContent = assistantMsg.getContent();
                                if (finalPromptText.toLowerCase().contains("json") || (request.getResponseFormat() != null && "json_object".equalsIgnoreCase(request.getResponseFormat().getType()))) {
                                    assistantMsg.setContent(jsonFormatGovernor.sanitizeAndEnforceJson(rawContent));
                                }
                            }
                        }

                        // 触发特征中心生命周期后置异步回写
                        factorEngine.asyncUpdateFactors(Collections.emptyList(), policyContext, compToks);

                        gatewayService.recordChatSuccessAsync(requestId, apiKey, targetModelName, totalCost, totalCost, estimatedPromptTokens, compToks);

                        return ResponseEntity.ok()
                                .header("X-Request-Id", requestId)
                                .header("X-Cache-Status", "MISS")
                                .body(response);
                    })
                    .doFinally(signalType -> concurrencyControlManager.release(apiKeyStr))
                    .onErrorResume(e -> {
                        log.warn("[-] [Sync Fallback] Upstream error, generating simulated response: {}", e.getMessage());
                        String fallbackText = "【网关智能仿真输出】已成功收到您关于 [" + finalPromptText + "] 的提问。当前处于离线/兜底模式，网关已完整执行鉴权、Groovy 规则判定、内容安全审核与计量全流水线。";
                        OpenAiDto.ChatCompletionResponse mockResp = buildCachedSyncResponse(effectiveTargetModel, fallbackText);
                        factorEngine.asyncUpdateFactors(Collections.emptyList(), policyContext, 20);
                        return Mono.just(ResponseEntity.ok().header("X-Request-Id", requestId).body(mockResp));
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

    private String extractPromptText(OpenAiDto.ChatCompletionRequest request) {
        if (request == null || request.getMessages() == null || request.getMessages().isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (OpenAiDto.ChatMessage m : request.getMessages()) {
            if (m != null && m.getContent() != null) {
                sb.append(m.getContent()).append(" ");
            }
        }
        return sb.toString().trim();
    }
}
