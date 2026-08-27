# chatling-dataplane 网关核心能力实施计划 (Implementation Plan)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 `chatling-dataplane` 补齐生产级 AI 网关三大硬核能力：Prompt 精准哈希缓存加速、多节点负载均衡与熔断降级状态机、敏感词安全合规拦截过滤器，并完善流式 SSE 计量闭环。

**Architecture:** 基于 WebFlux 响应式流水线（Filter Chain），在请求打到底层大模型前依次执行：`API Key 鉴权` $\rightarrow$ `敏感词合规安全拦截` $\rightarrow$ `Prompt MD5 缓存比对` $\rightarrow$ `TPM/QPS 令牌桶限流` $\rightarrow$ `多实例负载均衡与熔断路由` $\rightarrow$ `Engine 推理` $\rightarrow$ `SSE 流式计量与 TTFT 审计落盘`。

**Tech Stack:** Java 17 · Spring WebFlux · Caffeine Cache · Fastjson2 · H2 Database

---

## 全局约束与规范 (Global Constraints)
1. **零外部网络阻断**：所有缓存和敏感词检测均在内存毫秒级（< 5ms）完成，不增加系统延迟。
2. **缓存命中即时打字**：命中 Prompt 缓存时，网关直接构造 SSE 流式打字帧返回，Token 消耗记为 0，TTFT 降至 20ms。
3. **标准错误输出**：敏感词拦截、限流拦截均统一输出 OpenAI 兼容的错误 JSON 格式。

---

## 任务拆解与执行步骤 (Tasks Breakdown)

### Task 1: 敏感词与合规安全过滤器 (Content Guardrail Filter)

**Files:**
- Create: `chatling-dataplane/src/main/java/com/chatling/gateway/filter/ContentGuardrailFilter.java`
- Modify: `chatling-dataplane/src/main/java/com/chatling/gateway/controller/GatewayChatController.java`

- [ ] **Step 1: 编写敏感词前缀树（Trie / DFA）快速匹配算法**
  支持对 Prompt 输入内容进行毫秒级敏感违规词检测。

- [ ] **Step 2: 在网关入口集成合规检查**
  若检测到违规词汇，立即中断请求并返回标准 OpenAI 格式的 `400 Bad Request`（如 `{"error": {"message": "请求内容包含合规敏感词，已被网关拦截", "type": "content_policy_violation"}}`）。

---

### Task 2: Prompt 精准哈希缓存加速器 (Exact Prompt Cache)

**Files:**
- Create: `chatling-dataplane/src/main/java/com/chatling/gateway/cache/PromptCacheService.java`
- Modify: `chatling-dataplane/src/main/java/com/chatling/gateway/controller/GatewayChatController.java`

- [ ] **Step 1: 构建基于 Caffeine 的 Prompt MD5 响应缓存**
  对 `model + messages` 计算 SHA-256/MD5 Hash，缓存完整回复内容与 Token 数。

- [ ] **Step 2: 实现缓存命中时的流式打字回放器**
  命中缓存时，绕过大模型调用，直接由网关快速切词输出 SSE 流，Token 消耗记为 0，首字延迟（TTFT）降至 10~20ms。

---

### Task 3: 多实例负载均衡与熔断状态机 (Cluster Load Balancing & Circuit Breaker)

**Files:**
- Create: `chatling-dataplane/src/main/java/com/chatling/gateway/lb/ModelLoadBalancer.java`
- Create: `chatling-dataplane/src/main/java/com/chatling/gateway/lb/CircuitBreaker.java`
- Modify: `chatling-dataplane/src/main/java/com/chatling/gateway/controller/GatewayChatController.java`

- [ ] **Step 1: 实现加权轮询负载均衡 (Weighted Round-Robin)**
  支持同一个模型标识下配置多个 BaseURL 实例（以逗号分隔，如 `http://node1:8000,http://node2:8000`），按权重轮询分发。

- [ ] **Step 2: 实现简易熔断器（关闭-开启-半开）**
  当某节点连续报错 3 次时触发熔断拉黑 30 秒，自动路由至健康节点；全挂时透明切换到 Fallback 模型。

---

### Task 4: 单元测试与端到端全链路验收

**Files:**
- Create: `chatling-dataplane/src/test/java/com/chatling/gateway/GatewayFullFeaturesTest.java`

- [ ] **Step 1: 编写测试用例验证敏感词拦截、Prompt 缓存命中加速与加权轮询负载均衡**
- [ ] **Step 2: 在前端控制台看板中确认缓存命中与审计日志记录无误**
