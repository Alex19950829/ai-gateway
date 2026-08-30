# 企业级大模型网关上层用户治理与 QoS 架构设计规范 (Consumer Governance & QoS Spec)

## 一、 系统现状与设计目标

在大模型（LLM）API 网关体系中，治理能力必须从单一的**底层模型防护（Model-Centric）**扩展为**模型防护 + 上层租户治理（Model & Consumer Dual-Layer Governance）**的双重架构体系。

```mermaid
flowchart TD
    subgraph Layer1["1️⃣ 上层租户与消费者治理层 (Consumer Governance Layer)"]
        Consumer["客户端 / API Key 凭证"]
        Consumer --> Concurrency["🚀 活跃并发长连接控制 (Max Concurrency)"]
        Consumer --> WeeklyQuota["📅 周期性配额管理 (Weekly/Monthly Reset)"]
        Consumer --> QoS["👑 QoS 服务质量与租户分级 (VIP / Standard / Free)"]
        Consumer --> Masking["🔒 个人隐私与敏感数据脱敏 (Data Masking)"]
    end

    subgraph Layer2["2️⃣ 网关核心执行引擎 (Core Gateway Engine)"]
        Concurrency --> PolicyEngine["🛡️ Groovy 动态策略流水线 (Policy Pipeline)"]
        WeeklyQuota --> PolicyEngine
        QoS --> PolicyEngine
        Masking --> PolicyEngine
        PolicyEngine --> Green["🔒 阿里绿网 2.0 / 内容安全机审"]
        PolicyEngine --> RAG["🧠 知识库检索增强 (RAG)"]
        PolicyEngine --> JSON["📋 输出治理 (AI JSON Format)"]
    end

    subgraph Layer3["3️⃣ 底层大模型节点池 (Upstream Model Pool)"]
        Green --> Models["DeepSeek / 通义千问 / 火山方舟 / GLM / 自研集群"]
        RAG --> Models
        JSON --> Models
    end
```

---

## 二、 核心架构设计

### 1. 最大并发长连接控制 (Max Concurrency Manager)

由于大模型生成推理具有持续时间长（15~30s）的特性，并发控制必须基于响应式流信号（Reactive WebFlux Signals）进行安全生命周期回收。

```mermaid
sequenceDiagram
    autonumber
    actor Client as 客户端 (租户 A)
    participant GW as 网关并发控制器 (ConcurrencyManager)
    participant LLM as 下游大模型 (SSE 打字流)

    Client->>GW: 1. 发起流式聊天请求 (/v1/chat/completions)
    GW->>GW: 2. AtomicInteger.incrementAndGet()
    alt 当前并发数 > maxConcurrency (如 6 > 5)
        GW->>GW: 3. AtomicInteger.decrementAndGet() 立即归还
        GW-->>Client: 🔴 429 Too Many Requests ("当前活跃并发数已满 [6/5]，请等待已有长连接结束")
    else 当前并发数 <= maxConcurrency (如 3 <= 5)
        GW->>LLM: 4. 建立长连接并开始流式接收 Token
        LLM-->>Client: 5. 持续流式推送 SSE 数据包...
        alt 正常生成结束 / 客户端断开连接 / 上游超时异常
            LLM-->>GW: 6. 流终止信号 (onComplete / onError / onCancel)
            GW->>GW: 7. doFinally 触发 AtomicInteger.decrementAndGet() 槽位安全释放
        end
    end
```

---

### 2. 企业数据隐私与动态脱敏管道 (Data Masking & Unmasking Pipeline)

保护企业客户隐私数据（手机号、身份证、邮箱、银行卡），防止真实敏感数据直接泄露给公有云第三方大模型。

```mermaid
flowchart LR
    In["用户原始提问：<br/>'给客户张三 (手机号: 13812345678, 身份证: 110101199003072345) 发跟进邮件'"]
    --> Mask["1️⃣ 网关 DataMaskingGovernor<br/>(正则/NER 提取 + 映射记录)"]
    
    Mask -->|"2️⃣ 公网安全外发：<br/>'给客户张三 (手机号: [PHONE_1], 身份证: [IDCARD_1]) 发跟进邮件'"| Cloud["3️⃣ 第三方公有云大模型<br/>(DeepSeek / Qwen)"]
    
    Cloud -->|"4️⃣ 大模型推理返回：<br/>'尊敬的张三客户 ([PHONE_1])，感谢您的配合...'"| Unmask["5️⃣ 网关上下文反向还原<br/>([PHONE_1] ➔ 13812345678)"]
    
    Unmask --> Out["6️⃣ 客户端收到完整真实邮件<br/>(企业隐私数据 100% 零泄密)"]
```

---

### 3. 服务质量分级与路由调度 (QoS & Priority Routing)

```mermaid
flowchart TD
    Req["客户端请求 model = deepseek-chat"] --> Identify["1️⃣ 识别 API Key 凭证属性与 QoS 租户等级"]
    
    Identify --> Check{"判断 QoS 等级"}
    
    Check -->|👑 VIP 等级 (如 招聘生产核心线)| VIP["• 独享大并发池 (Max Concurrency=50)<br/>• 路由至 VIP 专属低延迟节点<br/>• 异常时毫秒级无缝自动容灾降级 (切换至备用 Qwen-Max)"]
    
    Check -->|⭐ Standard 等级 (常规业务)| STD["• 共享并发池 (Max Concurrency=10)<br/>• 常规排队与重试"]
    
    Check -->|🌱 Free/Test 等级 (测试体验)| FREE["• 严格低并发 (Max Concurrency=2)<br/>• 高峰期主动限流让路<br/>• 异常时降级至轻量低成本模型 (GLM-4-Flash)"]
```

---

## 三、 数据表与字段拓展规划

### 1. `t_api_key` 凭证表扩展：
```sql
ALTER TABLE t_api_key ADD COLUMN max_concurrency INT DEFAULT 5 COMMENT '租户最大活跃并发连接数';
ALTER TABLE t_api_key ADD COLUMN qos_tier VARCHAR(32) DEFAULT 'STANDARD' COMMENT 'QoS等级: VIP, STANDARD, FREE';
ALTER TABLE t_api_key ADD COLUMN quota_cycle VARCHAR(32) DEFAULT 'MONTHLY' COMMENT '配额重置周期: WEEKLY, MONTHLY, NEVER';
ALTER TABLE t_api_key ADD COLUMN cycle_quota_limit BIGINT DEFAULT 1000000 COMMENT '周期重置总额度Tokens';
ALTER TABLE t_api_key ADD COLUMN enable_data_masking INT DEFAULT 0 COMMENT '是否开启数据脱敏: 1开启, 0关闭';
```
