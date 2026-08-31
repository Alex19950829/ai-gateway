package com.chatling.engine.cache;

import com.alibaba.fastjson2.JSON;
import com.chatling.common.dto.OpenAiDto;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 精准 Prompt 哈希缓存加速器 (Exact Prompt Cache)
 * 对相同 Prompt 提问直接由网关毫秒级流式回放，Token 消耗记为 0，极大降低算力成本与延迟
 */
@Service
public class PromptCacheService {
    private static final Logger log = LoggerFactory.getLogger(PromptCacheService.class);

    // 内存缓存: hashKey -> 完整的模型响应文本内容
    private final Cache<String, CachedResponse> cache = Caffeine.newBuilder()
            .expireAfterWrite(30, TimeUnit.MINUTES) // 缓存 30 分钟
            .maximumSize(10000)
            .build();

    public static class CachedResponse {
        private final String fullText;
        private final int promptTokens;
        private final int completionTokens;

        public CachedResponse(String fullText, int promptTokens, int completionTokens) {
            this.fullText = fullText;
            this.promptTokens = promptTokens;
            this.completionTokens = completionTokens;
        }

        public String getFullText() { return fullText; }
        public int getPromptTokens() { return promptTokens; }
        public int getCompletionTokens() { return completionTokens; }
    }

    /**
     * 计算 model + prompt SHA-256 哈希
     */
    public String calculateHash(String model, String prompt) {
        if (prompt == null || prompt.trim().isEmpty()) {
            return null;
        }
        String content = (model != null ? model : "default") + "|" + prompt;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            return String.valueOf(content.hashCode());
        }
    }

    /**
     * 计算 model + messages 的 SHA-256 哈希
     */
    public String calculateHash(String model, List<OpenAiDto.ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder(model != null ? model : "default");
        for (OpenAiDto.ChatMessage msg : messages) {
            sb.append("|").append(msg.getRole()).append(":").append(msg.getContent());
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            return String.valueOf(sb.toString().hashCode());
        }
    }

    public CachedResponse get(String hashKey) {
        if (hashKey == null) return null;
        return cache.getIfPresent(hashKey);
    }

    public void put(String hashKey, String fullText, int promptTokens, int completionTokens) {
        if (hashKey != null && fullText != null && !fullText.trim().isEmpty()) {
            cache.put(hashKey, new CachedResponse(fullText, promptTokens, completionTokens));
            log.info("Cached prompt response for hash: {}, length: {}", hashKey.substring(0, Math.min(8, hashKey.length())), fullText.length());
        }
    }

    /**
     * 针对命中的缓存，构建极速打字机流式输出 (每 15ms 输出一个词，TTFT < 20ms)
     */
    public Flux<OpenAiDto.ChatCompletionChunk> createCachedStream(String model, String fullText) {
        String reqId = "chatcmpl-cache-" + UUID.randomUUID().toString().substring(0, 8);
        long created = System.currentTimeMillis() / 1000;
        List<String> words = splitIntoWords(fullText);
        List<OpenAiDto.ChatCompletionChunk> chunks = new ArrayList<>();
        for (int i = 0; i < words.size(); i++) {
            boolean isLast = (i == words.size() - 1);
            chunks.add(OpenAiDto.ChatCompletionChunk.builder()
                    .id(reqId)
                    .object("chat.completion.chunk")
                    .created(created)
                    .model(model)
                    .choices(Collections.singletonList(
                            OpenAiDto.ChunkChoice.builder()
                                    .index(0)
                                    .delta(OpenAiDto.Delta.builder().content(words.get(i)).build())
                                    .finishReason(isLast ? "stop" : null)
                                    .build()
                    ))
                    .build());
        }

        return Flux.fromIterable(chunks)
                .delayElements(Duration.ofMillis(15))
                .onBackpressureBuffer();
    }

    private List<String> splitIntoWords(String text) {
        List<String> list = new ArrayList<>();
        int len = text.length();
        int step = 3;
        for (int i = 0; i < len; i += step) {
            int end = Math.min(i + step, len);
            list.add(text.substring(i, end));
        }
        return list;
    }
}
