# chatling-core-engine 真实大模型调用与 API Key 动态注入实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 彻底淘汰本地伪代码/仅Mock机制，为 `chatling-core-engine` 构建生产级的高性能真实 HTTP 客户端，支持 DeepSeek 官方 API、阿里云百炼 (DashScope) 与本地 Ollama 真实大模型调用，并实现上游厂商 API Key 安全动态注入与下游业务 Key 分配鉴权。

**Architecture:** 基于 Spring WebFlux + Reactor Netty 真实连接池构建非阻塞 SSE 流式通道。网关接收到请求后，自动提取目标上游模型的真实 `api_secret`，注入到外发 HTTP 请求头中发起真实大模型推理，流式解析标准 OpenAI SSE 数据帧并透传前端。

**Tech Stack:** Java 17 · Spring WebFlux (Reactor Netty) · Fastjson2 · H2 Database · Tailwind CSS

---

## 全局约束与规范 (Global Constraints)
1. **纯真实网络调用**：除明确标记为 `mock` 的测试模型外，所有配置了 `baseUrl` 和 `api_secret` 的模型一律发起真实 HTTP POST 请求。
2. **安全动态注入**：业务请求仅携带网关自身的 `sk-chatling-xxx` 凭据，底层厂商 API Key（如 DeepSeek `sk-xxxx`）由网关在转发时自动注入，对外部业务透明。
3. **真实 SSE 规范解析**：支持逐行解析 `data: {...}`，精准提取 `delta.content`，并在收到 `[DONE]` 时优雅结束。

---

## 任务拆解与执行步骤 (Tasks Breakdown)

### Task 1: 真实 WebClient 响应式网络引擎与连接池调优

**Files:**
- Modify: `chatling-core-engine/src/main/java/com/chatling/engine/adapter/OpenAiCompatibleAdapter.java`
- Modify: `chatling-bootstrap/src/main/java/com/chatling/bootstrap/config/CorsConfig.java`

- [ ] **Step 1: 配置高并发 Reactor Netty ConnectionProvider 连接池**
  配置 500 最大连接数、TCP Keep-Alive、30 秒空闲超时，支持长连接高复用。

- [ ] **Step 2: 完善 OpenAiCompatibleAdapter 真实调用与请求头注入**
  - 自动从 `ModelConfig.getApiSecret()` 提取密钥并拼接 `Authorization: Bearer <Secret>`；
  - 确保向目标 `config.getBaseUrl() + "/chat/completions"` 发起真实非阻塞 POST；
  - 正确解析 HTTP 401/429/500 错误并抛出结构化异常触发 Fallback。

---

### Task 2: 百度文心千帆 (Baidu Qianfan) 原生协议适配器（可选扩展）

**Files:**
- Create: `chatling-core-engine/src/main/java/com/chatling/engine/adapter/BaiduQianfanAdapter.java`
- Modify: `chatling-core-engine/src/main/java/com/chatling/engine/service/ModelEngineService.java`

- [ ] **Step 1: 实现千帆 Token 换取与原生流式响应解析**
  支持通过 `client_id` + `client_secret` 动态换取百度 `access_token` 并完成流式调用。

---

### Task 3: 前端模型池支持真实 API Key 配置与一键“连通性测试”

**Files:**
- Modify: `chatling-admin/src/main/java/com/chatling/admin/controller/AdminApiController.java`
- Modify: `chatling-bootstrap/src/main/resources/static/index.html`

- [ ] **Step 1: 增加上游连通性测试端点 `POST /api/admin/models/test-connection`**
  接收前端填写的 BaseURL 与 API Key，向真实大模型发送一条打招呼测试请求，返回是否连通与耗时。

- [ ] **Step 2: 在 Web 界面增加“新增/编辑模型弹窗”与“测试连接”按钮**
  支持用户在网页端直接输入 DeepSeek / 通义千问的真实 Key 并一键测试连通性，保存入库。

---

### Task 4: 完整端到端真实大模型流式调用与验收

**Files:**
- Test: `chatling-core-engine/src/test/java/com/chatling/engine/RealLlmCallTest.java`

- [ ] **Step 1: 编写自动化测试验证真实 HTTP 调用（包括真实连接建立、超时控制、SSE 分块）**
- [ ] **Step 2: 启动项目，在模型体验广场输入真实问题，验证打字机真实大模型生成效果**
