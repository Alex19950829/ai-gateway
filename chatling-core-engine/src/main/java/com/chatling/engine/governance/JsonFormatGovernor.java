package com.chatling.engine.governance;

import com.alibaba.fastjson2.JSON;
import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class JsonFormatGovernor {

    private static final Pattern MARKDOWN_JSON_PATTERN = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)\\s*```", Pattern.CASE_INSENSITIVE);

    /**
     * 后置净化与结构化自愈：
     * 1. 自动剔除 ```json ... ``` 外层 Markdown 标记
     * 2. 自动剔除首尾的废话或非 JSON 字符
     * 3. 校验并确保返回严格合规的 JSON 字符串
     */
    public String sanitizeAndEnforceJson(String rawResponse) {
        if (rawResponse == null || rawResponse.trim().isEmpty()) {
            return "{}";
        }
        String content = rawResponse.trim();

        // 1. 尝试匹配 ```json ... ``` 代码块
        Matcher matcher = MARKDOWN_JSON_PATTERN.matcher(content);
        if (matcher.find()) {
            content = matcher.group(1).trim();
        }

        // 2. 如果包含前置或后置杂质文本，尝试截取最外层 { ... } 或 [ ... ]
        int firstBrace = content.indexOf('{');
        int firstBracket = content.indexOf('[');
        int start = -1;
        int end = -1;

        if (firstBrace != -1 && (firstBracket == -1 || firstBrace < firstBracket)) {
            start = firstBrace;
            end = content.lastIndexOf('}');
        } else if (firstBracket != -1) {
            start = firstBracket;
            end = content.lastIndexOf(']');
        }

        if (start != -1 && end != -1 && end > start) {
            content = content.substring(start, end + 1).trim();
        }

        // 3. 验证 JSON 格式合法性
        try {
            JSON.parse(content);
            return content;
        } catch (Exception e) {
            // 若尾部缺失括号导致解析失败，尝试自动补全修复
            if (content.startsWith("{") && !content.endsWith("}")) {
                content = content + "}";
            } else if (content.startsWith("[") && !content.endsWith("]")) {
                content = content + "]";
            }
            try {
                JSON.parse(content);
                return content;
            } catch (Exception ex) {
                // 实在无法自愈时包装为标准 JSON 返回
                return JSON.toJSONString(java.util.Collections.singletonMap("raw_text", rawResponse));
            }
        }
    }
}
