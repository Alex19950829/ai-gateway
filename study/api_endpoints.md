# Chatling-Gateway 完整接口清单与协议规范

本文档归档了 `/Users/a58/Downloads/学习/网关/chatling-gateway` 项目对外暴露的全部 **34 个 REST API 接口**，涵盖 **业务管控面（16 个）**、**规则引擎与高级特性管控（16 个）** 以及 **网关数据面核心接口（2 个）**。

---

## 目录
- [一、 业务管控面接口 (AdminApiController - 16 个)](#一-业务管控面接口-adminapicontroller---16-个)
  - [1. API Key 凭证治理 (4 个)](#1-api-key-凭证治理-4-个)
  - [2. 上游模型配置与连通性探活 (5 个)](#2-上游模型配置与连通性探活-5-个)
  - [3. 大盘监控与用量审计 (3 个)](#3-大盘监控与用量审计-3-个)
  - [4. 模型使用权限申请与审批流 (4 个)](#4-模型使用权限申请与审批流-4-个)
- [二、 规则引擎与高级特性管控接口 (FactorRuleAdminController - 16 个)](#二-规则引擎与高级特性管控接口-factorruleadmincontroller---16-个)
  - [1. 特征/因子库管理 (3 个)](#1-特征因子库管理-3-个)
  - [2. Groovy 动态规则管理与在线调试 (5 个)](#2-groovy-动态规则管理与在线调试-5-个)
  - [3. 模型策略流水线绑定 (4 个)](#3-模型策略流水线绑定-4-个)
  - [4. AI RAG 知识库管理 (3 个)](#4-ai-rag-知识库管理-3-个)
  - [5. 阿里云绿网 2.0 内容安全机审 (1 个)](#5-阿里云绿网-20-内容安全机审-1-个)
- [三、 网关数据面核心接口 (GatewayChatController - 2 个)](#三-网关数据面核心接口-gatewaychatcontroller---2-个)

---

## 一、 业务管控面接口 (AdminApiController - 16 个)
- **Controller 类**：`com.chatling.admin.controller.AdminApiController`
- **基础路径 (Base Path)**：`/api/admin`

### 1. API Key 凭证治理 (4 个)

| 序号 | 接口名称 | 请求方法 | 接口路径 | 核心请求参数 | 返回内容 | 说明 |
| :---: | :--- | :---: | :--- | :--- | :--- | :--- |
| 1 | 查询 API Key 列表 | `GET` | `/api/admin/apikeys` | 无 | `List<ApiKey>` | 获取所有已签发凭据及其所属租户、配额、并发数与模型白名单 |
| 2 | 创建新 API Key | `POST` | `/api/admin/apikeys` | Body: `ApiKey` 对象 | `ApiKey` | 签发 `sk-chatling-xxx`，配置 `totalQuota`、`maxConcurrency`、`allowedModels` |
| 3 | 修改 API Key 状态 | `PUT` | `/api/admin/apikeys/{apiKey}/status` | Path: `apiKey`<br/>Query: `status` (1:启用, 0:禁用) | `CommonResult<Void>` | 快速拉黑/封禁或重新激活某个 API Key |
| 4 | 删除 API Key | `DELETE` | `/api/admin/apikeys/{id}` | Path: `id` (主键 ID) | `CommonResult<Void>` | 硬删除废弃凭证 |

### 2. 上游模型配置与连通性探活 (5 个)

| 序号 | 接口名称 | 请求方法 | 接口路径 | 核心请求参数 | 返回内容 | 说明 |
| :---: | :--- | :---: | :--- | :--- | :--- | :--- |
| 5 | 查询模型配置列表 | `GET` | `/api/admin/models` | 无 | `List<ModelConfig>` | 获取所有接入的上游大模型（DeepSeek、豆包、通义千问、vLLM 等） |
| 6 | 新增模型配置 | `POST` | `/api/admin/models` | Body: `ModelConfig` 对象 | `ModelConfig` | 注册模型，设置 `baseUrl`、`apiSecret`、`fallbackModel` 等 |
| 7 | 修改模型配置 | `PUT` | `/api/admin/models` | Body: `ModelConfig` 对象 | `ModelConfig` | 更新模型接入点地址、密钥或兜底降级模型 |
| 8 | 删除模型配置 | `DELETE` | `/api/admin/models/{id}` | Path: `id` | `CommonResult<Void>` | 移除模型节点 |
| 9 | 上游真实连通性测试 | `POST` | `/api/admin/models/test-connection` | Body: `ModelConfig` | `{connected, latencyMs, message}` | 向指定大模型 BaseURL 和 Key 发起探活握手，测试网络与鉴权连通性 |

### 3. 大盘监控与用量审计 (3 个)

| 序号 | 接口名称 | 请求方法 | 接口路径 | 核心请求参数 | 返回内容 | 说明 |
| :---: | :--- | :---: | :--- | :--- | :--- | :--- |
| 10 | 仪表盘总体概览 | `GET` | `/api/admin/dashboard/stats` | 无 | `{todayTotalRequests, todayTotalTokens, avgTtftMs, ...}` | 获取今日调用总量、Token 消耗、首字耗时（TTFT）大盘数据 |
| 11 | 查询最近审计明细 | `GET` | `/api/admin/dashboard/audits` | Query: `limit` (默认 50) | `List<ChatAudit>` | 查询请求级别审计流水（TTFT、Token 计数、状态码、错误原因） |
| 12 | 每日用量趋势报表 | `GET` | `/api/admin/dashboard/usage` | Query: `limit` (默认 30 天) | `List<UsageDaily>` | 按天/部门/模型汇总的历史 Token 消耗与请求趋势 |

### 4. 模型使用权限申请与审批流 (4 个)

| 序号 | 接口名称 | 请求方法 | 接口路径 | 核心请求参数 | 返回内容 | 说明 |
| :---: | :--- | :---: | :--- | :--- | :--- | :--- |
| 13 | 提交模型申请 | `POST` | `/api/admin/applies` | Body: `ModelApply` 对象 | `ModelApply` | 业务方申请调用某大模型，填写申请配额、业务理由及部门 |
| 14 | 查询申请单列表 | `GET` | `/api/admin/applies` | Query: `status` (可选: 0待审, 1通过, 2驳回) | `List<ModelApply>` | 管理员审核后台拉取申请单流转状态 |
| 15 | 审批通过申请 | `PUT` | `/api/admin/applies/{id}/approve` | Path: `id` | `CommonResult<Void>` | 审核通过，自动下发权限并为申请人增加 Token 配额 |
| 16 | 审批驳回申请 | `PUT` | `/api/admin/applies/{id}/reject` | Path: `id` | `CommonResult<Void>` | 驳回申请单并记录状态 |

---

## 二、 规则引擎与高级特性管控接口 (FactorRuleAdminController - 16 个)
- **Controller 类**：`com.chatling.admin.controller.FactorRuleAdminController`
- **基础路径 (Base Path)**：`/api/admin`

### 1. 特征/因子库管理 (3 个)

| 序号 | 接口名称 | 请求方法 | 接口路径 | 核心请求参数 | 返回内容 | 说明 |
| :---: | :--- | :---: | :--- | :--- | :--- | :--- |
| 17 | 查询特征因子列表 | `GET` | `/api/admin/factors` | 无 | `Collection<FactorDefinition>` | 获取滑动窗口 Token、QPM 计数、独立 IP 等特征定义 |
| 18 | 注册/保存特征因子 | `POST` | `/api/admin/factors` | Body: `FactorDefinition` | `FactorDefinition` | 动态新增指标因子（如 `f_custom_qpm`、`f_phone_number_count`） |
| 19 | 删除特征因子 | `DELETE` | `/api/admin/factors/{code}` | Path: `code` (特征编码) | `CommonResult<Void>` | 移除废弃特征 |

### 2. Groovy 动态规则管理与在线调试 (5 个)

| 序号 | 接口名称 | 请求方法 | 接口路径 | 核心请求参数 | 返回内容 | 说明 |
| :---: | :--- | :---: | :--- | :--- | :--- | :--- |
| 20 | 查询规则列表 | `GET` | `/api/admin/rules` | 无 | `Collection<RuleDefinition>` | 查询已加载的所有 Groovy 决策规则 |
| 21 | 保存/更新规则 | `POST` | `/api/admin/rules` | Body: `RuleDefinition` | `RuleDefinition` | 保存 Groovy 规则并触发实时编译加载 |
| 22 | 删除规则 | `DELETE` | `/api/admin/rules/{code}` | Path: `code` | `CommonResult<Void>` | 卸载并删除规则 |
| 23 | Groovy 语法校验 | `POST` | `/api/admin/rules/test-syntax` | Body: `RuleDefinition` | `String` (编译信息) | 在线编译校验 Groovy 脚本是否存在语法/类型错误 |
| 24 | 规则沙箱试运行 (Dry-Run) | `POST` | `/api/admin/rules/dry-run` | Body: `{groovyScript, factors: {...}}` | `{passed, action, message, compileTimeMs, execTimeMs}` | 模拟传入特征变量并执行 Groovy 脚本，输出执行决策与微秒耗时 |

### 3. 模型策略流水线绑定 (4 个)

| 序号 | 接口名称 | 请求方法 | 接口路径 | 核心请求参数 | 返回内容 | 说明 |
| :---: | :--- | :---: | :--- | :--- | :--- | :--- |
| 25 | 查询所有模型策略 | `GET` | `/api/admin/model-policies` | 无 | `Collection<ModelPolicyDefinition>` | 查询每个模型绑定的规则责任链清单 |
| 26 | 查询指定模型策略 | `GET` | `/api/admin/model-policies/{modelName}` | Path: `modelName` | `ModelPolicyDefinition` | 获取指定模型（如 `deepseek-v3`）的防护策略 |
| 27 | 配置/保存模型策略 | `POST` | `/api/admin/model-policies` | Body: `ModelPolicyDefinition` | `ModelPolicyDefinition` | 为目标模型绑定有序规则执行链与阻断行为 |
| 28 | 清理模型策略 | `DELETE` | `/api/admin/model-policies/{modelName}` | Path: `modelName` | `CommonResult<Void>` | 解除目标模型的所有绑定策略 |

### 4. AI RAG 知识库管理 (3 个)

| 序号 | 接口名称 | 请求方法 | 接口路径 | 核心请求参数 | 返回内容 | 说明 |
| :---: | :--- | :---: | :--- | :--- | :--- | :--- |
| 29 | 查询知识库文档列表 | `GET` | `/api/admin/rag/docs` | 无 | `Collection<KnowledgeDoc>` | 获取所有私域知识库文档索引 |
| 30 | 录入/保存知识库文档 | `POST` | `/api/admin/rag/docs` | Body: `KnowledgeDoc` | `KnowledgeDoc` | 新增文档片段与关键词匹配索引 |
| 31 | 删除知识库文档 | `DELETE` | `/api/admin/rag/docs/{docId}` | Path: `docId` | `CommonResult<Void>` | 移除知识库文档 |

### 5. 阿里云绿网 2.0 内容安全机审 (1 个)

| 序号 | 接口名称 | 请求方法 | 接口路径 | 核心请求参数 | 返回内容 | 说明 |
| :---: | :--- | :---: | :--- | :--- | :--- | :--- |
| 32 | 手动内容安全送审 | `POST` | `/api/admin/security/check` | Body: `{"text": "待检测内容"}` | `ModerationResult` | 在线测试阿里云绿网 2.0 增强版机审审核结果及风险标签 |

---

## 三、 网关数据面核心接口 (GatewayChatController - 2 个)
- **Controller 类**：`com.chatling.gateway.controller.GatewayChatController`
- **基础路径 (Base Path)**：`/v1`

| 序号 | 接口名称 | 请求方法 | 接口路径 | 核心请求参数 | 返回内容 | 说明 |
| :---: | :--- | :---: | :--- | :--- | :--- | :--- |
| 33 | **标准 OpenAI 聊天补全接口** | `POST` | `/v1/chat/completions` | Header: `Authorization: Bearer sk-xxx`<br/>Body: `ChatCompletionRequest` (`model`, `messages`, `stream`) | 流式 `text/event-stream` (SSE 打字机) 或 同步 JSON `ChatCompletionResponse` | **网关核心处理主管道**：包含 API Key 鉴权、并发长连接控制、模型白名单校验、总配额校验、数据脱敏、Groovy 规则判定、绿网合规审核、敏感词拦截、RAG 上下文增强、Prompt 缓存加速、TPM 令牌桶限流、负载均衡、大模型流式推理与异步落盘计量 |
| 34 | **可用模型列表查询接口** | `GET` | `/v1/models` | 无 | `{object: "list", data: [...]}` | 标准 OpenAI 模型列表规范，供客户端动态拉取网关当前可用的模型枚举 |
