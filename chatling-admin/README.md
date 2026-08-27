# chatling-admin (业务管理控制面模块)

## 1. 模块定位
`chatling-admin` 是面向内部开发者与平台运营人员的**业务管理与数据中台服务**。提供 API Key 凭证全生命周期治理、大模型上游集群管理、全景用量监控以及调用审计日志查询接口。

---

## 2. 核心功能与包结构

```
com.chatling.admin/
└── controller/
    └── AdminApiController.java         # 控制台管理接口 (/api/admin/*)
```

---

## 3. 核心管理能力
1. **API Key 凭证管理 (`/api/admin/apikeys`)**：
   - 生成带有业务前缀的 `sk-chatling-xxx` 凭证；
   - 绑定允许调用的模型白名单（如 `chatling-turbo,deepseek-v3` 或 `*` 全部）；
   - 配置 TPM 限额（每分钟 Token）与 QPS 限制；
   - 支持在线一键启用/禁用与删除。
2. **模型池配置管理 (`/api/admin/models`)**：
   - 动态增删改查上游模型节点（BaseURL、API Key、超时时间、Fallback 目标模型）。
3. **监控大屏与用量统计 (`/api/admin/dashboard/*`)**：
   - 实时聚合全平台总消耗 Tokens、总请求次数、平均首字延迟（TTFT）、有效 Key 数量；
   - 查询最近调用审计流水与每日 Token 消耗趋势报表。
