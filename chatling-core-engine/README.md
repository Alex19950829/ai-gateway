# chatling-core-engine (核心大模型引擎适配模块)

## 1. 模块定位
`chatling-core-engine` 是灵犀平台的大模型统一调度与多源适配引擎。它负责屏蔽底层各类异构大模型（自研私有化推理集群、开源框架、公有云商用 API）的协议差异，对外输出统一的响应式（Reactive Flux/Mono）调用能力。

---

## 2. 核心功能与架构

```
com.chatling.engine/
├── adapter/
│   ├── ModelAdapter.java               # 统一模型适配器顶层接口 (supports, streamChat, syncChat)
│   ├── OpenAiCompatibleAdapter.java    # OpenAI 兼容协议适配器 (支持 vLLM, SGLang, Ollama, DeepSeek, 阿里云通义百炼)
│   └── MockModelAdapter.java           # 本地模拟打字机适配器 (供本地无网络/无Key时极速调试体验)
└── service/
    └── ModelEngineService.java         # 统一大模型调度与路由引擎服务 (负责适配器分发与 Fallback 降级)
```

---

## 3. 核心机制说明
1. **多模型协议抽象**：
   - 基于 Spring WebFlux 响应式非阻塞 `WebClient`，以极低的系统开销维系上万并发 SSE 流式长连接。
2. **零成本本地调试（Mock 兜底）**：
   - 若上游模型未配置真实 API 密钥或网络不通，`MockModelAdapter` 会自动模拟生成打字机流式响应，方便本地开发测试。
3. **主备容灾与 Fallback 降级**：
   - 当主力模型调用出现网络异常或 5xx 时，引擎支持无缝透明转移至备用模型。
