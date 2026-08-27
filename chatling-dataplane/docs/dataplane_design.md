# chatling-dataplane 模块设计文档 (Architecture & Design)

## 一、 模块定位与职责
`chatling-dataplane` 是灵犀 AI 网关的**高并发流量代理与数据面枢纽**，负责对外暴露统一标准的 `/v1/chat/completions` 与 `/v1/models` 接口。

---

## 二、 网关完整处理流水线 (Gateway Full Pipeline)

```mermaid
flowchart TD
    Client["客户端发起请求 (POST /v1/chat/completions)"] --> Pre1["1. API Key 鉴权与身份识别 (sk-chatling-xxx)"]
    Pre1 --> Pre2["2. 敏感词与合规安全检测 (Content Guardrail)"]
    
    Pre2 -->|违规敏感词| Block["❌ 立即 400 阻断并记录违规日志"]
    Pre2 -->|合规放行| Pre3["3. Prompt 精准哈希缓存检索 (Exact Cache)"]
    
    Pre3 -->|✅ 命中缓存| QuickStream["⚡ 0 Token 毫秒级流式打字回放 (TTFT < 20ms)"]
    QuickStream --> AuditDone["记录 0 Token 审计流水 -> 返回客户端"]
    
    Pre3 -->|未命中缓存| Pre4["4. TPM (Tokens/分) & QPS 令牌桶限流检查"]
    Pre4 -->|超额拦截| TooManyReq["❌ 429 Rate Limit Exceeded"]
    
    Pre4 -->|放行| Route["5. 智能负载均衡 (LB) 与熔断健康检查 (Circuit Breaker)"]
    Route --> Engine["6. 调度 chatling-core-engine 发起真实大模型推理"]
    Engine --> SSE["7. SSE 响应式流式打字透传客户端"]
    SSE --> Audit["8. 流结束记录 TTFT 首字延迟、真实Token消耗并落库"]
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
