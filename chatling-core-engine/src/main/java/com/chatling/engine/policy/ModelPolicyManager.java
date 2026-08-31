package com.chatling.engine.policy;

import com.chatling.common.model.ModelPolicyConfig;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 声明式模型策略管理器 (Model Policy Manager)
 * 集中管理 7 大标准模型的插件开关与参数配置，支持 UI 即时动态热生效
 */
@Service
public class ModelPolicyManager {

    private final Map<String, ModelPolicyConfig> policyMap = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        policyMap.clear();

        // 1. 火山方舟 Coding Plan 专属策略
        savePolicy(ModelPolicyConfig.builder()
                .modelName("ark-code-latest")
                .displayName("火山方舟 Coding Plan (DeepSeek/Doubao) 安全策略")
                .enableCache(true)
                .cacheTtlSeconds(7200L)
                .enableSensitiveFilter(true)
                .enableJailbreakFilter(true)
                .enableDataMasking(true)
                .maskMode("MASK")
                .enableRateLimit(true)
                .customQpmLimit(100)
                .enableAliyunGreen(true)
                .status(1)
                .build());

        // 2. DeepSeek 生产级专属策略
        savePolicy(ModelPolicyConfig.builder()
                .modelName("deepseek-chat")
                .displayName("DeepSeek 生产级全链路防护与缓存策略")
                .enableCache(true)
                .cacheTtlSeconds(7200L)
                .enableSensitiveFilter(true)
                .enableJailbreakFilter(true)
                .enableDataMasking(true)
                .maskMode("MASK")
                .enableRateLimit(true)
                .customQpmLimit(120)
                .enableAliyunGreen(true)
                .status(1)
                .build());

        // 3. 通义千问 Qwen-Plus 策略
        savePolicy(ModelPolicyConfig.builder()
                .modelName("qwen-plus")
                .displayName("阿里通义千问 Plus 官方安全策略")
                .enableCache(true)
                .cacheTtlSeconds(7200L)
                .enableSensitiveFilter(true)
                .enableJailbreakFilter(true)
                .enableDataMasking(true)
                .maskMode("MASK")
                .enableRateLimit(true)
                .customQpmLimit(80)
                .enableAliyunGreen(true)
                .status(1)
                .build());

        // 4. 智谱 GLM-4 Flash 策略
        savePolicy(ModelPolicyConfig.builder()
                .modelName("glm-4-flash")
                .displayName("智谱 GLM-4 Flash 极速安全策略")
                .enableCache(true)
                .cacheTtlSeconds(7200L)
                .enableSensitiveFilter(true)
                .enableJailbreakFilter(true)
                .enableDataMasking(true)
                .maskMode("MASK")
                .enableRateLimit(true)
                .customQpmLimit(150)
                .enableAliyunGreen(true)
                .status(1)
                .build());

        // 5. 月之暗面 Kimi (Moonshot) 策略
        savePolicy(ModelPolicyConfig.builder()
                .modelName("moonshot-v1-8k")
                .displayName("Kimi 月之暗面 Moonshot 策略")
                .enableCache(true)
                .cacheTtlSeconds(7200L)
                .enableSensitiveFilter(true)
                .enableJailbreakFilter(true)
                .enableDataMasking(true)
                .maskMode("MASK")
                .enableRateLimit(true)
                .customQpmLimit(60)
                .enableAliyunGreen(true)
                .status(1)
                .build());

        // 6. Google Gemini 2.5 Flash 策略
        savePolicy(ModelPolicyConfig.builder()
                .modelName("gemini-2.5-flash")
                .displayName("Google Gemini 2.5 Flash 策略")
                .enableCache(true)
                .cacheTtlSeconds(7200L)
                .enableSensitiveFilter(true)
                .enableJailbreakFilter(true)
                .enableDataMasking(true)
                .maskMode("MASK")
                .enableRateLimit(true)
                .customQpmLimit(100)
                .enableAliyunGreen(true)
                .status(1)
                .build());

        // 7. Chatling 灵犀自研模型策略
        savePolicy(ModelPolicyConfig.builder()
                .modelName("chatling-turbo")
                .displayName("灵犀 Turbo 全链路极速策略")
                .enableCache(true)
                .cacheTtlSeconds(7200L)
                .enableSensitiveFilter(true)
                .enableJailbreakFilter(true)
                .enableDataMasking(true)
                .maskMode("MASK")
                .enableRateLimit(true)
                .customQpmLimit(200)
                .enableAliyunGreen(true)
                .status(1)
                .build());
    }

    public void savePolicy(ModelPolicyConfig policy) {
        if (policy != null && policy.getModelName() != null) {
            policyMap.put(policy.getModelName(), policy);
        }
    }

    /**
     * 获取指定模型的策略配置。若该模型未配置专属策略，则返回 null（不挂载任何插件，直接放行）
     */
    public ModelPolicyConfig getPolicy(String modelName) {
        if (modelName == null) {
            return null;
        }
        ModelPolicyConfig config = policyMap.get(modelName);
        if (config == null) {
            // 兼容物理端点或别名自动映射至逻辑策略
            if ("ep-m-20260414104415-9rcgn".equalsIgnoreCase(modelName) || "doubao-coding-plan".equalsIgnoreCase(modelName)) {
                config = policyMap.get("ark-code-latest");
            }
        }
        return config;
    }

    public Collection<ModelPolicyConfig> getAllPolicies() {
        return policyMap.values();
    }

    public boolean deletePolicy(String modelName) {
        if (modelName == null) return false;
        return policyMap.remove(modelName) != null;
    }

    public boolean removePolicy(String modelName) {
        return deletePolicy(modelName);
    }
}
