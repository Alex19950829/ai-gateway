# chatling-common 模块设计文档 (Architecture & Design)

## 一、 模块定位与职责
`chatling-common` 是全平台的通用数据层，定义了标准 DTO、领域实体（Entity）以及公共返回包装对象。

---

## 二、 核心实体与 DTO 定义

| 实体 / DTO | 类名路径 | 作用说明 |
| :--- | :--- | :--- |
| **OpenAiDto** | `com.chatling.common.dto.OpenAiDto` | 包含 `ChatCompletionRequest`、`ChatCompletionResponse`、`ChatCompletionChunk` 等对齐 OpenAI 标准协议的对象 |
| **ApiKey** | `com.chatling.common.model.ApiKey` | 业务接入凭证实体，包含 `allowed_models`、`tpm_limit`、`qps_limit` 等字段 |
| **ModelConfig** | `com.chatling.common.model.ModelConfig` | 上游大模型配置实体，包含 `base_url`、`api_secret`、`fallback_model` 等字段 |
| **UsageDaily** | `com.chatling.common.model.UsageDaily` | 每日 Token 消耗聚合实体 |
| **ChatAudit** | `com.chatling.common.model.ChatAudit` | 单次调用审计流水实体（TTFT、Token消耗、响应码） |
| **CommonResult** | `com.chatling.common.model.CommonResult` | RESTful 统一响应包装对象 |
