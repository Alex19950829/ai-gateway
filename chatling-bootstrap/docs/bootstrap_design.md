# chatling-bootstrap 模块设计文档 (Architecture & Design)

## 一、 模块定位与职责
`chatling-bootstrap` 是整个灵犀平台的**统一启动入口与前端 SPA 静态托管中心**。

---

## 二、 核心组成

1. **主启动类**：`com.chatling.bootstrap.ChatlingGatewayApplication`，一键拉起 Spring Boot WebFlux 与全部子模块服务。
2. **内嵌数据库初始化**：`src/main/resources/schema.sql`，启动时自动初始化 H2 数据源和演示模型配置。
3. **前端单页应用 (SPA)**：`src/main/resources/static/index.html`，提供 58 灵犀官方同款 Ant Design 亮白风格的操作界面：
   - 📊 **网关看板**：实时大屏统计与审计流水；
   - 🔑 **API Key 管理**：一键生成 `sk-chatling-xxx` 通用 Key；
   - 💬 **模型体验广场 (`/textModel`)**：多模型打字机流式对话；
   - 🧪 **Prompt 调试实验室 (`/experience`)**：系统人设与超参数实时测速；
   - ⚙️ **模型池配置**：上游模型节点管理。
