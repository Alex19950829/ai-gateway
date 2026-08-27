package com.chatling.gateway.service;

import com.chatling.common.model.ApiKey;
import com.chatling.common.model.ChatAudit;
import com.chatling.common.model.ModelConfig;
import com.chatling.gateway.repository.ChatlingDao;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import javax.annotation.Resource;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@NoArgsConstructor
@AllArgsConstructor
public class GatewayService {

    @Resource
    private ChatlingDao chatlingDao;

    public Optional<ApiKey> findByApiKey(String apiKey) {
        return chatlingDao.findByApiKey(apiKey);
    }

    public ModelConfig getEffectiveModelConfig(String targetModelName) {
        return chatlingDao.findModelConfig(targetModelName).orElse(
                ModelConfig.builder()
                        .modelName(targetModelName)
                        .displayName(targetModelName)
                        .providerType("mock")
                        .baseUrl("")
                        .build()
        );
    }

    public List<ModelConfig> listAvailableModels() {
        return chatlingDao.listModelConfigs();
    }

    public ChatAudit buildChatAudit(String requestId, String apiKey, String ownerName, String modelName,
                                    long ttftMs, long totalCostMs, int promptTokens, int completionTokens,
                                    int httpStatus, String errorMsg) {
        return ChatAudit.builder()
                .requestId(requestId)
                .apiKey(apiKey)
                .ownerName(ownerName)
                .modelName(modelName)
                .ttftMs((int) ttftMs)
                .totalCostMs((int) totalCostMs)
                .promptTokens(promptTokens)
                .completionTokens(completionTokens)
                .httpStatus(httpStatus)
                .errorMsg(errorMsg)
                .build();
    }

    public com.chatling.common.model.UsageDaily buildUsageDaily(String apiKey, String ownerName, String department,
                                                                 String modelName, int promptTokens, int completionTokens) {
        return com.chatling.common.model.UsageDaily.builder()
                .statDate(java.time.LocalDate.now().toString())
                .apiKey(apiKey)
                .ownerName(ownerName)
                .department(department)
                .modelName(modelName)
                .requestCount(1)
                .promptTokens((long) promptTokens)
                .completionTokens((long) completionTokens)
                .totalTokens((long) (promptTokens + completionTokens))
                .build();
    }

    public void recordSensitiveBlockedAudit(String requestId, String apiKey, String ownerName, String modelName, String sensitiveWord) {
        ChatAudit audit = buildChatAudit(requestId, apiKey, ownerName, modelName, 2, 2, 0, 0, 400, "Sensitive content blocked: " + sensitiveWord);
        Mono.fromRunnable(() -> chatlingDao.insertAudit(audit))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();
    }

    public void recordCacheHitAudit(String requestId, String apiKey, String ownerName, String modelName, long ttftMs, long totalCostMs) {
        ChatAudit audit = buildChatAudit(requestId, apiKey, ownerName, modelName, ttftMs, totalCostMs, 0, 0, 200, "Cache Hit");
        Mono.fromRunnable(() -> chatlingDao.insertAudit(audit))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();
    }

    public void recordUsageAndAuditAsync(ChatAudit audit, com.chatling.common.model.UsageDaily usage) {
        Mono.fromRunnable(() -> {
            try {
                if (usage != null) {
                    int totalTokens = usage.getTotalTokens() != null ? usage.getTotalTokens().intValue() : 0;
                    chatlingDao.incrUsedQuota(usage.getApiKey(), totalTokens);
                    chatlingDao.recordDailyUsage(usage);
                }
                if (audit != null) {
                    chatlingDao.insertAudit(audit);
                }
            } catch (Exception e) {
                log.error("Failed to record usage and audit asynchronously for req: {}, error: {}", (audit != null ? audit.getRequestId() : "unknown"), e.getMessage(), e);
            }
        }).subscribeOn(Schedulers.boundedElastic()).subscribe();
    }

    public void recordChatSuccessAsync(String requestId, ApiKey apiKey, String targetModelName, long ttftMs, long totalCostMs, int promptTokens, int completionTokens) {
        ChatAudit audit = buildChatAudit(requestId, apiKey.getApiKey(), apiKey.getOwnerName(), targetModelName,
                ttftMs, totalCostMs, promptTokens, completionTokens, 200, null);
        com.chatling.common.model.UsageDaily usage = buildUsageDaily(apiKey.getApiKey(), apiKey.getOwnerName(),
                apiKey.getDepartment(), targetModelName, promptTokens, completionTokens);
        recordUsageAndAuditAsync(audit, usage);
    }
}
