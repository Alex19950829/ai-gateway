# chatling-dataplane 模块设计文档 (Architecture & Design)

## 一、 模块定位与职责
`chatling-dataplane` 是灵犀 AI 网关的**高并发流量代理与数据面枢纽**，负责对外暴露统一标准的 `/v1/chat/completions` 与 `/v1/models` 接口。

---

## 二、 网关完整处理流水线 (Gateway Full Pipeline)

```mermaid
%%{init: {'flowchart': {'curve': 'ortho'}}}%%
flowchart TD
    classDef default fill:#1e293b,stroke:#475569,stroke-width:1.5px,color:#f8fafc;
    classDef entry fill:#0f172a,stroke:#3b82f6,stroke-width:2px,color:#93c5fd;
    classDef check fill:#1e293b,stroke:#3b82f6,stroke-width:2px,color:#ffffff;
    classDef reject fill:#450a0a,stroke:#ef4444,stroke-width:1.5px,color:#fca5a5;
    classDef hit fill:#064e3b,stroke:#10b981,stroke-width:1.5px,color:#6ee7b7;

    Req["POST /v1/chat/completions"]:::entry --> S1["1. API Key 认证与鉴权"]:::check
    S1 --> S2["2. 总配额检查 (usedQuota >= totalQuota?)"]:::check

    S2 -->|超出配额| E1["❌ 429 Total Quota Exceeded"]:::reject
    S2 -->|正常| S3["3. 敏感词安全过滤 (Guardrails)"]:::check

    S3 -->|命中违规| E2["❌ 400 阻断"]:::reject
    S3 -->|放行| S4["4. Prompt 精准缓存检索 (Exact Cache)"]:::check

    S4 -->|✅ 命中缓存| E3["⚡ 0 Token 毫秒级流式回放"]:::hit
    S4 -->|未命中缓存| S5["5. TPM 预估 & QPS 速率限制检查"]:::check

    S5 -->|超限| E4["❌ 429 Rate Limit Exceeded"]:::reject
    S5 -->|放行| S6["6. 智能负载均衡 (LB) 与模型推理"]:::check

    S6 --> S7["7. SSE 流式回传 & 真实 Token 审计落盘"]:::check
```

---

## 三、 核心架构能力详解

1. **一 Key 通行全模型**：
   - 业务方只需持有一张 `allowed_models = "*"` 的通用 API Key，即可自由调用全平台任意开源或商用大模型。
2. **Prompt 缓存加速（Exact Cache）**：
   - 针对大量高频重复提问，计算 SHA-256 Hash，命中后由网关直接秒级流式回放，Token 消耗记为 0，极大降低算力成本与延迟。
3. **毫秒级敏感词安全围栏（Guardrails）**：
   - 基于 DFA 状态机算法在进入模型推理前毫秒级扫描敏感违规词，守住企业 AI 合规底线。
4. **多实例加权轮询与熔断器（LB & Failover）**：
   - 支持单模型配置多台推理机 IP，按权重轮询；某台实例异常连续报错 3 次自动熔断拉黑，无缝切流。
5. **真实 TTFT (首字延迟) 精准度量**：
   - 精准捕获大模型第一个 Token 到达时间（TTFT），在流结束时异步完成账单与审计落盘。
