package com.chatling.engine.template;

import com.chatling.common.dto.OpenAiDto;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 核心 Prompt 模板渲染与变量动态替换引擎
 */
@Component
public class PromptTemplateEngine {

    /**
     * 渲染替换字符串中的 {variable} 占位符
     */
    public String render(String template, Map<String, Object> variables) {
        if (template == null || variables == null || variables.isEmpty()) {
            return template;
        }
        String result = template;
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            String placeholder = "{" + entry.getKey() + "}";
            String value = entry.getValue() != null ? entry.getValue().toString() : "";
            result = result.replace(placeholder, value);
        }
        return result;
    }

    /**
     * 对 ChatCompletionRequest 中的所有 Messages 进行变量渲染
     */
    public OpenAiDto.ChatCompletionRequest renderRequest(OpenAiDto.ChatCompletionRequest request, Map<String, Object> variables) {
        if (request == null || request.getMessages() == null || variables == null || variables.isEmpty()) {
            return request;
        }
        List<OpenAiDto.ChatMessage> renderedMessages = new ArrayList<>();
        for (OpenAiDto.ChatMessage msg : request.getMessages()) {
            String content = render(msg.getContent(), variables);
            renderedMessages.add(OpenAiDto.ChatMessage.builder()
                    .role(msg.getRole())
                    .content(content)
                    .build());
        }
        request.setMessages(renderedMessages);
        return request;
    }
}
