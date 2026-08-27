# chatling-bootstrap (统一启动入口与前端 SPA 模块)

## 1. 模块定位
`chatling-bootstrap` 是整个灵犀平台的**聚合打包、主启动入口与内置前端交互中心**。它集成了其余四个业务子模块，内置了 H2 数据库初始化脚本与一套开箱即用的现代化 Web 单页应用（SPA）。

---

## 2. 核心功能与目录结构

```
chatling-bootstrap/
├── src/main/java/com/chatling/bootstrap/
│   ├── ChatlingGatewayApplication.java      # Spring Boot 主启动入口
│   └── config/
│       └── CorsConfig.java                  # 全局跨域与 WebClient 配置
└── src/main/resources/
    ├── application.yml                      # 全局配置文件 (端口 8088, 内嵌 H2 数据源)
    ├── schema.sql                           # 数据库表结构与默认数据初始化脚本
    └── static/
        └── index.html                       # 现代化前端单页应用 (Vue/Tailwind 风格，内含 5 大功能板块)
```

---

## 3. 内置前端 Web 控制台 5 大功能板块

用户直接访问 `http://localhost:8088` 即可使用：

1. ⚡ **网关看板 (Dashboard)**：全平台实时 KPI 大屏（Tokens、QPS、TTFT 延迟）与调用流水日志表格。
2. 🔑 **API Key 管理中心**：可视化创建 Key、设置 TPM/QPS、分配模型权限、一键复制。
3. 💬 **模型体验广场 (`/textModel`)**：多模型在线对话交互，支持真实打字机流式 SSE 动画输出。
4. 🧪 **Prompt 调试实验室 (`/experience`)**：在线调试系统人设 Prompt、调节 Temperature、Top-P 参数并测速。
5. ⚙️ **模型池配置**：管理私有化模型（vLLM）与公有云模型上游节点与主备 Fallback。
