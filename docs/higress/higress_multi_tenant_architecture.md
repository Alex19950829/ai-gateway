# Higress 企业级全景架构与多租户全治理体系

本文档系统性阐述了 **Higress AI 网关在企业级生产环境下的全景架构**，涵盖 **控制面与数据面解耦**、**多租户双层 API Key 体系**、**六大 Wasm 插件处理流水线** 以及 **后端存储与外部安全服务的协同机制**。

---

## 目录
- [一、 Higress 企业级全景架构图 (Ultimate Architecture)](#一-higress-企业级全景架构图-ultimate-architecture)
- [二、 数据面 Wasm 插件责任链流水线 (Pipeline)](#二-数据面-wasm-插件责任链流水线-pipeline)
- [三、 多用户「双层 API Key」多租户治理架构](#三-多用户双层-api-key多租户治理架构)
- [四、 核心插件与基础设施依赖关系](#四-核心插件与基础设施依赖关系)
- [五、 全链路调用时序流转图](#五-全链路调用时序流转图)

---

## 一、 Higress 企业级全景架构图 (Ultimate Architecture)

```mermaid
flowchart TB
    subgraph ClientLayer["1. 客户端与多租户接入层"]
        UserA["客服团队 customer-service<br/>Key: sk-higress-cs-888"]
        UserB["电商系统 dianshang-app<br/>Key: sk-higress-ec-666"]
        UserC["外包团队 wiabao-team<br/>Key: sk-higress-dev-111"]
        UserAdmin["超级管理员 Admin<br/>Key: sk-higress-admin-999"]
    end

    subgraph ControlPlane["2. 控制面 (Higress Controller - Istio Pilot 内核)"]
        UI["Higress Web 控制台 :8001"]
        K8sCRD["K8s Gateway API / WasmPlugin CRD"]
        Registry["微服务注册中心 (Nacos / CoreDNS)"]
        
        UI --> Pilot["Pilot 配置解析引擎"]
        K8sCRD --> Pilot
        Registry --> Pilot
    end

    subgraph DataPlane["3. 数据面 (Higress Gateway - Envoy C++ 内核 :8080)"]
        direction TB
        EnvoyCore["Envoy 高性能事件驱动转发引擎"]
        
        subgraph WasmPipeline["六大 Wasm 插件核心处理流水线"]
            F1["1. key-auth<br/>身份认证 & 识别消费者"]
            F2["2. ai-quota<br/>Token 账户总余额/月度预算检查"]
            F3["3. ai-token-ratelimit<br/>TPM / RPM 瞬时滑动窗口流控"]
            F4["4. ai-cache<br/>DashVector 向量语义缓存 10ms 秒回"]
            F5["5. ai-security-guard<br/>阿里云 Green 输入/输出双向内容安全"]
            F6["6. ai-proxy / model-router<br/>多模型智能权重路由 & 故障自动 Fallback"]
            
            F1 --> F2 --> F3 --> F4 --> F5 --> F6
        end
        
        EnvoyCore <--> WasmPipeline
    end

    subgraph InfraStorage["4. 企业级支撑基础设施与安全服务"]
        RedisCluster[("Redis 集群 :6379<br/>• ai-quota 存储各租户余额<br/>• ai-token-ratelimit 分布式滑动窗口")]
        DashVector[("阿里云 DashVector 向量库<br/>• 存储 Prompt 向量与标准答案<br/>• Cosine 语义相似度计算")]
        GreenService["阿里云内容安全 Green 2.0<br/>• 涉政/暴恐/越狱注入检测"]
        Observability["Prometheus & Grafana :15020<br/>• 实时 QPS / TTFT 首字耗时 / Token 消耗"]
    end

    subgraph UpstreamLLMs["5. 上游大模型供应商资源池"]
        DeepSeek["DeepSeek 官方 API (多账号 Key 轮询)"]
        Doubao["火山方舟 豆包 Coding Plan (ep-xxxx)"]
        Qwen["阿里云 通义千问 (DashScope)"]
        PrivateLLM["企业私有算力 (vLLM / Ollama)"]
    end

    Pilot -->|"gRPC xDS 动态热下发 (0 重启)"| EnvoyCore
    ClientLayer -->|"HTTP :8080 带 Consumer Key"| EnvoyCore
    
    F2 <-->|"原子查询与扣减余额"| RedisCluster
    F3 <-->|"滑动窗口原子计数"| RedisCluster
    F4 <-->|"1536 维向量检索"| DashVector
    F5 <-->|"HTTPS 双向审核"| GreenService
    EnvoyCore -.->|"导出指标"| Observability
    
    F6 -->|"托管凭证转发"| DeepSeek
    F6 -->|"托管凭证转发"| Doubao
    F6 -->|"托管凭证转发"| Qwen
    F6 -->|"私网连接"| PrivateLLM
```

---

## 二、 数据面 Wasm 插件责任链流水线 (Pipeline)

每个请求进入网关后，严格按照以下优先级流水线执行：

```
[ 客户端请求: Authorization: Bearer sk-wiabao-xxx ]
      ⬇️
1. 【key-auth 认证】校验 Key 合法性，识别出身份: consumer = "wiabao-team" (未通过 ➔ 401)
      ⬇️
2. 【ai-quota 检查】查询 Redis 中 wiabao-team 账户总余额是否 > 0？ (余额为0 ➔ 429 配额用尽)
      ⬇️
3. 【ai-token-ratelimit 限流】wiabao-team 当前 1 分钟内 TPM/RPM 是否超限？ (超限 ➔ 429 速率限制)
      ⬇️
4. 【ai-cache 语义缓存】在 DashVector 中比对语义相似度是否 > 0.85？
      ├─► [✅ 命中] ➔ 直接 10ms 流式回放缓存答案 (0 Token 消耗，跳过后续步骤)
      └─► [❌ 未命中] ➔ 继续向下流转
      ⬇️
5. 【ai-security-guard 安全审核】调用阿里云 Green 审核 Prompt 是否包含越狱/涉政违规 (违规 ➔ 403 阻断)
      ⬇️
6. 【ai-proxy / model-router 路由】根据请求 model 匹配上游提供者，按 50%:50% 挑中空闲 DeepSeek 账号
      ⬇️
7. 【大模型推理 & SSE 流式输出】
      ├─► 实时流式传输给客户端
      ├─► 边传输边实时进行输出内容安全审核
      └─► 收到结束包后：
            • 异步原子累加限流计数 (ai-token-ratelimit)
            • 异步原子扣减账户余额 (ai-quota)
            • 异步将 (向量, 问题, 答案) 回写入 DashVector (ai-cache)
            • 异步上报 Token 指标至 Prometheus (ai-statistics)
```

---

## 三、 多用户「双层 API Key」多租户治理架构

```mermaid
flowchart LR
    subgraph Downstream["下游多租户 (持有独立 Consumer Key)"]
        User1["客服部<br/>Key: sk-higress-cs-888"]
        User2["电商部<br/>Key: sk-higress-ec-666"]
        User3["外包团队<br/>Key: sk-higress-dev-111"]
    end

    subgraph GatewayCore["Higress AI 网关数据面"]
        Auth["1. 消费者鉴权 (Key Auth)"]
        
        subgraph MultiTenantPolicy["2. 多租户独立管控策略"]
            P1["客服部：总预算 1000万 Token / 100 RPM"]
            P2["电商部：总预算 5000万 Token / 500 RPM"]
            P3["外包团队：总预算 50万 Token / 仅限轻量模型"]
        end
        
        KeyPool["3. 上游出资凭证集中托管与 Key 轮询池"]
        
        Auth --> MultiTenantPolicy --> KeyPool
    end

    subgraph UpstreamProviders["上游大模型官方 (企业统一账户)"]
        DS["DeepSeek 官方<br/>企业统一总账号"]
        DoubaoArk["火山方舟 豆包<br/>企业统一总账号"]
        AliyunDash["通义千问 官方<br/>企业统一总账号"]
    end

    User1 -->|"携带专属 Key"| Auth
    User2 -->|"携带专属 Key"| Auth
    User3 -->|"携带专属 Key"| Auth

    KeyPool -->|"统一出资账号"| DS
    KeyPool -->|"统一出资账号"| DoubaoArk
    KeyPool -->|"统一出资账号"| AliyunDash
```

---

## 四、 核心插件与基础设施依赖关系

| 插件名称 | 核心职责 | 外部基础设施依赖 | 失败兜底策略 (Fail Strategy) |
| :--- | :--- | :--- | :--- |
| **`key-auth`** | 识别多租户 Consumer 身份 | 无 (内存字典快速比对) | `FAIL_CLOSE` (未携带有效凭证直接 401) |
| **`ai-quota`** | 管理多租户总账户余额与预算 | **Redis 集群** (`chat_quota:`) | `FAIL_OPEN` (Redis 异常时可配置放行或报警) |
| **`ai-token-ratelimit`** | TPM / RPM 滑动窗口速率限制 | **Redis 集群** (`ratelimit:`) | `FAIL_OPEN` (避免 Redis 抖动影响主业务) |
| **`ai-cache`** | 向量语义缓存，实现 10ms / 0 Token 秒回 | **阿里云 DashVector 向量库** + **DashScope Embedding** | `FAIL_OPEN` (未命中或超时自动回退调用真实模型) |
| **`ai-security-guard`** | 输入与输出双向合规/防越狱检测 | **阿里云内容安全增强版 (Green 2.0)** | `FAIL_OPEN` / `FAIL_CLOSE` (根据企业安全等级定制) |
| **`ai-proxy`** | 多大模型协议归一化与统一转发 | 上游大模型服务商 API | 支持配置 Fallback 自动故障降级服务商 |
| **`ai-statistics`** | 全景链路监控与 Token 消耗审计 | **Prometheus + Grafana** (:15020) | 异步内存采集，对请求转发 0 延迟开销 |

---

## 五、 全链路调用时序流转图

```mermaid
sequenceDiagram
    autonumber
    actor Client as 下游应用 (wiabao-team)
    participant GW as Higress AI 网关
    participant Redis as Redis 集群
    participant DashVector as DashVector 向量库
    participant Green as 阿里云内容安全
    participant LLM as 上游大模型 (DeepSeek / 豆包)

    Client->>GW: 1. POST /v1/chat/completions (带 Authorization: Bearer sk-wiabao-111)
    
    Note over GW: 2. key-auth 校验：识别为 wiabao-team
    GW->>Redis: 3. ai-quota 检查：wiabao-team 余额是否 > 0？
    GW->>Redis: 4. ai-token-ratelimit 检查：wiabao-team 当前 1 分钟 Token 是否超限？
    
    GW->>DashVector: 5. ai-cache 检索：计算 Prompt 语义相似度
    alt 语义相似度 > 0.85 (命中缓存)
        DashVector-->>GW: 返回已缓存的答案
        GW-->>Client: ⚡ 10ms 极速流式回放 (0 Token 消耗，链路结束)
    else 未命中缓存
        GW->>Green: 6. ai-security-guard 输入检测：审核 Prompt 是否合规/越狱
        Green-->>GW: 审核通过 (Pass)
        
        GW->>LLM: 7. 使用网关托管的企业官方 Provider Key 转发请求
        LLM-->>GW: 8. SSE 流式输出回答 Chunk 1, Chunk 2...
        GW->>Green: 9. 并发进行输出内容安全合规审核
        GW-->>Client: 10. 实时流式回传给客户端
        
        LLM-->>GW: 11. 收到结束包 (total_tokens = 150)
        par 异步后置处理
            GW->>Redis: 扣减 wiabao-team 余额 (DECRBY 150)
            GW->>Redis: 累加 wiabao-team 限流计数 (INCRBY 150)
            GW->>DashVector: 异步回写 (向量, Prompt, 完整答案)
        end
    end
```
