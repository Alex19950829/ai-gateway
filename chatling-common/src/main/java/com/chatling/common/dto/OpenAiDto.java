package com.chatling.common.dto;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

public class OpenAiDto {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatCompletionRequest {
        private String model;
        private List<ChatMessage> messages;
        private Boolean stream;
        private Double temperature;
        @JSONField(name = "top_p")
        private Double topP;
        @JSONField(name = "max_tokens")
        private Integer maxTokens;
        @JSONField(name = "response_format")
        private ResponseFormat responseFormat;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResponseFormat {
        private String type; // text, json_object
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatMessage {
        private String role;
        private String content;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatCompletionResponse {
        private String id;
        private String object;
        private Long created;
        private String model;
        private List<Choice> choices;
        private Usage usage;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Choice {
        private Integer index;
        private ChatMessage message;
        @JSONField(name = "finish_reason")
        private String finishReason;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Usage {
        @JSONField(name = "prompt_tokens")
        private Integer promptTokens;
        @JSONField(name = "completion_tokens")
        private Integer completionTokens;
        @JSONField(name = "total_tokens")
        private Integer totalTokens;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatCompletionChunk {
        private String id;
        private String object;
        private Long created;
        private String model;
        private List<ChunkChoice> choices;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChunkChoice {
        private Integer index;
        private Delta delta;
        @JSONField(name = "finish_reason")
        private String finishReason;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Delta {
        private String role;
        private String content;
    }
}
