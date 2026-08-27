# chatling-dataplane (AI 网关数据面模块)

## 1. 模块定位
`chatling-dataplane` 是灵犀 AI 网关的**高并发流量代理与数据面枢纽**。负责拦截外部业务请求，执行 API Key 鉴权校验、Token 桶流量限流、流式 SSE 响应透传，并在流结束时完成毫秒级 Token 统计与审计日志落盘。

---

## 2. 核心功能与架构

```
com.chatling.gateway/
├── controller/
│   └── GatewayChatController.java      # 网关核心对外 API (/v1/chat/completions, /v1/models)
├── service/
│   └── TokenRateLimiterService.java    # 基于 Caffeine 高性能本地滑动窗口的 TPM / QPS 限流器
└── repository/
    └── ChatlingDao.java                # 数据访问层 (JdbcTemplate，负责 Key校验、用量聚合、流水落库)
```

---

## 3. 核心处理流水线 (Gateway Pipeline)

```mermaid
flowchart LR
    Request["POST /v1/chat/completions"] --> Auth["1. API Key 鉴权校验"]
    Auth --> Quota["2. 模型权限与总配额检查"]
    Quota --> RateLimit["3. TPM / QPS 令牌桶限流"]
    RateLimit --> Engine["4. 调用 chatling-core-engine"]
    Engine --> Stream["5. SSE 响应式流式透传 (打字效果)"]
    Stream --> Audit["6. 流结束异步记录 TTFT、Token消耗与落库"]
```

---

## 4. 关键指标与特性
- **标准 OpenAI 兼容**：客户端只需配置 `Authorization: Bearer sk-chatling-xxx` 即可无缝接入。
- **精确度量**：实时捕获 **TTFT（Time To First Token 首字延迟）**、总请求耗时、Prompt Tokens 与 Completion Tokens。
- **高并发保护**：基于 TPM（Tokens Per Minute）与 QPS 双重限流保护，防止单一业务突发流量打满 GPU 推理池。
