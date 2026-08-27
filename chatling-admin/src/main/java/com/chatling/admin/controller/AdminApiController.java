package com.chatling.admin.controller;

import com.chatling.common.model.ApiKey;
import com.chatling.common.model.ChatAudit;
import com.chatling.common.model.CommonResult;
import com.chatling.common.model.ModelConfig;
import com.chatling.common.model.UsageDaily;
import com.chatling.engine.service.ModelEngineService;
import com.chatling.gateway.repository.ChatlingDao;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.security.SecureRandom;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/admin")
public class AdminApiController {

    private final ChatlingDao chatlingDao;
    private final ModelEngineService modelEngineService;
    private static final String KEY_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789";

    public AdminApiController(ChatlingDao chatlingDao, ModelEngineService modelEngineService) {
        this.chatlingDao = chatlingDao;
        this.modelEngineService = modelEngineService;
    }

    // ==================== API Key 治理 ====================

    @GetMapping("/apikeys")
    public Mono<CommonResult<List<ApiKey>>> listApiKeys() {
        return Mono.fromCallable(() -> CommonResult.success(chatlingDao.listApiKeys()));
    }

    @PostMapping("/apikeys")
    public Mono<CommonResult<ApiKey>> createApiKey(@RequestBody ApiKey keyReq) {
        return Mono.fromCallable(() -> {
            String randomStr = generateRandomString(24);
            String fullKey = "sk-chatling-" + randomStr;
            keyReq.setApiKey(fullKey);
            if (keyReq.getStatus() == null) keyReq.setStatus(1);
            if (keyReq.getTpmLimit() == null || keyReq.getTpmLimit() <= 0) keyReq.setTpmLimit(60000);
            if (keyReq.getQpsLimit() == null || keyReq.getQpsLimit() <= 0) keyReq.setQpsLimit(20);
            if (keyReq.getTotalQuota() == null) keyReq.setTotalQuota(-1L);
            keyReq.setUsedQuota(0L);
            if (keyReq.getAllowedModels() == null || keyReq.getAllowedModels().isEmpty()) {
                keyReq.setAllowedModels("*");
            }
            chatlingDao.insertApiKey(keyReq);
            return CommonResult.success(keyReq);
        });
    }

    @PutMapping("/apikeys/{apiKey}/status")
    public Mono<CommonResult<Void>> updateApiKeyStatus(@PathVariable String apiKey, @RequestParam int status) {
        return Mono.fromCallable(() -> {
            chatlingDao.updateApiKeyStatus(apiKey, status);
            return CommonResult.success();
        });
    }

    @DeleteMapping("/apikeys/{id}")
    public Mono<CommonResult<Void>> deleteApiKey(@PathVariable Long id) {
        return Mono.fromCallable(() -> {
            chatlingDao.deleteApiKey(id);
            return CommonResult.success();
        });
    }

    // ==================== 模型配置与连通性探活 ====================

    @GetMapping("/models")
    public Mono<CommonResult<List<ModelConfig>>> listModels() {
        return Mono.fromCallable(() -> CommonResult.success(chatlingDao.listModelConfigs()));
    }

    @PostMapping("/models")
    public Mono<CommonResult<ModelConfig>> addModel(@RequestBody ModelConfig modelConfig) {
        return Mono.fromCallable(() -> {
            if (modelConfig.getStatus() == null) modelConfig.setStatus(1);
            if (modelConfig.getTimeoutMs() == null) modelConfig.setTimeoutMs(60000);
            chatlingDao.insertModelConfig(modelConfig);
            return CommonResult.success(modelConfig);
        });
    }

    @PutMapping("/models")
    public Mono<CommonResult<ModelConfig>> updateModel(@RequestBody ModelConfig modelConfig) {
        return Mono.fromCallable(() -> {
            chatlingDao.updateModelConfig(modelConfig);
            return CommonResult.success(modelConfig);
        });
    }

    @DeleteMapping("/models/{id}")
    public Mono<CommonResult<Void>> deleteModel(@PathVariable Long id) {
        return Mono.fromCallable(() -> {
            chatlingDao.deleteModelConfig(id);
            return CommonResult.success();
        });
    }

    /**
     * 连通性测试：向填写的真实上游模型发送探活请求
     */
    @PostMapping("/models/test-connection")
    public Mono<CommonResult<Map<String, Object>>> testModelConnection(@RequestBody ModelConfig modelConfig) {
        long start = System.currentTimeMillis();
        return modelEngineService.testModelConnectivity(modelConfig)
                .map(ok -> {
                    long cost = System.currentTimeMillis() - start;
                    Map<String, Object> map = new HashMap<>();
                    map.put("connected", ok);
                    map.put("latencyMs", cost);
                    map.put("message", ok ? "连接成功，大模型响应正常" : "连接失败，请检查 BaseURL 或 API Key");
                    return CommonResult.success(map);
                });
    }

    // ==================== 看板统计与审计 ====================

    @GetMapping("/dashboard/stats")
    public Mono<CommonResult<Map<String, Object>>> getDashboardStats() {
        return Mono.fromCallable(() -> {
            Map<String, Object> stats = new HashMap<>();
            Long totalTokens = chatlingDao.getTotalTokensSum();
            Integer totalReqs = chatlingDao.getTotalRequestsSum();
            Double avgTtft = chatlingDao.getAvgTtft();
            List<ApiKey> keys = chatlingDao.listApiKeys();
            long activeKeys = keys.stream().filter(k -> k.getStatus() == 1).count();

            stats.put("totalTokens", totalTokens);
            stats.put("totalRequests", totalReqs);
            stats.put("avgTtftMs", Math.round(avgTtft));
            stats.put("activeKeys", activeKeys);
            stats.put("totalKeys", keys.size());
            return CommonResult.success(stats);
        });
    }

    @GetMapping("/dashboard/audits")
    public Mono<CommonResult<List<ChatAudit>>> getRecentAudits(@RequestParam(defaultValue = "50") int limit) {
        return Mono.fromCallable(() -> CommonResult.success(chatlingDao.listRecentAudits(limit)));
    }

    @GetMapping("/dashboard/usage")
    public Mono<CommonResult<List<UsageDaily>>> getDailyUsage(@RequestParam(defaultValue = "30") int limit) {
        return Mono.fromCallable(() -> CommonResult.success(chatlingDao.listDailyUsage(limit)));
    }

    // ==================== 模型权限申请与审批 ====================

    @PostMapping("/applies")
    public Mono<CommonResult<com.chatling.common.model.ModelApply>> submitApply(@RequestBody com.chatling.common.model.ModelApply apply) {
        return Mono.fromCallable(() -> {
            apply.setStatus(0); // 待审批
            chatlingDao.insertModelApply(apply);
            return CommonResult.success(apply);
        });
    }

    @GetMapping("/applies")
    public Mono<CommonResult<List<com.chatling.common.model.ModelApply>>> listApplies(@RequestParam(required = false) Integer status) {
        return Mono.fromCallable(() -> CommonResult.success(chatlingDao.listModelApplies(status)));
    }

    @PutMapping("/applies/{id}/approve")
    public Mono<CommonResult<Void>> approveApply(@PathVariable Long id) {
        return Mono.fromCallable(() -> {
            Optional<com.chatling.common.model.ModelApply> applyOpt = chatlingDao.findModelApplyById(id);
            if (applyOpt.isPresent()) {
                com.chatling.common.model.ModelApply apply = applyOpt.get();
                chatlingDao.updateModelApplyStatus(id, 1); // 1-已通过
                chatlingDao.appendAllowedModel(apply.getApiKey(), apply.getModelName());
                log.info("Approved model permission for key: {}, model: {}", apply.getApiKey(), apply.getModelName());
            }
            return CommonResult.success();
        });
    }

    @PutMapping("/applies/{id}/reject")
    public Mono<CommonResult<Void>> rejectApply(@PathVariable Long id) {
        return Mono.fromCallable(() -> {
            chatlingDao.updateModelApplyStatus(id, 2); // 2-已驳回
            return CommonResult.success();
        });
    }

    private String generateRandomString(int length) {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(KEY_CHARS.charAt(random.nextInt(KEY_CHARS.length())));
        }
        return sb.toString();
    }
}
