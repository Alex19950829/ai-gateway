package com.chatling.engine.governance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 敏感数据与个人隐私脱敏治理器 (Data Masking Governor)
 * 在发往外部公有云大模型前，极速识别并脱敏手机号、身份证、邮箱、银行卡等个人隐私，
 * 并支持在流式返回给用户端时进行反向透明还原。
 */
@Component
public class DataMaskingGovernor {
    private static final Logger log = LoggerFactory.getLogger(DataMaskingGovernor.class);

    // 中国大陆手机号正则 (匹配前后非数字的 11 位手机号)
    private static final Pattern PHONE_PATTERN = Pattern.compile("(?<!\\d)(1[3-9]\\d{9})(?!\\d)");
    // 18 位二代身份证正则
    private static final Pattern ID_CARD_PATTERN = Pattern.compile("(?<!\\d)([1-9]\\d{5}(18|19|20)\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}[\\dXx])(?!\\d)");
    // 电子邮箱正则
    private static final Pattern EMAIL_PATTERN = Pattern.compile("(?i)[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}");
    // 银行卡号正则 (16-19 位纯数字)
    private static final Pattern BANK_CARD_PATTERN = Pattern.compile("(?<!\\d)([1-9]\\d{15,18})(?!\\d)");

    public static class MaskingResult {
        private final String maskedText;
        private final boolean containsSensitive;
        private final int phoneCount;
        private final boolean hasIdCard;
        private final boolean hasBankCard;
        private final Map<String, String> placeholderMapping;

        public MaskingResult(String maskedText, boolean containsSensitive, int phoneCount, boolean hasIdCard, boolean hasBankCard, Map<String, String> placeholderMapping) {
            this.maskedText = maskedText;
            this.containsSensitive = containsSensitive;
            this.phoneCount = phoneCount;
            this.hasIdCard = hasIdCard;
            this.hasBankCard = hasBankCard;
            this.placeholderMapping = placeholderMapping;
        }

        public String getMaskedText() { return maskedText; }
        public boolean isContainsSensitive() { return containsSensitive; }
        public int getPhoneCount() { return phoneCount; }
        public boolean isHasIdCard() { return hasIdCard; }
        public boolean isHasBankCard() { return hasBankCard; }
        public Map<String, String> getPlaceholderMapping() { return placeholderMapping; }
    }

    /**
     * 快速检查是否包含手机号
     */
    public boolean hasPhoneNumber(String text) {
        return text != null && PHONE_PATTERN.matcher(text).find();
    }

    /**
     * 统计文本中包含的手机号总数
     */
    public long countPhoneNumbers(String text) {
        if (text == null || text.isEmpty()) return 0L;
        Matcher m = PHONE_PATTERN.matcher(text);
        long count = 0L;
        while (m.find()) {
            count++;
        }
        return count;
    }

    /**
     * 检查是否包含身份证号
     */
    public boolean hasIdCard(String text) {
        return text != null && ID_CARD_PATTERN.matcher(text).find();
    }

    /**
     * 检查是否包含银行卡号
     */
    public boolean hasBankCard(String text) {
        return text != null && BANK_CARD_PATTERN.matcher(text).find();
    }

    /**
     * 执行隐私数据动态脱敏与占位符映射生成
     * @param text 原始输入文本
     * @param mode 脱敏模式: "MASK" (直接变138****5678), "PLACEHOLDER" (生成[PHONE_1]供反向还原)
     */
    public MaskingResult mask(String text, String mode) {
        if (text == null || text.trim().isEmpty()) {
            return new MaskingResult(text, false, 0, false, false, new HashMap<>());
        }

        Map<String, String> mapping = new HashMap<>();
        boolean contains = false;
        int phoneCount = 0;
        boolean hasId = false;
        boolean hasBank = false;

        String result = text;

        // 1. 手机号脱敏
        Matcher phoneMatcher = PHONE_PATTERN.matcher(result);
        StringBuffer phoneSb = new StringBuffer();
        int pIndex = 1;
        while (phoneMatcher.find()) {
            contains = true;
            phoneCount++;
            String rawPhone = phoneMatcher.group(1);
            String replacement;
            if ("PLACEHOLDER".equalsIgnoreCase(mode)) {
                replacement = "[PHONE_" + (pIndex++) + "]";
                mapping.put(replacement, rawPhone);
            } else {
                replacement = rawPhone.substring(0, 3) + "****" + rawPhone.substring(7);
            }
            phoneMatcher.appendReplacement(phoneSb, Matcher.quoteReplacement(replacement));
        }
        phoneMatcher.appendTail(phoneSb);
        result = phoneSb.toString();

        // 2. 身份证脱敏
        Matcher idMatcher = ID_CARD_PATTERN.matcher(result);
        StringBuffer idSb = new StringBuffer();
        int idIndex = 1;
        while (idMatcher.find()) {
            contains = true;
            hasId = true;
            String rawId = idMatcher.group(1);
            String replacement;
            if ("PLACEHOLDER".equalsIgnoreCase(mode)) {
                replacement = "[IDCARD_" + (idIndex++) + "]";
                mapping.put(replacement, rawId);
            } else {
                replacement = rawId.substring(0, 6) + "********" + rawId.substring(rawId.length() - 4);
            }
            idMatcher.appendReplacement(idSb, Matcher.quoteReplacement(replacement));
        }
        idMatcher.appendTail(idSb);
        result = idSb.toString();

        // 3. 邮箱脱敏
        Matcher emailMatcher = EMAIL_PATTERN.matcher(result);
        StringBuffer emailSb = new StringBuffer();
        int eIndex = 1;
        while (emailMatcher.find()) {
            contains = true;
            String rawEmail = emailMatcher.group(0);
            String replacement;
            if ("PLACEHOLDER".equalsIgnoreCase(mode)) {
                replacement = "[EMAIL_" + (eIndex++) + "]";
                mapping.put(replacement, rawEmail);
            } else {
                int atIdx = rawEmail.indexOf('@');
                if (atIdx > 1) {
                    replacement = rawEmail.charAt(0) + "***" + rawEmail.substring(atIdx);
                } else {
                    replacement = "***" + rawEmail.substring(atIdx);
                }
            }
            emailMatcher.appendReplacement(emailSb, Matcher.quoteReplacement(replacement));
        }
        emailMatcher.appendTail(emailSb);
        result = emailSb.toString();

        // 4. 银行卡脱敏
        Matcher bankMatcher = BANK_CARD_PATTERN.matcher(result);
        StringBuffer bankSb = new StringBuffer();
        int bIndex = 1;
        while (bankMatcher.find()) {
            contains = true;
            hasBank = true;
            String rawBank = bankMatcher.group(1);
            String replacement;
            if ("PLACEHOLDER".equalsIgnoreCase(mode)) {
                replacement = "[BANKCARD_" + (bIndex++) + "]";
                mapping.put(replacement, rawBank);
            } else {
                replacement = rawBank.substring(0, 4) + "********" + rawBank.substring(rawBank.length() - 4);
            }
            bankMatcher.appendReplacement(bankSb, Matcher.quoteReplacement(replacement));
        }
        bankMatcher.appendTail(bankSb);
        result = bankSb.toString();

        if (contains) {
            log.info("[*] [DataMasking] Applied masking: phones={}, hasId={}, hasBank={}, mode={}", phoneCount, hasId, hasBank, mode);
        }

        return new MaskingResult(result, contains, phoneCount, hasId, hasBank, mapping);
    }

    /**
     * 将大模型输出中包含的占位符反向还原为用户的真实数据
     */
    public String unmask(String text, Map<String, String> mapping) {
        if (text == null || mapping == null || mapping.isEmpty()) {
            return text;
        }
        String unmasked = text;
        for (Map.Entry<String, String> entry : mapping.entrySet()) {
            unmasked = unmasked.replace(entry.getKey(), entry.getValue());
        }
        return unmasked;
    }
}
