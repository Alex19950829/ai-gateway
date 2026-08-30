package com.chatling.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiKey {
    private Long id;
    private String apiKey;
    private String keyName;
    private String ownerName;
    private String department;
    private String allowedModels; // 逗号分隔模型名称列表，或 *
    private Integer tpmLimit;     // 每分钟 Token 限额
    private Integer qpsLimit;     // QPS 限额
    private Long totalQuota;      // 总配额 (-1 表示无限制)
    private Long usedQuota;       // 已用配额
    private Integer status;       // 1: 启用, 0: 禁用
    private Integer maxConcurrency; // 最大活跃并发连接数 (默认 5)
    private String qosTier;       // QoS 服务质量等级: VIP, STANDARD, FREE
    private String quotaCycle;    // 配额刷新周期: WEEKLY, MONTHLY, NEVER
    private Long cycleQuotaLimit; // 周期配额上限
    private Long lastCycleResetTime; // 上次周期重置时间戳
    private Integer enableDataMasking; // 1: 开启隐私数据脱敏, 0: 关闭
    private Date createdTime;
    private Date updatedTime;
}
