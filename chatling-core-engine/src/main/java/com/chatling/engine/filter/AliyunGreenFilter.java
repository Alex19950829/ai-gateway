package com.chatling.engine.filter;

import com.chatling.common.model.ModelPolicyConfig;
import com.chatling.common.policy.PolicyPipelineResult;
import com.chatling.common.security.ModerationResult;
import com.chatling.engine.security.AliyunGreenSecurityService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 6. 阿里云绿网 2.0 云端深度机审过滤器 (Order: 500)
 */
@Slf4j
@Component
public class AliyunGreenFilter implements PolicyFilter {

    @Autowired(required = false)
    private AliyunGreenSecurityService aliyunGreenSecurityService;

    @Override
    public int getOrder() {
        return 500;
    }

    @Override
    public boolean isEnabled(ModelPolicyConfig config) {
        return config != null && Boolean.TRUE.equals(config.getEnableAliyunGreen());
    }

    @Override
    public PolicyPipelineResult filter(RequestContext ctx, ModelPolicyConfig config) {
        if (!isEnabled(config) || aliyunGreenSecurityService == null) {
            return PolicyPipelineResult.pass();
        }

        String prompt = ctx.getCurrentPrompt();
        if (prompt == null || prompt.isEmpty()) {
            return PolicyPipelineResult.pass();
        }

        ModerationResult result = aliyunGreenSecurityService.checkContent(prompt);
        if (result != null && !result.isPass()) {
            log.warn("[-] [Filter: Aliyun Green Blocked] model={}, reason={}", ctx.getModelName(), result.getRiskReason());
            return PolicyPipelineResult.reject(400, result.getRiskReason(), "plugin_aliyun_green");
        }

        return PolicyPipelineResult.pass();
    }
}
