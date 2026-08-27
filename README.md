# 灵犀 AI 大模型服务平台与网关 (Chatling Gateway)

企业级大模型（LLM）统一路由、API Key 治理、Token 级限流与在线体验中台。

---

## 一、 核心架构与多模块分层

本项目采用清晰规范的 **Maven 多模块分层架构**：

```
chatling-gateway/ (Maven 根父工程)
├── chatling-common              # 1. 公共基础层 (OpenAI 协议 DTO、通用返回体 CommonResult、实体模型)
├── chatling-core-engine          # 2. 核心模型适配引擎 (ModelAdapter 抽象、vLLM/OpenAI/通义/Mock 适配器)
├── chatling-dataplane           # 3. AI 网关数据面 (/v1/chat/completions 接口、API Key 鉴权、TPM 令牌桶限流、SSE 流式计量)
├── chatling-admin                # 4. 业务控制面 (API Key 治理接口、用量统计、模型配置接口)
└── chatling-bootstrap            # 5. 启动入口与前端 SPA (主启动类、内置 H2 数据库初始化、现代化 Web 控制台)
```

---

## 二、 核心功能特性

1. **统一 OpenAI 规范 API**：
   - 暴露标准 `POST /v1/chat/completions` 与 `GET /v1/models`，支持任意 SDK、LangChain、Dify 或 cURL 直连。
2. **API Key 凭据与配额治理**：
   - 支持在 Web 界面随时生成 `sk-chatling-xxx` Key，支持按业务线分配、绑定模型白名单、设置 TPM（每分钟 Token）与 QPS 限制。
3. **基于令牌桶的 Token 级流式限流**：
   - 基于 Caffeine 高性能本地缓存实现毫秒级滑动窗口 TPM 限流拦截，防止大模型算力被单业务耗尽。
4. **模型体验广场 (`/textModel`)**：
   - 网页内置打字机流式 SSE 对话界面，支持多模型切换体验与 Token 消耗实时统计。
5. **Prompt 调试实验室 (`/experience`)**：
   - 提供系统人设 Prompt 设定、Temperature、Top-P、Max Tokens 参数实时调优与 TTFT 首字延迟测速。
6. **用量与调用审计看板**：
   - 自动记录每一次调用的首字耗时（TTFT）、总耗时、输入/输出 Token 数，提供全景监控报表。

---

## 三、 快速启动与本地体验

### 1. 启动服务
在 IDEA 中直接打开 `chatling-gateway` 项目，运行主类：
```
com.chatling.bootstrap.ChatlingGatewayApplication
```

### 2. 浏览器访问控制台与体验广场
打开浏览器访问：
👉 **http://localhost:8088**

### 3. API 接口调用示例
使用默认系统体验 Key 即可发起标准流式请求：
```bash
curl http://localhost:8088/v1/chat/completions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer sk-chatling-admin-demo888" \
  -d '{
    "model": "chatling-turbo",
    "messages": [{"role": "user", "content": "你好，请介绍一下灵犀AI网关的功能"}],
    "stream": true
  }'
```
