package com.chatling.engine.rag;

import com.chatling.common.dto.OpenAiDto;
import com.chatling.common.rag.KnowledgeDoc;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

public class RagKnowledgeServiceTest {

    private RagKnowledgeService ragService;

    @BeforeEach
    public void setUp() {
        ragService = new RagKnowledgeService();
        ragService.init();
    }

    @Test
    public void testSearchTopK() {
        List<KnowledgeDoc> docs = ragService.searchTopK("请问公司年假和休假几天？", 2);
        Assertions.assertFalse(docs.isEmpty());
        Assertions.assertEquals("2026年员工休假管理制度", docs.get(0).getTitle());
    }

    @Test
    public void testAugmentMessages() {
        OpenAiDto.ChatMessage userMsg = OpenAiDto.ChatMessage.builder()
                .role("user")
                .content("我想了解一下网关的限流规范")
                .build();
        List<OpenAiDto.ChatMessage> result = ragService.augmentMessagesWithKnowledge(Collections.singletonList(userMsg), userMsg.getContent());
        Assertions.assertEquals(2, result.size());
        Assertions.assertEquals("system", result.get(0).getRole());
        Assertions.assertTrue(result.get(0).getContent().contains("灵犀 AI 网关架构与限流规范"));
    }
}
