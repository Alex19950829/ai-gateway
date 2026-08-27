# chatling-admin 模型权限申请与审批流实施计划 (Implementation Plan)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 `chatling-admin`、数据库与 Web 控制台 UI 中实现完整的**模型权限申请与审批闭环体系**：用户端按需申请未开通的大模型 $\rightarrow$ 管理端【审批管理】页面出现审批单 $\rightarrow$ 管理员审批通过 $\rightarrow$ 系统原子更新 `t_api_key.allowed_models` $\rightarrow$ 用户端即刻显示【已开通】并可正常发起流式调用。

**Architecture:** 
1. **DB 层**：在 H2 中创建 `t_model_apply` 审批工单表；
2. **Entity / DAO 层**：在 `chatling-common` 和 `chatling-dataplane` 中新增 `ModelApply` 实体与 CRUD SQL；
3. **Admin 控制面**：在 `AdminApiController` 中提供申请提交、列表查询、一键通过与一键驳回接口；
4. **UI 前端**：在 `index.html` 模型广场中联动权限状态（已开通/审批中/未开通-点击申请），并新增【权限审批中心】管理员独立视图。

**Tech Stack:** Java 17 · Spring WebFlux · H2 Database · Fastjson2 · Tailwind CSS (58 Ant Design 风格)

---

## 全局约束与规范 (Global Constraints)
1. **权限原子追加**：审批通过时，必须原子地将新申请的模型名称（如 `qwen-max`）追加到该用户 `t_api_key.allowed_models` 字段中（例如由 `"chatling-turbo"` 变为 `"chatling-turbo,qwen-max"`）。
2. **状态流转严密**：0-待审批、1-已通过、2-已驳回。只有处于 1-已通过状态，网关和前端才认可该模型有权访问。
3. **双端无缝感知**：用户端模型卡片根据工单状态和当前 `allowed_models` 动态渲染标签（已开通 / 审批中 ⏳ / 申请权限）。

---

## 任务拆解与执行步骤 (Tasks Breakdown)

### Task 1: 数据表与领域实体定义

**Files:**
- Modify: `chatling-bootstrap/src/main/resources/schema.sql`
- Create: `chatling-common/src/main/java/com/chatling/common/model/ModelApply.java`
- Modify: `chatling-dataplane/src/main/java/com/chatling/gateway/repository/ChatlingDao.java`

- [ ] **Step 1: 在 `schema.sql` 中新增 `t_model_apply` 表 DDL 与种子数据**
- [ ] **Step 2: 在 `chatling-common` 中创建 `ModelApply` 实体**
- [ ] **Step 3: 在 `ChatlingDao` 中实现工单查询、插入、审批通过与追加 `allowed_models` 的 SQL 方法**

---

### Task 2: 后端 RESTful 申请与审批接口开发

**Files:**
- Modify: `chatling-admin/src/main/java/com/chatling/admin/controller/AdminApiController.java`
- Test: `chatling-admin/src/test/java/com/chatling/admin/AdminApplyControllerTest.java`

- [ ] **Step 1: 编写 `POST /api/admin/applies`（用户端发起申请）**
- [ ] **Step 2: 编写 `GET /api/admin/applies`（审批单列表查询）**
- [ ] **Step 3: 编写 `PUT /api/admin/applies/{id}/approve` 与 `PUT /api/admin/applies/{id}/reject`（管理员审批）**
- [ ] **Step 4: 编写自动化测试验证工单提交与审批后 `allowed_models` 的自动生效**

---

### Task 3: 前端 UI 侧模型广场与【权限审批中心】全流程联动

**Files:**
- Modify: `chatling-bootstrap/src/main/resources/static/index.html`

- [ ] **Step 1: 模型广场卡片按用户权限动态展示状态**
  - 如果已在 `allowed_models` 中 $\rightarrow$ 显示绿色【已开通】+【立即体验】；
  - 如果在 `t_model_apply` 中且 `status=0` $\rightarrow$ 显示橙色【审批中 ⏳】；
  - 否则 $\rightarrow$ 显示灰色【未开通】+【申请权限】（点击弹出申请理由对话框）。
- [ ] **Step 2: 在左侧导航新增【权限审批中心】管理员视图**
  - 展示待审批工单列表（申请人、部门、申请模型、申请理由、时间）；
  - 操作列提供【通过】和【驳回】按钮，点击一键生效并即时刷新状态。

---

### Task 4: 全链路端到端验收与验证

- [ ] **Step 1: 验证用户端在 UI 申请 `deepseek-v3` 权限**
- [ ] **Step 2: 在【权限审批中心】页面看到审批单，点击【通过】**
- [ ] **Step 3: 回到模型广场确认 `deepseek-v3` 变为【已开通】，并在【体验广场】成功调用**
