package com.chatling.engine.rule;

import com.chatling.common.rule.BaseRuleExecutor;
import com.chatling.common.rule.RuleDecision;
import com.chatling.common.rule.RuleDefinition;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import groovy.lang.GroovyClassLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
public class RuleExecutorManager {
    private static final Logger log = LoggerFactory.getLogger(RuleExecutorManager.class);
    private static final GroovyClassLoader GROOVY_CLASS_LOADER = new GroovyClassLoader();

    private static final Cache<String, BaseRuleExecutor> RULE_INSTANCE_CACHE = Caffeine.newBuilder()
            .maximumSize(1024)
            .expireAfterAccess(2, TimeUnit.HOURS)
            .build();

    private final Map<String, RuleDefinition> ruleDefinitionMap = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        ruleDefinitionMap.clear();

        // 1. 定制化 QPM 限流规则 (绑定 f_custom_qpm + f_minute_req_cnt)
        registerRule(new RuleDefinition(
                "rule_dynamic_qpm_limit",
                "租户定制化 QPM 滑动窗口限流",
                Arrays.asList("f_custom_qpm", "f_minute_req_cnt"),
                "package com.chatling.gateway.rule.executor\n" +
                "import com.chatling.common.rule.BaseRuleExecutor\n" +
                "import com.chatling.common.rule.RuleDecision\n" +
                "\n" +
                "class DynamicQpmRuleExecutor extends BaseRuleExecutor {\n" +
                "    @Override\n" +
                "    RuleDecision executeRule(Map<String, Object> factor) {\n" +
                "        long currentCount = (Long) factor.getOrDefault('f_minute_req_cnt', 0L)\n" +
                "        long maxQpm = (Long) factor.getOrDefault('f_custom_qpm', 60L)\n" +
                "        if (currentCount >= maxQpm) {\n" +
                "            return RuleDecision.reject(\"您的专属 QPM 限额 [\" + maxQpm + \"] 已耗尽，当前 1 分钟内已调用 [\" + currentCount + \"] 次！\")\n" +
                "        }\n" +
                "        return RuleDecision.pass()\n" +
                "    }\n" +
                "}",
                "admin",
                1
        ));

        // 2. DFA 敏感词极速过滤规则 (绑定 f_has_sensitive_word + f_sensitive_word)
        registerRule(new RuleDefinition(
                "rule_dfa_sensitive_filter",
                "DFA 本地敏感词极速合规拦截",
                Arrays.asList("f_has_sensitive_word", "f_sensitive_word"),
                "package com.chatling.gateway.rule.executor\n" +
                "import com.chatling.common.rule.BaseRuleExecutor\n" +
                "import com.chatling.common.rule.RuleDecision\n" +
                "\n" +
                "class DfaSensitiveRuleExecutor extends BaseRuleExecutor {\n" +
                "    @Override\n" +
                "    RuleDecision executeRule(Map<String, Object> factor) {\n" +
                "        boolean hasSensitive = (Boolean) factor.getOrDefault('f_has_sensitive_word', false)\n" +
                "        String word = (String) factor.getOrDefault('f_sensitive_word', '')\n" +
                "        if (hasSensitive) {\n" +
                "            return RuleDecision.reject(\"包含合规违规敏感词 [\" + word + \"]，已被拦截\")\n" +
                "        }\n" +
                "        return RuleDecision.pass()\n" +
                "    }\n" +
                "}",
                "admin",
                1
        ));

        // 3. 动态数据脱敏与隐私改写规则 (绑定 f_has_phone_number + f_has_id_card + f_masked_prompt)
        registerRule(new RuleDefinition(
                "rule_data_masking",
                "个人隐私与敏感数据动态脱敏改写",
                Arrays.asList("f_has_phone_number", "f_has_id_card", "f_masked_prompt"),
                "package com.chatling.gateway.rule.executor\n" +
                "import com.chatling.common.rule.BaseRuleExecutor\n" +
                "import com.chatling.common.rule.RuleDecision\n" +
                "\n" +
                "class DataMaskingRuleExecutor extends BaseRuleExecutor {\n" +
                "    @Override\n" +
                "    RuleDecision executeRule(Map<String, Object> factor) {\n" +
                "        boolean hasPhone = (Boolean) factor.getOrDefault('f_has_phone_number', false)\n" +
                "        boolean hasId = (Boolean) factor.getOrDefault('f_has_id_card', false)\n" +
                "        String maskedPrompt = (String) factor.getOrDefault('f_masked_prompt', '')\n" +
                "        if (hasPhone || hasId) {\n" +
                "            return RuleDecision.mask(maskedPrompt, \"已自动执行个人敏感隐私脱敏处理\")\n" +
                "        }\n" +
                "        return RuleDecision.pass()\n" +
                "    }\n" +
                "}",
                "admin",
                1
        ));

        // 4. IP 黑名单拦截规则 (绑定 f_ip_blacklist + f_client_ip)
        registerRule(new RuleDefinition(
                "rule_ip_blacklist_security",
                "高危恶意 IP 黑名单拦截",
                Arrays.asList("f_ip_blacklist", "f_client_ip"),
                "package com.chatling.gateway.rule.executor\n" +
                "import com.chatling.common.rule.BaseRuleExecutor\n" +
                "import com.chatling.common.rule.RuleDecision\n" +
                "\n" +
                "class IpBlacklistRuleExecutor extends BaseRuleExecutor {\n" +
                "    @Override\n" +
                "    RuleDecision executeRule(Map<String, Object> factor) {\n" +
                "        String clientIp = (String) factor.getOrDefault('f_client_ip', '')\n" +
                "        List<String> blacklist = (List<String>) factor.getOrDefault('f_ip_blacklist', ['192.168.1.99', '127.0.0.99'])\n" +
                "        if (blacklist.contains(clientIp)) {\n" +
                "            return RuleDecision.reject(\"安全拦截：客户端 IP [\" + clientIp + \"] 处于风险封禁名单中！\")\n" +
                "        }\n" +
                "        return RuleDecision.pass()\n" +
                "    }\n" +
                "}",
                "admin",
                1
        ));

        // 5. Prompt 越狱与恶意注入拦截 (绑定 f_user_prompt)
        registerRule(new RuleDefinition(
                "rule_prompt_jailbreak_security",
                "Prompt 越狱与恶意注入拦截",
                Collections.singletonList("f_user_prompt"),
                "package com.chatling.gateway.rule.executor\n" +
                "import com.chatling.common.rule.BaseRuleExecutor\n" +
                "import com.chatling.common.rule.RuleDecision\n" +
                "\n" +
                "class JailbreakSecurityRuleExecutor extends BaseRuleExecutor {\n" +
                "    @Override\n" +
                "    RuleDecision executeRule(Map<String, Object> factor) {\n" +
                "        String prompt = (String) factor.get('f_user_prompt')\n" +
                "        if (prompt != null && (prompt.contains('忽略之前的指令') || prompt.contains('DAN模式') || prompt.contains('没有道德限制'))) {\n" +
                "            return RuleDecision.reject('安全拦截：检测到恶意 Prompt 注入或越狱攻击！')\n" +
                "        }\n" +
                "        return RuleDecision.pass()\n" +
                "    }\n" +
                "}",
                "admin",
                1
        ));

        // 6. 阿里云绿网 2.0 内容安全机审规则 (绑定 f_aliyun_green_status + f_user_prompt)
        registerRule(new RuleDefinition(
                "rule_aliyun_green_security",
                "阿里云绿网 2.0 内容安全机审拦截",
                Arrays.asList("f_aliyun_green_status", "f_user_prompt"),
                "package com.chatling.gateway.rule.executor\n" +
                "import com.chatling.common.rule.BaseRuleExecutor\n" +
                "import com.chatling.common.rule.RuleDecision\n" +
                "\n" +
                "class AliyunGreenSecurityRuleExecutor extends BaseRuleExecutor {\n" +
                "    @Override\n" +
                "    RuleDecision executeRule(Map<String, Object> factor) {\n" +
                "        String greenStatus = (String) factor.getOrDefault('f_aliyun_green_status', 'pass')\n" +
                "        String prompt = (String) factor.getOrDefault('f_user_prompt', '')\n" +
                "\n" +
                "        if ('political'.equalsIgnoreCase(greenStatus)) {\n" +
                "            return RuleDecision.reject('阿里云绿网拦截：检测到涉政高危敏感内容，严禁传播！')\n" +
                "        }\n" +
                "        if ('violence'.equalsIgnoreCase(greenStatus)) {\n" +
                "            return RuleDecision.reject('阿里云绿网拦截：检测到涉暴恐或高危违禁品交易信息！')\n" +
                "        }\n" +
                "        if ('porn'.equalsIgnoreCase(greenStatus)) {\n" +
                "            return RuleDecision.reject('阿里云绿网拦截：检测到低俗色情违规内容！')\n" +
                "        }\n" +
                "        if ('contraband'.equalsIgnoreCase(greenStatus)) {\n" +
                "            return RuleDecision.reject('阿里云绿网拦截：检测到涉及管制违禁物品信息！')\n" +
                "        }\n" +
                "\n" +
                "        if (prompt != null && (prompt.contains('自制炸药') || prompt.contains('恐怖袭击') || prompt.contains('邪教宣传'))) {\n" +
                "            return RuleDecision.reject('阿里云绿网拦截：触发高危涉暴/涉政机审风控策略！')\n" +
                "        }\n" +
                "        return RuleDecision.pass()\n" +
                "    }\n" +
                "}",
                "admin",
                1
        ));
    }

    public void registerRule(RuleDefinition definition) {
        ruleDefinitionMap.put(definition.getRuleCode(), definition);
    }

    public Collection<RuleDefinition> getAllRules() {
        return ruleDefinitionMap.values();
    }

    public RuleDefinition getRule(String ruleCode) {
        return ruleDefinitionMap.get(ruleCode);
    }

    public void removeRule(String ruleCode) {
        ruleDefinitionMap.remove(ruleCode);
    }

    public BaseRuleExecutor getOrCompileExecutor(RuleDefinition definition) {
        String script = definition.getGroovyScript();
        String cacheKey = definition.getRuleCode() + "_" + md5(script);

        BaseRuleExecutor executor = RULE_INSTANCE_CACHE.getIfPresent(cacheKey);
        if (executor != null) {
            return executor;
        }

        synchronized (this) {
            executor = RULE_INSTANCE_CACHE.getIfPresent(cacheKey);
            if (executor != null) {
                return executor;
            }
            try {
                Class<?> clazz = GROOVY_CLASS_LOADER.parseClass(script);
                executor = (BaseRuleExecutor) clazz.getDeclaredConstructor().newInstance();
                RULE_INSTANCE_CACHE.put(cacheKey, executor);
                log.info("成功动态编译并缓存 Groovy 规则实例: {}", cacheKey);
                return executor;
            } catch (Exception e) {
                log.error("编译 Groovy 规则脚本失败: {}", definition.getRuleCode(), e);
                throw new RuntimeException("Groovy 规则编译异常: " + e.getMessage(), e);
            }
        }
    }

    public RuleDecision executeRule(String ruleCode, Map<String, Object> factor) {
        RuleDefinition def = ruleDefinitionMap.get(ruleCode);
        if (def == null || def.getStatus() == 0) {
            return RuleDecision.pass();
        }
        BaseRuleExecutor executor = getOrCompileExecutor(def);
        return executor.executeRule(factor);
    }

    private static String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(input.hashCode());
        }
    }
}
