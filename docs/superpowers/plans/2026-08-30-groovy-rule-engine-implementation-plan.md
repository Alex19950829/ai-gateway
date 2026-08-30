# Chatling AI 网关 · 特征变量、Groovy 规则与模型策略引擎实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 构建对标 58 集团风控体系的「特征变量 (Factors) ➔ Groovy 动态规则 (Rules) ➔ 模型防护策略 (Policies)」三层可插拔引擎，并在 `chatling-gateway` 数据面实现基于模型维度的动态限流、安全拦截与 Fallback 容灾。

**Architecture:** 
1. **特征层**：支持字段特征（请求头/Body）、聚合特征（包含 5 大通用 Java 聚合实现类：滑动窗口 QPM、滑动窗口 TPM、原子计数++、Token 预算扣减、IP 去重统计）与名单特征，提供 `IFactorAggregator` 标准实现；
2. **规则层**：复用 58 风控成熟模式，基于 `GroovyClassLoader` + `Caffeine Cache` 实现 1ms 在线热加载与沙箱隔离执行；
3. **策略层**：以目标模型为锚点，绑定多条规则，支持按优先级（Priority）降序流水线调度、短路阻断（自定义 HTTP 状态码与文案）与自动降级。

**Tech Stack:** Java 17, Spring Boot, Spring WebFlux, Groovy 3.x, Caffeine, Redis, H2/MySQL.

**Spec:** [`docs/superpowers/specs/2026-08-30-groovy-rule-engine-design.md`](file:///Users/a58/Downloads/学习/网关/chatling-gateway/docs/superpowers/specs/2026-08-30-groovy-rule-engine-design.md)

---

## Task 1: 特征变量与 5 大 Java 聚合实现类引擎 (Feature Factor Domain & 5 Aggregators)

**Files:**
- Create: `chatling-common/src/main/java/com/chatling/common/factor/FactorType.java`
- Create: `chatling-common/src/main/java/com/chatling/common/factor/IFactorExtractor.java`
- Create: `chatling-common/src/main/java/com/chatling/common/factor/IFactorAggregator.java`
- Create: `chatling-core-engine/src/main/java/com/chatling/engine/factor/impl/SlidingWindowCountAggregator.java` (通用-滑动窗口请求频次统计 QPM)
- Create: `chatling-core-engine/src/main/java/com/chatling/engine/factor/impl/SlidingWindowTokenAggregator.java` (通用-滑动窗口 Token 消耗统计 TPM)
- Create: `chatling-core-engine/src/main/java/com/chatling/engine/factor/impl/AtomicCounterAggregator.java` (通用-原子计数器 Count++)
- Create: `chatling-core-engine/src/main/java/com/chatling/engine/factor/impl/TokenQuotaDecrementAggregator.java` (通用-Token 预算扣减与余额查询 ai-quota)
- Create: `chatling-core-engine/src/main/java/com/chatling/engine/factor/impl/DistinctIpAggregator.java` (通用-指定时间段关联 IP 去重统计)
- Create: `chatling-core-engine/src/main/java/com/chatling/engine/factor/FactorEngine.java`
- Test: `chatling-core-engine/src/test/java/com/chatling/engine/factor/FactorEngineTest.java`

- [ ] **Step 1: 编写 FactorEngineTest 单元测试** (覆盖 5 大聚合实现类的提取与后置异步回写)
- [ ] **Step 2: 运行测试并确认失败**
- [ ] **Step 3: 实现特征类型枚举与 5 大聚合实现类**
  1. `SlidingWindowCountAggregator` (基于 Redis 滑动窗口统计 60s 内 QPM)
  2. `SlidingWindowTokenAggregator` (基于 Redis 滑动窗口统计 60s 内 TPM)
  3. `AtomicCounterAggregator` (基于 Redis INCR 实现原子调用次数计数)
  4. `TokenQuotaDecrementAggregator` (基于 Redis DECRBY 实现 Token 余额扣减与配额硬拦截)
  5. `DistinctIpAggregator` (基于 Redis HyperLogLog 统计 API Key 关联 IP 去重数)
- [ ] **Step 4: 实现 FactorEngine (根据规则绑定的特征列表提取 factor 数据字典)**
- [ ] **Step 5: 运行测试并确认通过**

---

## Task 2: Groovy 规则动态编译与 Caffeine 缓存池 (Groovy Rule Execution Engine)

**Files:**
- Create: `chatling-common/src/main/java/com/chatling/common/rule/RuleDecision.java`
- Create: `chatling-common/src/main/java/com/chatling/common/rule/BaseRuleExecutor.java`
- Create: `chatling-core-engine/src/main/java/com/chatling/engine/rule/RuleExecutorManager.java`
- Test: `chatling-core-engine/src/test/java/com/chatling/engine/rule/RuleExecutorManagerTest.java`

- [ ] **Step 1: 编写 RuleExecutorManagerTest 单元测试** (测试在线编译 Groovy 脚本、实例缓存、执行 executeRule 并断言 RuleDecision)
- [ ] **Step 2: 运行测试并确认失败**
- [ ] **Step 3: 引入 Groovy 依赖，实现 BaseRuleExecutor 与 RuleDecision (PASS, REJECT, FALLBACK)**
- [ ] **Step 4: 实现 RuleExecutorManager (基于 GroovyClassLoader + Caffeine Cache，支持脚本 MD5 缓存)**
- [ ] **Step 5: 运行测试并确认通过**

---

## Task 3: 模型专属策略编排引擎与流水线 (Model Policy Orchestrator & Pipeline)

**Files:**
- Create: `chatling-common/src/main/java/com/chatling/common/policy/ModelPolicyRule.java`
- Create: `chatling-core-engine/src/main/java/com/chatling/engine/policy/ModelPolicyManager.java`
- Create: `chatling-core-engine/src/main/java/com/chatling/engine/policy/PolicyPipelineExecutor.java`
- Test: `chatling-core-engine/src/test/java/com/chatling/engine/policy/PolicyPipelineExecutorTest.java`

- [ ] **Step 1: 编写 PolicyPipelineExecutorTest 单元测试** (模拟多规则优先级执行、短路阻断与自定义错误码)
- [ ] **Step 2: 运行测试并确认失败**
- [ ] **Step 3: 实现 ModelPolicyManager (管理模型与规则流水线的映射)**
- [ ] **Step 4: 实现 PolicyPipelineExecutor (按优先级降序调度规则，处理 Reject 短路和 Fallback 标记)**
- [ ] **Step 5: 运行测试并确认通过**

---

## Task 4: 数据面集成与 Web 控制台 REST 接口 (DataPlane Filter & Admin APIs)

**Files:**
- Create: `chatling-dataplane/src/main/java/com/chatling/gateway/filter/ModelPolicyGatewayFilter.java`
- Create: `chatling-admin/src/main/java/com/chatling/admin/controller/FactorAdminController.java`
- Create: `chatling-admin/src/main/java/com/chatling/admin/controller/RuleAdminController.java`
- Create: `chatling-admin/src/main/java/com/chatling/admin/controller/ModelPolicyAdminController.java`
- Test: `chatling-dataplane/src/test/java/com/chatling/gateway/ModelPolicyIntegrationTest.java`

- [ ] **Step 1: 编写集成测试 ModelPolicyIntegrationTest** (模拟发起 HTTP 请求，验证特定模型的 QPM 超额返回 429 及自定义文案)
- [ ] **Step 2: 运行测试并确认失败**
- [ ] **Step 3: 将 ModelPolicyGatewayFilter 挂载进 WebFlux 数据面请求流水线**
- [ ] **Step 4: 实现特征变量、Groovy 规则与模型策略的 CRUD REST API 控制器**
- [ ] **Step 5: 运行测试并确认全部通过**

---

## Task 5: 前端 Web 控制台双向联调与端到端验证 (E2E Verification)

**Files:**
- Modify: `chatling-bootstrap/src/main/resources/static/index.html`
- Test: 端到端浏览器验证与 cURL 请求测试

- [ ] **Step 1: 联调前端特征管理、规则编辑与模型策略配置 API**
- [ ] **Step 2: 在控制台为 deepseek-chat 配置一条 QPM=1 的限流规则**
- [ ] **Step 3: 发起连续 2 次 cURL 请求，验证第 1 次成功，第 2 次精准被 Groovy 规则拦截并返回 429**
