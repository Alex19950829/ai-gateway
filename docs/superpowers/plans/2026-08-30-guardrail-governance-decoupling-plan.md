# 安全合规治理体系解耦与动态策略化改造实施计划 (Implementation Plan)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将数据脱敏（Data Masking）、阿里云绿网 2.0 机审（Aliyun Green）以及 DFA 本地敏感词拦截从 Controller 固定代码中彻底抽离，下沉为基于 Groovy 规则引擎与特征惰性计算的统一动态安全策略流水线。

**Architecture:**
1. 扩展 `RuleDecision` 与 `PolicyPipelineResult`，增加 `REWRITE/MASK` 动态改写决策。
2. 升级 `FactorEngine` 对高开销特征（如 `f_aliyun_green_status`）实现按需惰性计算（Lazy Evaluation）。
3. 在 `RuleExecutorManager` 中预置脱敏、敏感词与绿网 3 大标准安全策略规则。
4. 净化 `GatewayChatController`，移除所有硬编码安全/脱敏 `if-else`，委托 `PolicyPipelineExecutor` 统一编排。

**Tech Stack:** Java 17, Spring Boot 2.7, Spring WebFlux, Groovy 3.0, JUnit 5.

---

## Task 1: 扩展 RuleDecision 与 PolicyPipelineResult 支持改写/脱敏决策

**Files:**
- Modify: `/Users/a58/Downloads/学习/网关/chatling-gateway/chatling-common/src/main/java/com/chatling/common/rule/RuleDecision.java`
- Modify: `/Users/a58/Downloads/学习/网关/chatling-gateway/chatling-common/src/main/java/com/chatling/common/policy/PolicyPipelineResult.java`

- [ ] **Step 1: 在 RuleDecision 中增加 MASK / REWRITE 决策类型与工厂方法**
  - 增加 `action = "MASK"` / `"REWRITE"` 支持。
  - 增加 `rewrittenContent` 字段与 `RuleDecision.mask(String maskedContent, String message)` 方法。

- [ ] **Step 2: 在 PolicyPipelineResult 中增加脱敏改写结果载体**
  - 增加 `modifiedPrompt`、`isModified` 字段与对应的构建器支持。

- [ ] **Step 3: 运行 chatling-common 编译确保类型与接口无误**
  - 执行 `mvn compile` 验证无报错。

---

## Task 2: 升级 FactorEngine 增加惰性求值与内置安全因子提取

**Files:**
- Modify: `/Users/a58/Downloads/学习/网关/chatling-gateway/chatling-core-engine/src/main/java/com/chatling/engine/factor/FactorEngine.java`
- Modify: `/Users/a58/Downloads/学习/网关/chatling-gateway/chatling-core-engine/src/test/java/com/chatling/engine/factor/FactorEngineTest.java`

- [ ] **Step 1: 编写 FactorEngine 惰性计算单元测试**
  - 验证当规则不需要 `f_aliyun_green_status` 时，不触发远程绿网 HTTP 客户端。

- [ ] **Step 2: 在 FactorEngine 中实现对敏感词命中、脱敏结果与绿网状态的按需动态加载**
  - 注册 `f_has_sensitive_word`、`f_sensitive_word`、`f_masked_prompt` 等标准特征。

- [ ] **Step 3: 运行单测验证 FactorEngine 功能正确**

---

## Task 3: 预置脱敏、敏感词与绿网动态规则并增强 PolicyPipelineExecutor

**Files:**
- Modify: `/Users/a58/Downloads/学习/网关/chatling-gateway/chatling-core-engine/src/main/java/com/chatling/engine/rule/RuleExecutorManager.java`
- Modify: `/Users/a58/Downloads/学习/网关/chatling-gateway/chatling-core-engine/src/main/java/com/chatling/engine/policy/PolicyPipelineExecutor.java`
- Test: `/Users/a58/Downloads/学习/网关/chatling-gateway/chatling-core-engine/src/test/java/com/chatling/engine/policy/PolicyPipelineExecutorTest.java`

- [ ] **Step 1: 编写 PolicyPipelineExecutor 完整规则流测试**
  - 覆盖 `REJECT`（400/403/429 阻断）、`FALLBACK`（模型降级）以及 `MASK`（动态 Prompt 脱敏）全场景。

- [ ] **Step 2: 在 RuleExecutorManager 中预置 3 大核心安全规则**
  - `rule_dfa_sensitive_filter`：检查 `f_has_sensitive_word`，违规返回 `RuleDecision.reject(400, "包含合规敏感词")`；
  - `rule_data_masking`：当包含个人隐私时返回 `RuleDecision.mask(f_masked_prompt, "已脱敏")`；
  - `rule_aliyun_green_security`：当绿网机审不通过时返回 `RuleDecision.reject(400, "内容安全机审不通过")`。

- [ ] **Step 3: PolicyPipelineExecutor 中处理 MASK / REWRITE 状态流转**
  - 动态收集改写后的 Prompt，并填充进 `PolicyPipelineResult`。

- [ ] **Step 4: 运行 core-engine 模块单测验证全部通过**

---

## Task 4: 净化 GatewayChatController，彻底解耦固定代码

**Files:**
- Modify: `/Users/a58/Downloads/学习/网关/chatling-gateway/chatling-dataplane/src/main/java/com/chatling/gateway/controller/GatewayChatController.java`
- Test: `/Users/a58/Downloads/学习/网关/chatling-gateway/chatling-dataplane/src/test/java/com/chatling/gateway/GatewayFeaturesTest.java`

- [ ] **Step 1: 移除 Controller 中的硬编码调用**
  - 彻底删除 `aliyunGreenSecurityService.checkContent(promptText)` 无差别同步调用；
  - 彻底删除硬编码的 `dataMaskingGovernor.mask(promptText, "MASK")` 逻辑；
  - 彻底删除硬编码的 `checkRequestSensitive(request)` 逻辑。

- [ ] **Step 2: 接入 PolicyPipelineExecutor 统一处理结果**
  - 若 `pipelineResult.isRejected()` ➔ 直接返回拒绝状态码与提示；
  - 若 `pipelineResult.isMasked()` ➔ 自动将改写后的 `pipelineResult.getModifiedPrompt()` 同步更新到 `request.getMessages()` 中；
  - 若 `pipelineResult.isFallback()` ➔ 自动切换目标模型。

- [ ] **Step 3: 运行 dataplane 模块全量集成测试，验证网关全链路正常**

---

## Task 5: 验证与端到端回归

- [ ] **Step 1: 执行全局 Maven 单测打包验证**
  - `mvn clean test` 确保各模块全部 PASS。
- [ ] **Step 2: 验证普通无绿网规则的模型调用**
  - 验证无绿网规则的模型首字耗时显著降低（不再发生多余的 100ms 远程调用）。
