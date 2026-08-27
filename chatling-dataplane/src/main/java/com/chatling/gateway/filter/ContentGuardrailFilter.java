package com.chatling.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 敏感词与合规安全过滤器（基于 DFA 确定有穷自动机状态机算法，毫秒级快速匹配）
 */
@Slf4j
@Component
public class ContentGuardrailFilter {

    private final Map<Character, Object> rootNode = new HashMap<>();

    // 默认内置违规敏感词库（生产环境可对接公司合规词库表）
    private static final List<String> DEFAULT_SENSITIVE_WORDS = Arrays.asList(
            "赌博", "高利贷", "暴恐", "洗钱", "发票代开", "违禁品", "翻墙教程", "木马注入", "sql注入攻击"
    );

    public ContentGuardrailFilter() {
        initWordTree(DEFAULT_SENSITIVE_WORDS);
    }

    /**
     * 构建 DFA 前缀树
     */
    @SuppressWarnings("unchecked")
    public synchronized void initWordTree(Collection<String> words) {
        rootNode.clear();
        for (String word : words) {
            if (word == null || word.trim().isEmpty()) continue;
            Map<Character, Object> current = rootNode;
            char[] chars = word.trim().toCharArray();
            for (int i = 0; i < chars.length; i++) {
                char c = Character.toLowerCase(chars[i]);
                Map<Character, Object> sub = (Map<Character, Object>) current.get(c);
                if (sub == null) {
                    sub = new HashMap<>();
                    current.put(c, sub);
                }
                current = sub;
                if (i == chars.length - 1) {
                    current.put('\0', Boolean.TRUE); // 结束标记
                }
            }
        }
        log.info("Initialized ContentGuardrailFilter with {} sensitive keywords.", words.size());
    }

    /**
     * 校验文本是否包含敏感词
     * @return 命中的敏感词，若合规返回 null
     */
    @SuppressWarnings("unchecked")
    public String checkSensitiveWord(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        char[] chars = text.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            Map<Character, Object> current = rootNode;
            int matchLength = 0;
            for (int j = i; j < chars.length; j++) {
                char c = Character.toLowerCase(chars[j]);
                current = (Map<Character, Object>) current.get(c);
                if (current == null) {
                    break;
                }
                matchLength++;
                if (current.containsKey('\0')) {
                    // 命中敏感词
                    return text.substring(i, i + matchLength);
                }
            }
        }
        return null;
    }
}
