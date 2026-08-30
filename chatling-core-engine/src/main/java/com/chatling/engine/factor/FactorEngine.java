package com.chatling.engine.factor;

import com.chatling.common.factor.FactorDefinition;
import com.chatling.common.factor.FactorType;
import com.chatling.common.factor.IFactorAggregator;
import com.chatling.common.security.ModerationResult;
import com.chatling.engine.governance.DataMaskingGovernor;
import com.chatling.engine.security.AliyunGreenSecurityService;
import com.chatling.engine.security.DfaSensitiveWordService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class FactorEngine {
    private static final Logger log = LoggerFactory.getLogger(FactorEngine.class);

    @Autowired(required = false)
    private List<IFactorAggregator> aggregators = new ArrayList<>();

    @Autowired
    private AliyunGreenSecurityService aliyunGreenSecurityService;

    @Autowired
    private DataMaskingGovernor dataMaskingGovernor;

    @Autowired
    private DfaSensitiveWordService dfaSensitiveWordService;

    private final Map<String, IFactorAggregator> aggregatorMap = new ConcurrentHashMap<>();
    private final Map<String, FactorDefinition> factorDefinitionMap = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        factorDefinitionMap.clear();
        aggregatorMap.clear();

        if (aggregators != null) {
            for (IFactorAggregator aggregator : aggregators) {
                aggregatorMap.put(aggregator.getAggregatorCode(), aggregator);
            }
        }

        // ==================== 1. 字段特征 (FIELD) ====================
        registerFactor(new FactorDefinition("f_consumer_id", "消费者/租户身份标识", FactorType.FIELD, "String", null, "提取自当前请求的 API Key 或 Header"));
        registerFactor(new FactorDefinition("f_user_prompt", "用户提问 Prompt 文本", FactorType.FIELD, "String", null, "提取自请求 messages body 中的用户提问内容"));
        registerFactor(new FactorDefinition("f_custom_qpm", "租户审批定制 QPM 上限", FactorType.FIELD, "Long", null, "租户专属审批分配的每分钟请求上限 (如 38/60)"));
        registerFactor(new FactorDefinition("f_client_ip", "客户端真实 IP", FactorType.FIELD, "String", null, "提取自 X-Forwarded-For 或远程 Socket IP"));
        registerFactor(new FactorDefinition("f_model_name", "请求目标模型名称", FactorType.FIELD, "String", null, "客户端请求的目标大模型对外标识 (如 deepseek-chat)"));

        // ==================== 2. 聚合特征 (AGGREGATION - 5 大实现类) ====================
        registerFactor(new FactorDefinition("f_minute_req_cnt", "当前1分钟已调用频次 (QPM)", FactorType.AGGREGATION, "Long", "SlidingWindowCountAggregator", "滑动窗口统计该租户最近 60 秒内的请求总次数"));
        registerFactor(new FactorDefinition("f_minute_token_cnt", "当前1分钟已消耗Token数 (TPM)", FactorType.AGGREGATION, "Long", "SlidingWindowTokenAggregator", "滑动窗口统计该租户最近 60 秒内 SSE 累计消耗的 Token 真实总量"));
        registerFactor(new FactorDefinition("f_total_invoke_cnt", "历史累计调用次数 (Count++)", FactorType.AGGREGATION, "Long", "AtomicCounterAggregator", "基于原子计数器记录该租户历史总调用请求次数"));
        registerFactor(new FactorDefinition("f_account_balance", "Token 预算剩余额度 (ai-quota)", FactorType.AGGREGATION, "Long", "TokenQuotaDecrementAggregator", "账户剩余可用 Token 余额，欠费自动硬拦截"));
        registerFactor(new FactorDefinition("f_distinct_ip_cnt", "关联不同 IP 去重数", FactorType.AGGREGATION, "Long", "DistinctIpAggregator", "统计单个 API Key 近期被多少个不同 IP 共用，防跨网共享盗刷"));

        // ==================== 3. 名单特征 (LIST) ====================
        registerFactor(new FactorDefinition("f_ip_blacklist", "恶意客户端 IP 拦截黑名单", FactorType.LIST, "List", null, "命中即触发 403 风险阻断"));

        // ==================== 4. 内容安全与隐私特征 (SECURITY & PRIVACY - 惰性求值) ====================
        registerFactor(new FactorDefinition("f_aliyun_green_status", "阿里云绿网 2.0 机审合规标签", FactorType.FIELD, "String", null, "云端内容安全机审结果 (pass/political/porn/violence)"));
        registerFactor(new FactorDefinition("f_has_sensitive_word", "是否命中本地敏感词", FactorType.FIELD, "Boolean", null, "基于 DFA 状态机检测是否包含违规敏感词 (true/false)"));
        registerFactor(new FactorDefinition("f_sensitive_word", "命中的具体敏感词内容", FactorType.FIELD, "String", null, "DFA 匹配到的具体敏感词文本"));
        registerFactor(new FactorDefinition("f_has_phone_number", "输入包含手机号检测", FactorType.FIELD, "Boolean", null, "识别用户提问中是否包含大陆手机号 (true/false)"));
        registerFactor(new FactorDefinition("f_phone_number_count", "输入手机号总个数", FactorType.FIELD, "Long", null, "输入提问中包含的手机号数量，用于防爬虫洗数据"));
        registerFactor(new FactorDefinition("f_has_id_card", "输入包含身份证检测", FactorType.FIELD, "Boolean", null, "识别用户提问中是否包含18位二代身份证号码"));
        registerFactor(new FactorDefinition("f_masked_prompt", "动态脱敏后的 Prompt 文本", FactorType.FIELD, "String", null, "对手机号/身份证等脱敏后的干净文本"));
        registerFactor(new FactorDefinition("f_consumer_concurrency", "租户当前活跃并发长连接数", FactorType.FIELD, "Long", null, "该租户当前正在进行中的 SSE 长连接数量"));
        registerFactor(new FactorDefinition("f_consumer_tier", "租户 QoS 服务质量等级", FactorType.FIELD, "String", null, "租户分级标识 (VIP / STANDARD / FREE)"));

        // ==================== 5. 异步后置特征 (ASYNC) ====================
        registerFactor(new FactorDefinition("f_async_audit_log", "异步审计与计量落盘标记", FactorType.FIELD, "Boolean", null, "请求结束后触发异步 ESB/MQ 消息通知与计量持久化"));
    }

    public void registerFactor(FactorDefinition definition) {
        factorDefinitionMap.put(definition.getFactorCode(), definition);
    }

    public Collection<FactorDefinition> getAllFactors() {
        return factorDefinitionMap.values();
    }

    public FactorDefinition getFactor(String factorCode) {
        return factorDefinitionMap.get(factorCode);
    }

    public void removeFactor(String factorCode) {
        factorDefinitionMap.remove(factorCode);
    }

    /**
     * 提取当前规则所需的特征因子（支持高开销因子按需惰性求值）
     */
    public Map<String, Object> extractFactors(List<String> boundFactorCodes, Map<String, Object> context) {
        Map<String, Object> factorMap = new HashMap<>();
        if (boundFactorCodes == null || boundFactorCodes.isEmpty()) {
            return factorMap;
        }

        String prompt = (String) context.getOrDefault("f_user_prompt", "");

        for (String code : boundFactorCodes) {
            // 1. 如果 context 中已显式提供该特征，直接使用
            if (context.containsKey(code)) {
                factorMap.put(code, context.get(code));
                continue;
            }

            // 2. 惰性计算：阿里云绿网机审 (仅在规则显式绑定 f_aliyun_green_status 时触发远程调用)
            if ("f_aliyun_green_status".equals(code)) {
                log.debug("[*] [FactorEngine] 规则触发 f_aliyun_green_status 惰性计算...");
                ModerationResult greenRes = aliyunGreenSecurityService.checkContent(prompt);
                String status = greenRes.isPass() ? "pass" : greenRes.getRiskLabel();
                context.put("f_aliyun_green_status", status);
                context.put("f_aliyun_green_reason", greenRes.getRiskReason());
                factorMap.put(code, status);
                continue;
            }

            // 3. 惰性计算：DFA 本地敏感词
            if ("f_has_sensitive_word".equals(code) || "f_sensitive_word".equals(code)) {
                String hitWord = dfaSensitiveWordService.checkSensitiveWord(prompt);
                context.put("f_sensitive_word", hitWord);
                context.put("f_has_sensitive_word", hitWord != null);
                factorMap.put(code, "f_has_sensitive_word".equals(code) ? (hitWord != null) : hitWord);
                continue;
            }

            // 4. 惰性计算：数据脱敏与隐私识别
            if ("f_has_phone_number".equals(code)) {
                boolean hasPhone = dataMaskingGovernor.hasPhoneNumber(prompt);
                context.put(code, hasPhone);
                factorMap.put(code, hasPhone);
                continue;
            }
            if ("f_phone_number_count".equals(code)) {
                long phoneCount = dataMaskingGovernor.countPhoneNumbers(prompt);
                context.put(code, phoneCount);
                factorMap.put(code, phoneCount);
                continue;
            }
            if ("f_has_id_card".equals(code)) {
                boolean hasId = dataMaskingGovernor.hasIdCard(prompt);
                context.put(code, hasId);
                factorMap.put(code, hasId);
                continue;
            }
            if ("f_masked_prompt".equals(code)) {
                DataMaskingGovernor.MaskingResult maskRes = dataMaskingGovernor.mask(prompt, "MASK");
                context.put(code, maskRes.getMaskedText());
                factorMap.put(code, maskRes.getMaskedText());
                continue;
            }

            // 5. 黑名单默认特征
            if ("f_ip_blacklist".equals(code)) {
                factorMap.put(code, Arrays.asList("192.168.1.99", "10.0.0.1", "127.0.0.99"));
                continue;
            }

            // 6. 聚合类特征
            FactorDefinition def = factorDefinitionMap.get(code);
            if (def != null && def.getFactorType() == FactorType.AGGREGATION && def.getAggregatorCode() != null) {
                IFactorAggregator aggregator = aggregatorMap.get(def.getAggregatorCode());
                if (aggregator != null) {
                    Object val = aggregator.extractValue(code, context);
                    factorMap.put(code, val);
                    continue;
                }
            }
        }
        return factorMap;
    }

    public void asyncUpdateFactors(List<String> boundFactorCodes, Map<String, Object> context, long tokenUsage) {
        // 默认更新所有聚合类
        for (IFactorAggregator aggregator : aggregatorMap.values()) {
            try {
                aggregator.asyncUpdate(aggregator.getAggregatorCode(), context, tokenUsage);
            } catch (Exception e) {
                // ignore
            }
        }
    }
}
