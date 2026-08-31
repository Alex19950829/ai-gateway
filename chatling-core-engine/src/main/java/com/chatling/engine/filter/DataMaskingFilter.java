package com.chatling.engine.filter;

import com.chatling.common.model.ModelPolicyConfig;
import com.chatling.common.policy.PolicyPipelineResult;
import com.chatling.engine.governance.DataMaskingGovernor;
import com.chatling.engine.governance.DataMaskingGovernor.MaskingResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 4. PII 个人隐私与敏感数据动态脱敏过滤器 (Order: 300)
 */
@Slf4j
@Component
public class DataMaskingFilter implements PolicyFilter {

    @Autowired(required = false)
    private DataMaskingGovernor dataMaskingGovernor;

    @Override
    public int getOrder() {
        return 300;
    }

    @Override
    public boolean isEnabled(ModelPolicyConfig config) {
        return config != null && Boolean.TRUE.equals(config.getEnableDataMasking());
    }

    @Override
    public PolicyPipelineResult filter(RequestContext ctx, ModelPolicyConfig config) {
        if (!isEnabled(config) || dataMaskingGovernor == null) {
            return PolicyPipelineResult.pass();
        }

        String prompt = ctx.getCurrentPrompt();
        if (prompt == null || prompt.isEmpty()) {
            return PolicyPipelineResult.pass();
        }

        String maskMode = config.getMaskMode() != null ? config.getMaskMode() : "MASK";
        MaskingResult result = dataMaskingGovernor.mask(prompt, maskMode);
        if (result != null && result.isContainsSensitive() && result.getMaskedText() != null) {
            String masked = result.getMaskedText();
            log.info("[*] [Filter: Data Masked] model={}, mode={}, originalLen={}, maskedLen={}",
                    ctx.getModelName(), maskMode, prompt.length(), masked.length());
            return PolicyPipelineResult.mask(masked, "plugin_data_masking", "PII 个人隐私数据已自动脱敏");
        }

        return PolicyPipelineResult.pass();
    }
}
