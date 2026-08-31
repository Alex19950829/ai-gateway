package com.chatling.admin.controller;

import com.chatling.common.model.CommonResult;
import com.chatling.common.model.ModelPolicyConfig;
import com.chatling.engine.policy.ModelPolicyManager;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import javax.annotation.Resource;
import java.util.Collection;

/**
 * 声明式模型策略管控接口 (Model Policy Admin Controller)
 * 支持在控制台一键开关与配置各模型的防护/加速插件
 */
@Slf4j
@RestController
@NoArgsConstructor
@AllArgsConstructor
@RequestMapping("/api/admin")
public class ModelPolicyAdminController {

    @Resource
    private ModelPolicyManager modelPolicyManager;

    @GetMapping("/model-policies")
    public Mono<CommonResult<Collection<ModelPolicyConfig>>> listModelPolicies() {
        return Mono.fromCallable(() -> CommonResult.success(modelPolicyManager.getAllPolicies()));
    }

    @GetMapping("/model-policies/{modelName}")
    public Mono<CommonResult<ModelPolicyConfig>> getModelPolicy(@PathVariable String modelName) {
        return Mono.fromCallable(() -> CommonResult.success(modelPolicyManager.getPolicy(modelName)));
    }

    @PostMapping("/model-policies")
    public Mono<CommonResult<ModelPolicyConfig>> saveModelPolicy(@RequestBody ModelPolicyConfig config) {
        return Mono.fromCallable(() -> {
            modelPolicyManager.savePolicy(config);
            log.info("[*] [Admin] Updated model policy config for model: {}", config.getModelName());
            return CommonResult.success(config);
        });
    }

    @DeleteMapping("/model-policies/{modelName}")
    public Mono<CommonResult<Void>> deleteModelPolicy(@PathVariable String modelName) {
        return Mono.fromCallable(() -> {
            modelPolicyManager.removePolicy(modelName);
            log.info("[*] [Admin] Removed model policy config for model: {}", modelName);
            return CommonResult.success();
        });
    }
}
