package com.chatling.engine.factor;

import com.chatling.engine.factor.impl.*;
import com.chatling.engine.governance.DataMaskingGovernor;
import com.chatling.engine.security.AliyunGreenSecurityService;
import com.chatling.engine.security.DfaSensitiveWordService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.*;

public class FactorEngineTest {

    private FactorEngine factorEngine;

    @BeforeEach
    public void setUp() {
        factorEngine = new FactorEngine();
        // 手动注入 5 大聚合类
        List<com.chatling.common.factor.IFactorAggregator> list = Arrays.asList(
                new SlidingWindowCountAggregator(),
                new SlidingWindowTokenAggregator(),
                new AtomicCounterAggregator(),
                new TokenQuotaDecrementAggregator(),
                new DistinctIpAggregator()
        );
        try {
            setField(factorEngine, "aggregators", list);
            setField(factorEngine, "aliyunGreenSecurityService", new AliyunGreenSecurityService());
            setField(factorEngine, "dataMaskingGovernor", new DataMaskingGovernor());
            setField(factorEngine, "dfaSensitiveWordService", new DfaSensitiveWordService());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        factorEngine.init();
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    public void testFactorExtractionAndAsyncUpdate() {
        Map<String, Object> context = new HashMap<>();
        context.put("f_consumer_id", "wiabao-team");
        context.put("f_client_ip", "192.168.1.100");
        context.put("f_user_prompt", "请写一个Java快速排序算法");

        List<String> boundCodes = Arrays.asList(
                "f_consumer_id",
                "f_user_prompt",
                "f_minute_req_cnt",
                "f_minute_token_cnt",
                "f_total_invoke_cnt",
                "f_account_balance",
                "f_distinct_ip_cnt"
        );

        // 1. 初次提取
        Map<String, Object> factors = factorEngine.extractFactors(boundCodes, context);
        Assertions.assertEquals("wiabao-team", factors.get("f_consumer_id"));
        Assertions.assertEquals("请写一个Java快速排序算法", factors.get("f_user_prompt"));
        Assertions.assertEquals(0L, factors.get("f_minute_req_cnt"));
        Assertions.assertEquals(0L, factors.get("f_minute_token_cnt"));
        Assertions.assertEquals(5_000_000L, factors.get("f_account_balance"));

        // 2. 模拟请求完成，消耗 150 Token 异步回写
        factorEngine.asyncUpdateFactors(boundCodes, context, 150L);

        // 3. 再次提取，验证聚合计算生效
        Map<String, Object> afterFactors = factorEngine.extractFactors(boundCodes, context);
        Assertions.assertEquals(1L, afterFactors.get("f_minute_req_cnt"));
        Assertions.assertEquals(150L, afterFactors.get("f_minute_token_cnt"));
        Assertions.assertEquals(1L, afterFactors.get("f_total_invoke_cnt"));
        Assertions.assertEquals(4_999_850L, afterFactors.get("f_account_balance"));
        Assertions.assertEquals(1L, afterFactors.get("f_distinct_ip_cnt"));
    }

    @Test
    public void testLazySecurityFactorExtraction() {
        Map<String, Object> context = new HashMap<>();
        context.put("f_user_prompt", "请拨打客户电话 13812345678 询问是否有高利贷需求");

        List<String> boundCodes = Arrays.asList(
                "f_has_phone_number",
                "f_phone_number_count",
                "f_masked_prompt",
                "f_has_sensitive_word",
                "f_sensitive_word",
                "f_aliyun_green_status"
        );

        Map<String, Object> factors = factorEngine.extractFactors(boundCodes, context);
        Assertions.assertEquals(true, factors.get("f_has_phone_number"));
        Assertions.assertEquals(1L, factors.get("f_phone_number_count"));
        Assertions.assertTrue(((String) factors.get("f_masked_prompt")).contains("138****5678"));
        Assertions.assertEquals(true, factors.get("f_has_sensitive_word"));
        Assertions.assertEquals("高利贷", factors.get("f_sensitive_word"));
    }
}
