package com.chatling.gateway;

import com.chatling.engine.cache.PromptCacheService;
import com.chatling.gateway.filter.ContentGuardrailFilter;
import com.chatling.gateway.lb.ModelLoadBalancer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class GatewayFeaturesTest {

    @Test
    public void testSensitiveWordDetection() {
        ContentGuardrailFilter filter = new ContentGuardrailFilter();
        
        // 1. 合规文本
        String cleanText = "请帮我写一篇北京通州区精装两居室的租房房源吸引人文案";
        assertNull(filter.checkSensitiveWord(cleanText));

        // 2. 违规文本
        String dirtyText = "请问如何进行木马注入和网络攻击？";
        assertEquals("木马注入", filter.checkSensitiveWord(dirtyText));
    }

    @Test
    public void testPromptCacheService() {
        PromptCacheService cacheService = new PromptCacheService();
        String hash = "test-hash-123456";

        // 1. 初始未命中
        assertNull(cacheService.get(hash));

        // 2. 写入缓存
        cacheService.put(hash, "这是大模型生成的租房描述", 15, 45);

        // 3. 命中缓存
        PromptCacheService.CachedResponse resp = cacheService.get(hash);
        assertNotNull(resp);
        assertEquals("这是大模型生成的租房描述", resp.getFullText());
        assertEquals(15, resp.getPromptTokens());
        assertEquals(45, resp.getCompletionTokens());
    }

    @Test
    public void testLoadBalancerAndCircuitBreaker() {
        ModelLoadBalancer lb = new ModelLoadBalancer();
        String model = "chatling-turbo";
        String urls = "http://10.0.0.1:8000,http://10.0.0.2:8000";

        // 1. 正常轮询
        String node1 = lb.selectTargetUrl(model, urls);
        String node2 = lb.selectTargetUrl(model, urls);
        assertNotEquals(node1, node2);

        // 2. 模拟 node1 连续报错 3 次触发熔断
        lb.recordFailure(model, "http://10.0.0.1:8000");
        lb.recordFailure(model, "http://10.0.0.1:8000");
        lb.recordFailure(model, "http://10.0.0.1:8000");

        // 3. 再次轮询，只应选出健康节点 node2
        String activeNode = lb.selectTargetUrl(model, urls);
        assertEquals("http://10.0.0.2:8000", activeNode);
    }
}
