# 40+ Agent 矩阵多租户配额隔离与上游多 Key 资源池容灾实施计划 (Implementation Plan)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 构建企业级大模型网关的双层治理体系——租户-模型专属 QPM/TPM 配额硬隔离（防止 40+ Agent 相互挤兑）与上游官方多 Key 聚合资源池（平滑加权轮询分摊并发 + 429 自动冷却 60s 透明重试）。

**Architecture:** 
- 第一层基于 `t_api_key_model_quota` 实现 `(api_key, model_name)` 粒度的双重阶梯令牌桶，隔离定时任务与在线 Agent；
- 第二层基于 `t_model_key_pool` 与 `DynamicKeyPoolManager` 实现 Nginx 同款平滑加权轮询（Smooth Weighted Round-Robin），并在 WebFlux 响应式流遇到 429 时将故障 Key 标记冷却 60 秒并自动换 Key 重试；
- 联动 `AdminService` 实现申请工单审批通过后原子写入配额，并支持两阶段 Token 预扣与结算。

**Tech Stack:** Java 17, Spring Boot 2.7, Spring WebFlux, Reactor Core, Caffeine Cache / In-Memory Token Bucket, H2 Database / Spring JDBC, FastJSON2, JUnit 5.

**Spec:** [`docs/superpowers/specs/2026-09-01-multi-agent-quota-and-key-pool-governance-design.md`](file:///Users/a58/Downloads/学习/网关/chatling-gateway/docs/superpowers/specs/2026-09-01-multi-agent-quota-and-key-pool-governance-design.md)

## Global Constraints
- 保证 100% 兼容 OpenAI Chat Completions API 标准格式与流式 SSE 契约；
- 所有响应式流式 WebFlux 管道必须具备非阻塞（Non-blocking）与背压安全保障；
- 429 故障转移必须对下游 OpenClaw / Agent 完全透明无感知；
- 多模块 Maven 构建（`mvn clean test`）必须全绿通过。

---

### Task 1: 实体与数据库 Schema 扩展 (Data Models & DDL Migration)

**Files:**
- Create: `chatling-common/src/main/java/com/chatling/common/model/ModelKeyPool.java`
- Create: `chatling-common/src/main/java/com/chatling/common/model/ApiKeyModelQuota.java`
- Modify: `chatling-common/src/main/java/com/chatling/common/model/ModelApply.java`
- Modify: `chatling-bootstrap/src/main/resources/schema.sql`
- Modify: `chatling-admin/src/test/resources/schema-test.sql`

**Interfaces:**
- Produces: 
  - `ModelKeyPool(id, modelName, apiKey, weight, qpmLimit, tpmLimit, status, cooldownUntil, description)`
  - `ApiKeyModelQuota(id, apiKey, modelName, allocatedQpm, allocatedTpm, status)`
  - `ModelApply` (added `requestedQpm`, `allocatedQpm`, `reviewerName`, `reviewComment`)

- [ ] **Step 1: 创建 `ModelKeyPool` 实体类**

```java
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
public class ModelKeyPool {
    private Long id;
    private String modelName;
    private String apiKey;
    private Integer weight;
    private Integer qpmLimit;
    private Integer tpmLimit;
    private Integer status; // 1: NORMAL/HEALTHY, 0: DISABLED, 2: COOLDOWN
    private Long cooldownUntil; // timestamp in millis
    private String description;
    private Date createdTime;
    private Date updatedTime;
}
```

- [ ] **Step 2: 创建 `ApiKeyModelQuota` 实体类**

```java
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
public class ApiKeyModelQuota {
    private Long id;
    private String apiKey;
    private String modelName;
    private Integer allocatedQpm;
    private Integer allocatedTpm;
    private Integer status; // 1: ACTIVE, 0: FROZEN
    private Date createdTime;
    private Date updatedTime;
}
```

- [ ] **Step 3: 扩展 `ModelApply` 实体并更新 `schema.sql` 和 `schema-test.sql`**
在 `ModelApply.java` 中增加 `requestedQpm`, `allocatedQpm`, `reviewerName`, `reviewComment` 字段；
在 `schema.sql` 和 `schema-test.sql` 中新增 `t_model_key_pool` 表、`t_api_key_model_quota` 表，并在 `t_model_apply` 补充字段。

- [ ] **Step 4: 运行 Maven 编译测试验证**
Run: `export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home && mvn clean test-compile`
Expected: BUILD SUCCESS

---

### Task 2: 平滑加权轮询器与动态 Key 资源池管理器 (Smooth Weighted Round-Robin & Key Pool Manager)

**Files:**
- Create: `chatling-core-engine/src/main/java/com/chatling/engine/pool/SmoothWeightedRoundRobinSelector.java`
- Create: `chatling-core-engine/src/main/java/com/chatling/engine/pool/DynamicKeyPoolManager.java`
- Test: `chatling-core-engine/src/test/java/com/chatling/engine/pool/SmoothWeightedRoundRobinTest.java`

**Interfaces:**
- Produces:
  - `SmoothWeightedRoundRobinSelector.selectKey(List<ModelKeyPool> candidates)` -> `ModelKeyPool`
  - `DynamicKeyPoolManager.acquireKey(String modelName)` -> `ModelKeyPool`
  - `DynamicKeyPoolManager.markCooldown(String modelName, String apiKey, long cooldownDurationMs)`
  - `DynamicKeyPoolManager.reloadPools()`

- [ ] **Step 1: 编写加权轮询算法单测 `SmoothWeightedRoundRobinTest`**

```java
package com.chatling.engine.pool;

import com.chatling.common.model.ModelKeyPool;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

public class SmoothWeightedRoundRobinTest {
    @Test
    void testWeightedDistribution() {
        SmoothWeightedRoundRobinSelector selector = new SmoothWeightedRoundRobinSelector();
        ModelKeyPool k1 = ModelKeyPool.builder().id(1L).apiKey("key-a").weight(5).status(1).build();
        ModelKeyPool k2 = ModelKeyPool.builder().id(2L).apiKey("key-b").weight(1).status(1).build();

        Map<String, Integer> counts = new HashMap<>();
        for (int i = 0; i < 60; i++) {
            ModelKeyPool picked = selector.selectKey(Arrays.asList(k1, k2));
            assertNotNull(picked);
            counts.put(picked.getApiKey(), counts.getOrDefault(picked.getApiKey(), 0) + 1);
        }
        assertEquals(50, counts.get("key-a"));
        assertEquals(10, counts.get("key-b"));
    }
}
```

- [ ] **Step 2: 实现 `SmoothWeightedRoundRobinSelector` 与 `DynamicKeyPoolManager`**
实现基于 Nginx 原理的 `SmoothWeightedRoundRobinSelector`，并在 `DynamicKeyPoolManager` 中管理从 H2 数据库加载的 Key 列表，支持基于 `cooldownUntil` 的健康检查与自动冷却恢复。

- [ ] **Step 3: 运行单测验证**
Run: `export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home && mvn test -pl chatling-core-engine -Dtest=SmoothWeightedRoundRobinTest`
Expected: PASS

---

### Task 3: 上游适配器集成多 Key 调度与 429 透明容灾 (Upstream Adapter Failover)

**Files:**
- Modify: `chatling-core-engine/src/main/java/com/chatling/engine/adapter/OpenAiCompatibleAdapter.java`
- Modify: `chatling-core-engine/src/main/java/com/chatling/engine/policy/ModelEngineService.java`

**Interfaces:**
- Consumes: `DynamicKeyPoolManager.acquireKey(modelName)`, `DynamicKeyPoolManager.markCooldown(modelName, key, 60000)`
- Produces: `streamChat(config, request)` with transparent multi-key retry on 429/503.

- [ ] **Step 1: 在 `OpenAiCompatibleAdapter` 中动态注入池化 Key**
在发起 WebClient 请求前，通过 `dynamicKeyPoolManager.acquireKey(config.getModelName())` 获取当前最佳健康 Key；
若池中有可用 Key，则优先使用池中 Key 覆盖 `config.getApiSecret()`。

- [ ] **Step 2: 注入 429 智能捕获与透明换 Key 重试流**
当 WebClient 接收到 429 HTTP 状态码时：
1. 立即调用 `dynamicKeyPoolManager.markCooldown(modelName, currentKey, 60000)` 将故障 Key 降级冷却 60 秒；
2. 响应式流水线通过 `retryWhen` 或 `onErrorResume` 从池中重新获取健康 Key 进行透明二次重试。

- [ ] **Step 3: 编译并验证 engine 模块**
Run: `export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home && mvn test -pl chatling-core-engine`
Expected: BUILD SUCCESS

---

### Task 4: 租户-模型双重限流与两阶段 Token 预扣 (Dual Rate Limiting & Two-Phase Settlement)

**Files:**
- Modify: `chatling-core-engine/src/main/java/com/chatling/engine/ratelimit/RateLimiterService.java`
- Modify: `chatling-dataplane/src/main/java/com/chatling/gateway/controller/GatewayChatController.java`
- Modify: `chatling-dataplane/src/main/java/com/chatling/gateway/service/GatewayService.java`
- Test: `chatling-dataplane/src/test/java/com/chatling/gateway/GatewayModelQuotaRateLimitTest.java`

**Interfaces:**
- Produces:
  - `RateLimiterService.tryAcquireModelQuota(apiKey, modelName, estimatedTokens)` -> `boolean`
  - `RateLimiterService.settleTokens(apiKey, modelName, deltaTokens)`

- [ ] **Step 1: 编写租户-模型专属配额隔离单测**
编写测试用例验证：API Key A 申请了 2 QPM 的 `deepseek-chat`，连续打 3 次请求时第 3 次被拦截返回 429，而调用未限制的 `ark-code-latest` 依旧畅通。

- [ ] **Step 2: 实现 `RateLimiterService` 细粒度模型令牌桶**
在 `RateLimiterService` 中引入 `ConcurrentHashMap<"apiKey:modelName", TokenBucket>`，优先读取 `t_api_key_model_quota` 中的 `allocated_qpm` 与 `allocated_tpm`；未单独配置时回退到 Key 的全局限流。

- [ ] **Step 3: 在 `GatewayChatController` 中集成两阶段 Token 预扣与事后精确结算**
在请求入口处基于 Prompt Tokens + 预估输出预扣令牌；在 SSE 流式结束的 `doFinally` 阶段计算真实差额并还回令牌桶。

- [ ] **Step 4: 运行 dataplane 模块单测**
Run: `export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home && mvn test -pl chatling-dataplane`
Expected: BUILD SUCCESS

---

### Task 5: 权限审批与配额自动化发放闭环 (Admin Approval & Quota Automation)

**Files:**
- Modify: `chatling-admin/src/main/java/com/chatling/admin/service/AdminService.java`
- Modify: `chatling-admin/src/main/java/com/chatling/admin/controller/AdminApiController.java`
- Modify: `chatling-admin/src/test/java/com/chatling/admin/ModelApplyWorkflowTest.java`
- Modify: `chatling-bootstrap/src/main/resources/static/index.html`

**Interfaces:**
- Produces:
  - `POST /api/admin/apply/submit` 支持提交 `requestedQpm`
  - `POST /api/admin/apply/approve` 审批通过时原子向 `t_api_key_model_quota` 写入 `allocatedQpm`

- [ ] **Step 1: 扩展 `AdminService.approveApply()` 写入配额**
在管理员点击审批通过时，除了向 `t_api_key.allowed_models` 追加模型白名单，同时在 `t_api_key_model_quota` 表中执行 `MERGE INTO` 写入对应的 `allocated_qpm`（取工单的 `requested_qpm`，默认 30）。

- [ ] **Step 2: 更新前端 UI 申请弹窗与审批看板**
在 `index.html` 的申请模型权限弹窗中，增加「申请期望 QPM」输入项；在审批中心表格中展示申请的 QPM 额度。

- [ ] **Step 3: 运行 admin 模块单测**
Run: `export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home && mvn test -pl chatling-admin`
Expected: BUILD SUCCESS

---

### Task 6: 密钥文件装载器与端到端全链路集成验证 (Secret Loader & E2E Verification)

**Files:**
- Modify: `chatling-bootstrap/src/main/java/com/chatling/bootstrap/config/LocalDatabaseSecretLoader.java`
- Modify: `data/secrets.properties`
- Modify: `chatling-bootstrap/src/main/resources/schema.sql`

- [ ] **Step 1: 扩展 `LocalDatabaseSecretLoader` 支持多 Key 自动注入**
支持在 `data/secrets.properties` 中为同一个模型配置多个 Key（如 `deepseek-chat.1=xxx`, `deepseek-chat.2=yyy`），并在网关启动时自动插入到 `t_model_key_pool` 中。

- [ ] **Step 2: 预置管理员 Key 专属全量模型配额**
在 `schema.sql` 中为 `sk-chatling-admin-demo888` 预置 7 大模型的超大 QPM 配额（默认 200 QPM），开箱即用。

- [ ] **Step 3: 执行全模块 Maven 构建与回归测试**
Run: `export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home && mvn clean test`
Expected: 6 个模块全部 `BUILD SUCCESS`，所有单元测试与集成测试 100% 通过。
