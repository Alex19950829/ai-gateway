package com.chatling.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 声明式模型策略与插件配置 (Declarative Model Policy Configuration)
 * 摒弃复杂的脚本代码编写，通过结构化参数与开关直接定义模型的各项防护与加速能力
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelPolicyConfig implements Serializable {
    private static final long serialVersionUID = 1L;

    private String modelName;          // 模型标识，如 "deepseek-v3", "qwen-max", 或 "*" (全局默认)
    private String displayName;        // 显示名称
    
    // 1. Prompt 缓存插件 (Exact Cache)
    @Builder.Default
    private Boolean enableCache = true;
    @Builder.Default
    private Long cacheTtlSeconds = 7200L; // 缓存有效期，默认 2 小时

    // 2. DFA 敏感词硬拦截插件
    @Builder.Default
    private Boolean enableSensitiveFilter = true;

    // 3. 个人隐私 (PII) 数据动态脱敏插件
    @Builder.Default
    private Boolean enableDataMasking = true;
    @Builder.Default
    private String maskMode = "MASK";   // MASK (掩码打码) 或 PLACEHOLDER (占位符还原)

    // 4. 动态滑动窗口限流插件
    @Builder.Default
    private Boolean enableRateLimit = true;
    @Builder.Default
    private Integer customQpmLimit = 60; // 每分钟请求数限制

    // 5. 阿里云绿网 2.0 云端深度机审插件
    @Builder.Default
    private Boolean enableAliyunGreen = false;

    // 6. 防越狱/提示词注入前置拦截插件
    @Builder.Default
    private Boolean enableJailbreakFilter = true;

    // 7. 备用降级模型 (Fallback)
    private String fallbackModel;

    // 状态: 1: 启用, 0: 停用
    @Builder.Default
    private Integer status = 1;
}
