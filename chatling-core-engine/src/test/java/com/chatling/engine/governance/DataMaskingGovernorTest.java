package com.chatling.engine.governance;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class DataMaskingGovernorTest {

    private final DataMaskingGovernor governor = new DataMaskingGovernor();

    @Test
    public void testPhoneAndIdCardMasking() {
        String text = "请帮我联系客户张三，手机号是 13812345678，身份证号码为 110101199003072345，邮箱是 test@58.com";

        assertTrue(governor.hasPhoneNumber(text));
        assertEquals(1, governor.countPhoneNumbers(text));
        assertTrue(governor.hasIdCard(text));

        // 1. 直接打码模式
        DataMaskingGovernor.MaskingResult maskResult = governor.mask(text, "MASK");
        assertTrue(maskResult.isContainsSensitive());
        assertEquals(1, maskResult.getPhoneCount());
        assertTrue(maskResult.isHasIdCard());
        assertTrue(maskResult.getMaskedText().contains("138****5678"));
        assertTrue(maskResult.getMaskedText().contains("110101********2345"));
        assertTrue(maskResult.getMaskedText().contains("t***@58.com"));

        // 2. 占位符 + 反向透明还原模式
        DataMaskingGovernor.MaskingResult placeholderResult = governor.mask(text, "PLACEHOLDER");
        assertTrue(placeholderResult.getMaskedText().contains("[PHONE_1]"));
        assertTrue(placeholderResult.getMaskedText().contains("[IDCARD_1]"));

        // 模拟大模型输出并反向还原
        String llmOutput = "好的，已经为您给客户张三（手机号：[PHONE_1]，身份证：[IDCARD_1]）准备好邮件。";
        String unmasked = governor.unmask(llmOutput, placeholderResult.getPlaceholderMapping());
        assertTrue(unmasked.contains("13812345678"));
        assertTrue(unmasked.contains("110101199003072345"));
        assertFalse(unmasked.contains("[PHONE_1]"));
    }
}
