# chatling-common (公共基础模块)

## 1. 模块定位
`chatling-common` 是整个灵犀大模型平台的公共基础层，定义了平台通用的标准协议、数据传输对象（DTO）、领域实体模型（Entity）以及公共返回包装。所有其他业务子模块均依赖本模块。

---

## 2. 核心功能与包结构

```
com.chatling.common/
├── dto/
│   └── OpenAiDto.java          # 标准 OpenAI 兼容协议数据对象 (ChatCompletionRequest, Response, Chunk 等)
└── model/
    ├── ApiKey.java             # API Key 凭证与权限实体 (Key、所有者、所属部门、允许模型、TPM/QPS、配额)
    ├── ModelConfig.java        # 大模型上游配置实体 (模型标识、提供商类型、BaseURL、降级模型 Fallback)
    ├── UsageDaily.java         # 每日 Token 用量聚合报表实体 (按日期、Key、模型维度的 Token 与请求数)
    ├── ChatAudit.java          # 单次调用审计流水实体 (请求ID、耗时、TTFT 首字延迟、输入/输出 Token 数)
    └── CommonResult.java       # 全局统一 RESTful 响应包装对象 (code, message, data)
```

---

## 3. 设计规范
- **高内聚、零业务依赖**：本模块仅包含 POJO 与 DTO 定义，不引入任何数据库或网络驱动依赖。
- **协议标准化**：严格对齐 OpenAI OpenAPI 规范，确保网关对外暴露的接口无缝兼容 LangChain、Dify 及各类大模型 SDK。
