# chatling-admin 模块设计文档 (Architecture & Design)

## 一、 模块定位与职责
`chatling-admin` 是灵犀平台的**业务管理与控制面（Control Plane）**，负责 API Key 凭证生命周期管理、50+ 大模型资产管理、多租户按需权限分配与审批流、用量看板统计。

---

## 二、 核心架构：双端权限交互与模型资产绑定流程

### 1. 双端（用户端 vs 管理员端）权限申请与审批时序图

```mermaid
sequenceDiagram
    autonumber
    actor Dev as 普通业务研发 (用户端 UI)
    actor AdminUser as 平台管理员 (Admin UI)
    participant AdminService as chatling-admin 服务
    participant DB as 数据库 (t_model_config & t_api_key)
    participant Gateway as chatling-dataplane 网关数据面

    Note over Dev, DB: 阶段一：用户在模型广场浏览并按需申请
    Dev->>AdminService: 1. 获取全量 50 个模型资产 (GET /api/admin/models)
    AdminService->>DB: 查询 t_model_config (获取全量50个模型)
    Dev->>AdminService: 2. 获取当前用户的 Key 凭据 (GET /api/admin/apikeys)
    AdminService->>DB: 查询 t_api_key 获取当前用户 allowed_models
    Dev->>Dev: 前端渲染 50 个模型卡片：<br>• 5 个已授权模型显示【已开通 (绿色) / 立即体验】<br>• 45 个未授权模型显示【未开通 (带锁) / 申请开通】
    Dev->>AdminService: 3. 勾选未开通模型，发起申请 (POST /api/admin/apikeys)
    AdminService->>DB: 写入/更新 t_api_key (记录申请的模型列表，初始为待审批或分配状态)

    Note over AdminUser, DB: 阶段二：管理员审批与模型授权
    AdminUser->>AdminService: 4. 管理员在后台审批通过，配置限额 (PUT /api/admin/apikeys)
    AdminService->>DB: 更新 t_api_key 中的 allowed_models = "model1,model2,model3,model4,model5"

    Note over Dev, Gateway: 阶段三：网关数据面精准鉴权与路由拦截
    Dev->>Gateway: 5. 业务代码发起推理 (POST /v1/chat/completions, Key: sk-user-xxx, model: model1)
    Gateway->>DB: 校验 key.allowed_models 是否包含 model1
    Gateway->>Gateway: ✅ 校验通过，放行请求，转发至 Engine 推理
    
    Dev->>Gateway: 6. 尝试调用未授权模型 (model: model6)
    Gateway-->>Dev: ❌ 拦截并返回 403 Forbidden: "当前 API Key 无权访问该模型，请前往控制台申请权限"
```

---

## 三、 数据模型与权限绑定设计

### 1. 物理表结构划分

#### ① 全量大模型资产底表 (`t_model_config`)
平台纳管的 50 个模型在此统一维护：
* `model_name`: 模型唯一英文标识（如 `chatling-turbo`, `deepseek-v3`, `qwen-max`）；
* `display_name`: 模型展示名称（如 `灵犀自研生活服务大模型`）；
* `provider_type`: 提供商类型（`vllm`, `openai`, `dashscope`, `mock`）；
* `base_url`: 上游推理节点地址。

#### ② 用户 API Key 凭证与权限表 (`t_api_key`)
每个用户/业务线在表中持有一条凭证记录：
* `api_key`: `sk-chatling-xxxx`（唯一凭证）；
* `owner_name`: 申请人（如 `zhangsan`）；
* `department`: 归属部门（如 `房产事业部`）；
* **`allowed_models`（权限核心字段）**：
  * 若为 `*`：代表拥有全部 50 个模型的调用权限；
  * 若为指定列表：以逗号分隔，如 `"chatling-turbo,deepseek-v3,qwen-max"`，仅对这几个模型拥有访问权限。
* `tpm_limit`: 每分钟最大 Token 消耗限额；
* `qps_limit`: 每秒最大请求数。

---

## 四、 对外暴露的 RESTful 接口清单

| 接口分组 | Method | 接口路径 | 说明 |
| :--- | :--- | :--- | :--- |
| **模型资产查询** | `GET` | `/api/admin/models` | 获取全平台 50 个模型全量列表 |
| | `POST` | `/api/admin/models` | 管理员录入新模型节点 |
| | `POST` | `/api/admin/models/test-connection` | 测试上游模型 BaseURL 与 Key 连通性 |
| **API Key 与权限** | `GET` | `/api/admin/apikeys` | 获取 Key 列表及其 `allowed_models` 权限 |
| | `POST` | `/api/admin/apikeys` | 用户申请 / 管理员创建 API Key |
| | `PUT` | `/api/admin/apikeys/{apiKey}/status` | 启用 / 禁用 Key |
| | `DELETE` | `/api/admin/apikeys/{id}` | 删除指定 Key |
| **监控看板与审计** | `GET` | `/api/admin/dashboard/stats` | 看板核心 KPI 指标聚合 |
| | `GET` | `/api/admin/dashboard/audits` | 最近 50 条调用审计流水 |
