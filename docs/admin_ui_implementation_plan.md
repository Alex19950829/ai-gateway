# chatling-admin 与 UI 页面实施计划 (Implementation Plan)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 完善并联调 `chatling-admin` 管理层 RESTful 接口与 `chatling-bootstrap` 内部的前端 SPA（单页应用），实现 API Key 治理、模型池配置、用量大屏监控、模型体验广场（`/textModel`）流式打字与 Prompt 实验室（`/experience`）调试五大业务闭环。

**Architecture:** 前后端通过标准 JSON RESTful API (`/api/admin/*`) 及 OpenAI 兼容流式接口 (`/v1/chat/completions`) 通信。后端采用 Spring WebFlux 响应式非阻塞架构，前端采用现代化响应式 Web 单页应用，直观渲染 KPI 大屏、管理表格、SSE 实时打字机与参数调试面板。

**Tech Stack:** Java 17 · Spring Boot 2.7.x / 3.x · Spring WebFlux · H2 / JDBC · TailwindCSS · Native Server-Sent Events (SSE)

**Spec:** [`chatling-admin/README.md`](file:///Users/a58/Downloads/学习/网关/chatling-gateway/chatling-admin/README.md) 与 [`lingxi_platform_design_spec.md`](file:///Users/a58/.gemini/antigravity/brain/56cbd459-d966-4915-bdbb-119ab95ad652/lingxi_platform_design_spec.md)

---

## 全局约束与规范 (Global Constraints)
1. **接口命名规范**：管理控制台 API 统一前缀为 `/api/admin/*`；大模型对外及体验接口统一为 `/v1/chat/completions` 与 `/v1/models`。
2. **统一响应格式**：所有管理接口统一返回 `CommonResult<T>` (`code: 0, message: "success", data: T`)。
3. **零外部强依赖**：使用内嵌 H2 数据库和内存令牌桶，无需额外安装 Node.js 或配置 MySQL/Redis 即可直接运行。

---

## 任务拆解与执行步骤 (Tasks Breakdown)

### Task 1: API Key 凭证管理前后端闭环 (Key Management)

**Files:**
- Modify: `chatling-admin/src/main/java/com/chatling/admin/controller/AdminApiController.java`
- Modify: `chatling-dataplane/src/main/java/com/chatling/gateway/repository/ChatlingDao.java`
- Modify: `chatling-bootstrap/src/main/resources/static/index.html`

- [ ] **Step 1: 编写 API Key 单元测试验证 CRUD 逻辑**
  验证生成 Key、根据 ID 删除 Key、修改 Key 启用/禁用状态的原子性。

- [ ] **Step 2: 完善 API Key 管理端点 (`/api/admin/apikeys`)**
  - `GET /api/admin/apikeys`：获取全量 Key 列表及配额使用进度。
  - `POST /api/admin/apikeys`：生成前缀为 `sk-chatling-` 的 24 位随机高强度 Key，并初始化 TPM/QPS 限额。
  - `PUT /api/admin/apikeys/{apiKey}/status`：快速切换启停状态（1 启用，0 禁用）。
  - `DELETE /api/admin/apikeys/{id}`：安全删除指定 Key。

- [ ] **Step 3: 前端 UI 联调与交互增强**
  - 列表支持状态徽章（绿色“启用”、红色“已禁用”）；
  - 增加一键复制 Key 剪贴板功能；
  - 弹窗支持自定义模型白名单（逗号分隔或 `*` 通配符）与 TPM 限额输入。

---

### Task 2: 监控大屏与用量审计流水前后端闭环 (Dashboard & Analytics)

**Files:**
- Modify: `chatling-admin/src/main/java/com/chatling/admin/controller/AdminApiController.java`
- Modify: `chatling-dataplane/src/main/java/com/chatling/gateway/repository/ChatlingDao.java`
- Modify: `chatling-bootstrap/src/main/resources/static/index.html`

- [ ] **Step 1: 完善大屏数据聚合查询接口 (`/api/admin/dashboard/*`)**
  - `GET /api/admin/dashboard/stats`：实时聚合全平台 `totalTokens`、`totalRequests`、`avgTtftMs`、`activeKeys`。
  - `GET /api/admin/dashboard/audits`：获取最近 50 条调用流水（包含请求 ID、调用人、模型、TTFT、总耗时、Token 拆解）。
  - `GET /api/admin/dashboard/usage`：按日期获取每日聚合 Token 趋势数据。

- [ ] **Step 2: 前端 UI 看板卡片与流水表渲染**
  - 顶部 4 张核心 KPI 卡片数字自动千分位格式化；
  - 审计流水表格支持 TTFT 毫秒数高亮、模型名称徽章、响应状态码彩色提示；
  - 增加“一键刷新”数据功能。

---

### Task 3: 模型体验广场 (`/textModel`) 流式打字闭环 (Playground)

**Files:**
- Modify: `chatling-dataplane/src/main/java/com/chatling/gateway/controller/GatewayChatController.java`
- Modify: `chatling-bootstrap/src/main/resources/static/index.html`

- [ ] **Step 1: 确保 `/v1/chat/completions` SSE 流式返回协议规范**
  返回 `Content-Type: text/event-stream`，数据块为 `data: {"choices":[{"delta":{"content":"..."}}]}\n\n`，以 `data: [DONE]\n\n` 结尾。

- [ ] **Step 2: 前端 SSE 实时打字机渲染器对接**
  - 使用原生 `fetch` + `ReadableStreamDefaultReader` 逐块解析 SSE Chunk；
  - 实现字符渐显打字机动画与自动平滑滚动到底部；
  - 提供快捷 Prompt 气泡（“租房文案”、“限流原理解析”等）。

---

### Task 4: Prompt 调试实验室 (`/experience`) 参数调优闭环 (Prompt Lab)

**Files:**
- Modify: `chatling-bootstrap/src/main/resources/static/index.html`
- Modify: `chatling-core-engine/src/main/java/com/chatling/engine/service/ModelEngineService.java`

- [ ] **Step 1: 左侧参数控制台绑定**
  - 支持多模型下拉切换；
  - 绑定 System Prompt 多行文本框；
  - 绑定 Temperature (0.0~1.0) 与 Top-P (0.0~1.0) 实时双向绑定滑块；
  - 绑定 Max Tokens 参数。

- [ ] **Step 2: 运行调试与实时测速统计**
  - 点击“运行调试”后组装 System + User Messages 发起流式请求；
  - 实时捕获首字到达时间并计算出 `TTFT (Time to First Token)` 与 `总耗时 (Total Latency)` 显示在状态栏。

---

### Task 5: 模型池上游节点管理闭环 (Model Pool Management)

**Files:**
- Modify: `chatling-admin/src/main/java/com/chatling/admin/controller/AdminApiController.java`
- Modify: `chatling-bootstrap/src/main/resources/static/index.html`

- [ ] **Step 1: 模型管理 API (`/api/admin/models`) 完善**
  - `GET /api/admin/models`：获取全部纳管模型列表。
  - `POST /api/admin/models`：新增私有化模型（vLLM/Ollama）或公有云模型。
  - `PUT /api/admin/models`：编辑 BaseURL、API 密钥、超时时间或 Fallback 降级模型。
  - `DELETE /api/admin/models/{id}`：下线并移除模型节点。

- [ ] **Step 2: 前端模型表格渲染与下拉联动**
  - 在体验广场和 Prompt 实验室的模型下拉框中，自动从后端获取最新在线模型列表进行动态渲染。

---

## 验证与验收方案 (Verification & Acceptance)

1. **编译构建**：在根目录执行 `mvn clean compile` 确保 5 个模块均 `BUILD SUCCESS`。
2. **应用启动**：运行 `ChatlingGatewayApplication`，控制台无报错并输出 `8088` 启动成功日志。
3. **端到端体验**：
   - 浏览器打开 `http://localhost:8088`，查看 4 项核心指标均正常显示；
   - 在 API Key 页面创建一张新 Key 并复制；
   - 在模型体验广场发送一句话，观察打字机流式输出是否顺畅；
   - 回到网关看板，确认审计日志已新增刚刚的调用记录（含 TTFT、Token 计数）。
