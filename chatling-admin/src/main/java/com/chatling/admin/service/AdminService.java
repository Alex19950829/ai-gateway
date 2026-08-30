package com.chatling.admin.service;

import com.chatling.common.model.ApiKey;
import com.chatling.common.model.ChatAudit;
import com.chatling.common.model.ModelApply;
import com.chatling.common.model.ModelConfig;
import com.chatling.common.model.UsageDaily;
import com.chatling.gateway.repository.ChatlingDao;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.security.SecureRandom;
import java.util.*;

@Slf4j
@Service
@NoArgsConstructor
@AllArgsConstructor
public class AdminService {

    @Resource
    private ChatlingDao chatlingDao;

    private static final String KEY_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789";

    // ==================== API Key 治理 ====================

    public List<ApiKey> listApiKeys() {
        return chatlingDao.listApiKeys();
    }

    public ApiKey createApiKey(ApiKey keyReq) {
        String randomStr = generateRandomString(24);
        String fullKey = "sk-chatling-" + randomStr;
        keyReq.setApiKey(fullKey);
        if (keyReq.getStatus() == null) keyReq.setStatus(1);
        if (keyReq.getTpmLimit() == null || keyReq.getTpmLimit() <= 0) keyReq.setTpmLimit(60000);
        if (keyReq.getQpsLimit() == null || keyReq.getQpsLimit() <= 0) keyReq.setQpsLimit(20);
        if (keyReq.getMaxConcurrency() == null || keyReq.getMaxConcurrency() <= 0) keyReq.setMaxConcurrency(5);
        if (keyReq.getQosTier() == null || keyReq.getQosTier().trim().isEmpty()) keyReq.setQosTier("STANDARD");
        if (keyReq.getQuotaCycle() == null || keyReq.getQuotaCycle().trim().isEmpty()) keyReq.setQuotaCycle("MONTHLY");
        if (keyReq.getCycleQuotaLimit() == null || keyReq.getCycleQuotaLimit() <= 0) keyReq.setCycleQuotaLimit(1000000L);
        if (keyReq.getEnableDataMasking() == null) keyReq.setEnableDataMasking(0);
        if (keyReq.getTotalQuota() == null) keyReq.setTotalQuota(-1L);
        keyReq.setUsedQuota(0L);
        if (keyReq.getAllowedModels() == null || keyReq.getAllowedModels().isEmpty()) {
            keyReq.setAllowedModels("*");
        }
        chatlingDao.insertApiKey(keyReq);
        return keyReq;
    }

    public void updateApiKeyStatus(String apiKey, int status) {
        chatlingDao.updateApiKeyStatus(apiKey, status);
    }

    public void deleteApiKey(Long id) {
        chatlingDao.deleteApiKey(id);
    }

    // ==================== 模型配置 ====================

    public List<ModelConfig> listModels() {
        return chatlingDao.listModelConfigs();
    }

    public ModelConfig addModel(ModelConfig modelConfig) {
        if (modelConfig.getStatus() == null) modelConfig.setStatus(1);
        if (modelConfig.getTimeoutMs() == null) modelConfig.setTimeoutMs(60000);
        chatlingDao.insertModelConfig(modelConfig);
        return modelConfig;
    }

    public ModelConfig updateModel(ModelConfig modelConfig) {
        chatlingDao.updateModelConfig(modelConfig);
        return modelConfig;
    }

    public void deleteModel(Long id) {
        chatlingDao.deleteModelConfig(id);
    }

    // ==================== 看板统计与审计 ====================

    public Map<String, Object> getDashboardStats() {
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
        return stats;
    }

    public List<ChatAudit> listRecentAudits(int limit) {
        return chatlingDao.listRecentAudits(limit);
    }

    public List<UsageDaily> listDailyUsage(int limit) {
        return chatlingDao.listDailyUsage(limit);
    }

    // ==================== 模型权限申请与审批 ====================

    public ModelApply submitApply(ModelApply apply) {
        apply.setStatus(0); // 待审批
        chatlingDao.insertModelApply(apply);
        return apply;
    }

    public List<ModelApply> listModelApplies(Integer status) {
        return chatlingDao.listModelApplies(status);
    }

    public void approveApply(Long id) {
        Optional<ModelApply> applyOpt = chatlingDao.findModelApplyById(id);
        if (applyOpt.isPresent()) {
            ModelApply apply = applyOpt.get();
            chatlingDao.updateModelApplyStatus(id, 1); // 1-已通过
            chatlingDao.appendAllowedModel(apply.getApiKey(), apply.getModelName());
            log.info("Approved model permission for key: {}, model: {}", apply.getApiKey(), apply.getModelName());
        }
    }

    public void rejectApply(Long id) {
        chatlingDao.updateModelApplyStatus(id, 2); // 2-已驳回
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
