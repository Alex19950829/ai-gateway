package com.chatling.engine.governance;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class JsonFormatGovernorTest {

    private final JsonFormatGovernor governor = new JsonFormatGovernor();

    @Test
    public void testStripMarkdownJsonWrapper() {
        String raw = "好的，这是你要的JSON数据：\n```json\n{\n  \"name\": \"Alex\",\n  \"role\": \"Engineer\"\n}\n```\n希望对你有帮助！";
        String clean = governor.sanitizeAndEnforceJson(raw);
        Assertions.assertTrue(clean.startsWith("{"));
        Assertions.assertTrue(clean.endsWith("}"));
        Assertions.assertFalse(clean.contains("```"));
    }

    @Test
    public void testFixDanglingBracket() {
        String broken = "{\"user\": \"zhangsan\", \"status\": \"ACTIVE\""; // 缺少尾部 }
        String fixed = governor.sanitizeAndEnforceJson(broken);
        Assertions.assertTrue(fixed.endsWith("}"));
    }
}
