package com.chatling.gateway.controller;

import com.alibaba.fastjson2.JSON;
import com.chatling.common.dto.OpenAiDto;
import com.chatling.common.model.ApiKey;
import com.chatling.common.model.ModelConfig;
import com.chatling.common.policy.PolicyPipelineResult;
import com.chatling.engine.cache.PromptCacheService;
import com.chatling.engine.filter.RequestContext;
import com.chatling.engine.policy.PolicyPipelineExecutor;
import com.chatling.engine.service.ModelEngineService;
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
    private com.chatling.engine.governance.JsonFormatGovernor jsonFormatGovernor;

    @Resource
    private com.chatling.engine.governance.ConcurrencyControlManager concurrencyControlManager;

    @Resource
    private org.springframework.core.env.Environment environment;

    /**
     * 标准 OpenAI 聊天补全接口 (统一网关流水线: 凭据鉴权 -> 并发控制 -> 策略插件声明式治理 -> 令牌桶限流 -> 负载均衡 -> 异步审计)
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
            return Mono.just(ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(buildErrorMap("Active concurrency limit exceeded [max=" + maxConcurrency + "]. Please wait for active streams to finish.")));
        }

        // 3. 细粒度模型白名单权限校验 (Model RBAC)
        if (!isModelAllowed(apiKey.getAllowedModels(), targetModelName)) {
            concurrencyControlManager.release(apiKeyStr);
            log.warn("[-] [Permission Denied] ApiKey: {} not authorized for model: {}", apiKeyStr, targetModelName);
            return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).body(buildErrorMap("Model not allowed for this API Key. Allowed: " + apiKey.getAllowedModels())));
        }

        // 4. 周期配额与总额度检查 (Quota Check)
        if (apiKey.getTotalQuota() != null && apiKey.getTotalQuota() > 0
                && apiKey.getUsedQuota() != null && apiKey.getUsedQuota() >= apiKey.getTotalQuota()) {
            concurrencyControlManager.release(apiKeyStr);
            log.warn("[-] [Quota Exceeded] ApiKey: {}, used: {}, total: {}", apiKeyStr, apiKey.getUsedQuota(), apiKey.getTotalQuota());
            return Mono.just(ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(buildErrorMap("Total quota exceeded for this API key")));
        }

        // 5. 组装请求上下文 (RequestContext)
        String promptText = extractPromptText(request);
        int activeCount = concurrencyControlManager.getActiveCount(apiKeyStr);
        RequestContext requestContext = RequestContext.builder()
                .apiKey(apiKeyStr)
                .ownerName(apiKey.getOwnerName())
                .clientIp("127.0.0.1")
                .modelName(targetModelName)
                .originalPrompt(promptText)
                .activeConcurrency(activeCount)
                .requestTime(startTime)
                .build();

        // 6. 执行声明式策略责任链 (缓存加速 / 敏感词 / 越狱拦截 / PII脱敏 / 限流 / 绿网机审)
        PolicyPipelineResult pipelineResult = policyPipelineExecutor.executePipeline(requestContext);

        // 6.1 若命中缓存，极速短路直回 (TTFT < 15ms, 0 Token)
        if (pipelineResult.isCacheHit()) {
            return handleCacheHitResponse(apiKey, apiKeyStr, requestId, targetModelName, request, pipelineResult.getCachedContent());
        }

        // 6.2 处理策略阻断
        if (pipelineResult.isRejected()) {
            concurrencyControlManager.release(apiKeyStr);
            log.warn("[-] [Policy Blocked] model={}, plugin={}, code={}, msg={}",
                    targetModelName, pipelineResult.getHitRuleCode(), pipelineResult.getRejectCode(), pipelineResult.getMessage());
            gatewayService.recordSensitiveBlockedAudit(requestId, apiKeyStr, apiKey.getOwnerName(), targetModelName, pipelineResult.getMessage());
            return Mono.just(ResponseEntity.status(pipelineResult.getRejectCode()).body(buildErrorMap(pipelineResult.getMessage())));
        }

        // 6.3 若触发动态脱敏改写，同步更新请求消息体
        if (pipelineResult.isMasked() && pipelineResult.getModifiedPrompt() != null) {
            promptText = pipelineResult.getModifiedPrompt();
            applyMaskedPrompt(request, promptText);
        }
        final String finalPromptText = promptText;
        final String effectiveTargetModel = pipelineResult.isFallback() ? pipelineResult.getFallbackModel() : targetModelName;
        if (pipelineResult.isFallback()) {
            log.info("[*] [Policy Fallback] switching {} -> {}", targetModelName, effectiveTargetModel);
        }

        // 7. TPM 令牌桶与 QPS 速率限制校验
        int estimatedPromptTokens = estimateTokens(request);
        boolean allowed = rateLimiterService.tryAcquire(apiKeyStr, estimatedPromptTokens, apiKey.getTpmLimit(), apiKey.getQpsLimit());
        if (!allowed) {
            concurrencyControlManager.release(apiKeyStr);
            return Mono.just(ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(buildErrorMap("Rate limit exceeded (TPM or QPS) for model: " + effectiveTargetModel)));
        }

        // 8. 发起大模型流式推理与全生命周期处理 (流式 SSE -> 缓存回写 -> 异步审计)
        return executeStreamChat(apiKey, apiKeyStr, requestId, targetModelName, effectiveTargetModel, finalPromptText, request, estimatedPromptTokens, startTime);
    }

    /**
     * 获取可用模型列表 (兼容 OpenAI /v1/models)
     */
    @GetMapping("/models")
    public Mono<ResponseEntity<?>> listModels() {
        List<ModelConfig> configs = gatewayService.listAvailableModels();
        List<Map<String, Object>> data = new ArrayList<>();
        for (ModelConfig cfg : configs) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", cfg.getModelName());
            m.put("object", "model");
            m.put("created", System.currentTimeMillis() / 1000);
            m.put("owned_by", cfg.getProviderType());
            m.put("display_name", cfg.getDisplayName());
            m.put("description", cfg.getDescription());
            data.add(m);
        }
        Map<String, Object> res = new HashMap<>();
        res.put("object", "list");
        res.put("data", data);
        return Mono.just(ResponseEntity.ok(res));
    }

    // ==================== 内部辅助与流式管道方法 ====================

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
        if (allowedModels == null || allowedModels.trim().isEmpty() || "*".equals(allowedModels.trim())) {
            return true;
        }
        String[] models = allowedModels.split(",");
        for (String m : models) {
            if (m.trim().equalsIgnoreCase(targetModel.trim())) {
                return true;
            }
        }
        return false;
    }

    private int estimateTokens(OpenAiDto.ChatCompletionRequest request) {
        if (request.getMessages() == null || request.getMessages().isEmpty()) {
            return 10;
        }
        int totalChars = 0;
        for (OpenAiDto.ChatMessage msg : request.getMessages()) {
            if (msg.getContent() != null) {
                totalChars += msg.getContent().length();
            }
        }
        return Math.max(1, totalChars / 2);
    }

    private String extractPromptText(OpenAiDto.ChatCompletionRequest request) {
        if (request.getMessages() == null || request.getMessages().isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (OpenAiDto.ChatMessage msg : request.getMessages()) {
            if ("user".equalsIgnoreCase(msg.getRole()) && msg.getContent() != null) {
                sb.append(msg.getContent()).append(" ");
            }
        }
        return sb.toString().trim();
    }

    private void applyMaskedPrompt(OpenAiDto.ChatCompletionRequest request, String maskedText) {
        if (request.getMessages() == null || maskedText == null) return;
        for (int i = request.getMessages().size() - 1; i >= 0; i--) {
            OpenAiDto.ChatMessage msg = request.getMessages().get(i);
            if ("user".equalsIgnoreCase(msg.getRole())) {
                msg.setContent(maskedText);
                break;
            }
        }
    }

    private ModelConfig resolveActiveModelConfig(String effectiveTargetModel) {
        ModelConfig baseConfig = gatewayService.getEffectiveModelConfig(effectiveTargetModel);
        String selectedUrl = modelLoadBalancer.selectTargetUrl(effectiveTargetModel, baseConfig.getBaseUrl());
        return ModelConfig.builder()
                .id(baseConfig.getId())
                .modelName(baseConfig.getModelName())
                .displayName(baseConfig.getDisplayName())
                .providerType(baseConfig.getProviderType())
                .baseUrl(selectedUrl)
                .apiSecret(baseConfig.getApiSecret())
                .fallbackModel(baseConfig.getFallbackModel())
                .timeoutMs(baseConfig.getTimeoutMs())
                .status(baseConfig.getStatus())
                .description(baseConfig.getDescription())
                .build();
    }

    private Map<String, Object> buildErrorMap(String message) {
        Map<String, Object> error = new HashMap<>();
        error.put("message", message);
        error.put("type", "gateway_error");
        error.put("code", 400);
        Map<String, Object> res = new HashMap<>();
        res.put("error", error);
        return res;
    }

    /**
     * 处理缓存命中响应 (Cache Hit)
     */
    private Mono<ResponseEntity<?>> handleCacheHitResponse(
            ApiKey apiKey,
            String apiKeyStr,
            String requestId,
            String targetModelName,
            OpenAiDto.ChatCompletionRequest request,
            String cachedContent) {

        concurrencyControlManager.release(apiKeyStr);
        log.info("[*] [Prompt Cache HIT] Returning cached stream response for model: {}", targetModelName);

        gatewayService.recordCacheHitAudit(requestId, apiKeyStr, apiKey.getOwnerName(), targetModelName, 5, 10);

        if (Boolean.FALSE.equals(request.getStream())) {
            OpenAiDto.ChatCompletionResponse syncResponse = OpenAiDto.ChatCompletionResponse.builder()
                    .id(requestId)
                    .object("chat.completion")
                    .created(System.currentTimeMillis() / 1000)
                    .model(targetModelName)
                    .choices(Collections.singletonList(
                            OpenAiDto.Choice.builder()
                                    .index(0)
                                    .message(OpenAiDto.ChatMessage.builder()
                                            .role("assistant")
                                            .content(cachedContent)
                                            .build())
                                    .finishReason("stop")
                                    .build()
                    ))
                    .usage(OpenAiDto.Usage.builder()
                            .promptTokens(0)
                            .completionTokens(0)
                            .totalTokens(0)
                            .build())
                    .build();

            return Mono.just(ResponseEntity.ok()
                    .header("X-Request-Id", requestId)
                    .header("X-Cache-Status", "HIT")
                    .body(syncResponse));
        }

        Flux<String> cachedStreamFlux = promptCacheService.createCachedStream(targetModelName, cachedContent)
                .map(JSON::toJSONString)
                .concatWith(Flux.just("[DONE]"));

        return Mono.just(ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_EVENT_STREAM_VALUE)
                .header("X-Request-Id", requestId)
                .header("X-Cache-Status", "HIT")
                .body(cachedStreamFlux));
    }

    /**
     * 执行上游大模型流式推理调用及全生命周期管理 (负载均衡 -> SSE 推送 -> 缓存异步写入 -> 异步审计)
     */
    private Mono<ResponseEntity<?>> executeStreamChat(
            ApiKey apiKey,
            String apiKeyStr,
            String requestId,
            String targetModelName,
            String effectiveTargetModel,
            String finalPromptText,
            OpenAiDto.ChatCompletionRequest request,
            int estimatedPromptTokens,
            long startTime) {

        // 1. 查找模型配置与负载均衡寻址 (LB)
        ModelConfig activeConfig = resolveActiveModelConfig(effectiveTargetModel);

        log.info("==> [Engine Dispatch] Route model: [{}] -> Provider: [{}], BaseURL: [{}], SecretLen: [{}]",
                targetModelName, activeConfig.getProviderType(), activeConfig.getBaseUrl(),
                (activeConfig.getApiSecret() != null ? activeConfig.getApiSecret().length() : 0));

        String promptHash = promptCacheService.calculateHash(effectiveTargetModel, finalPromptText);

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

                        // 1. 异步回写 Prompt 缓存
                        if (fullGeneratedText.length() > 0 && promptHash != null) {
                            promptCacheService.put(promptHash, fullGeneratedText.toString(), estimatedPromptTokens, compToks);
                        }

                        // 2. 异步解耦落盘审计
                        gatewayService.recordChatSuccessAsync(requestId, apiKey, targetModelName, ttft, totalCost, estimatedPromptTokens, compToks);
                    }
                })
                .onErrorResume(e -> {
                    log.warn("[-] [Stream Fallback] Upstream error, generating simulated stream: {}", e.getMessage());
                    String fallbackText = "【网关智能仿真输出】已成功收到您关于 [" + finalPromptText + "] 的提问。当前处于离线/兜底模式，网关已完整执行鉴权、内容安全审核与计量全流水线。";
                    return promptCacheService.createCachedStream(effectiveTargetModel, fallbackText)
                            .map(JSON::toJSONString)
                            .concatWith(Flux.just("[DONE]"));
                });

        return Mono.just(ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_EVENT_STREAM_VALUE)
                .header("X-Request-Id", requestId)
                .header("X-Cache-Status", "MISS")
                .body(streamFlux));
    }
}
