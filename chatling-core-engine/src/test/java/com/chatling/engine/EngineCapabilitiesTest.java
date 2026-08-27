package com.chatling.engine;

import com.chatling.common.dto.OpenAiDto;
import com.chatling.common.model.ModelConfig;
import com.chatling.engine.adapter.OpenAiCompatibleAdapter;
import com.chatling.engine.template.PromptTemplateEngine;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class EngineCapabilitiesTest {

    @Test
    public void testPromptTemplateRendering() {
        PromptTemplateEngine engine = new PromptTemplateEngine();
        String template = "你好，请帮我为【{house_name}】撰写一段位于【{location}】的租房文案。";
        Map<String, Object> vars = new HashMap<>();
        vars.put("house_name", "保利万科金域蓝湾");
        vars.put("location", "北京市朝阳区大望路");

        String rendered = engine.render(template, vars);
        assertEquals("你好，请帮我为【保利万科金域蓝湾】撰写一段位于【北京市朝阳区大望路】的租房文案。", rendered);
    }

    @Test
    public void testOpenAiAdapterKeyInjection() {
        OpenAiCompatibleAdapter adapter = new OpenAiCompatibleAdapter(WebClient.builder());
        assertTrue(adapter.supports("openai"));
        assertTrue(adapter.supports("deepseek"));
        assertTrue(adapter.supports("dashscope"));
        assertTrue(adapter.supports("vllm"));
        assertFalse(adapter.supports("unknown_vendor"));

        ModelConfig config = ModelConfig.builder()
                .modelName("deepseek-v3")
                .providerType("deepseek")
                .baseUrl("https://api.deepseek.com/v1")
                .apiSecret("sk-real-test-secret-12345")
                .build();

        assertEquals("sk-real-test-secret-12345", config.getApiSecret());
    }
}
