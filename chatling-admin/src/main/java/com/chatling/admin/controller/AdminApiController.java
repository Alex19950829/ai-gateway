package com.chatling.admin.controller;

import com.chatling.admin.service.AdminService;
import com.chatling.common.model.ApiKey;
import com.chatling.common.model.ChatAudit;
import com.chatling.common.model.CommonResult;
import com.chatling.common.model.ModelConfig;
import com.chatling.common.model.UsageDaily;
import com.chatling.engine.service.ModelEngineService;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import javax.annotation.Resource;
import java.util.*;

@Slf4j
@RestController
@NoArgsConstructor
@AllArgsConstructor
@RequestMapping("/api/admin")
public class AdminApiController {

    @Resource
    private AdminService adminService;
    
    @Resource
    private ModelEngineService modelEngineService;

    // ==================== API Key 治理 ====================

    @GetMapping("/apikeys")
    public Mono<CommonResult<List<ApiKey>>> listApiKeys() {
        return Mono.fromCallable(() -> CommonResult.success(adminService.listApiKeys()));
    }

    @PostMapping("/apikeys")
    public Mono<CommonResult<ApiKey>> createApiKey(@RequestBody ApiKey keyReq) {
        return Mono.fromCallable(() -> CommonResult.success(adminService.createApiKey(keyReq)));
    }

    @PutMapping("/apikeys/{apiKey}/status")
    public Mono<CommonResult<Void>> updateApiKeyStatus(@PathVariable String apiKey, @RequestParam int status) {
        return Mono.fromCallable(() -> {
            adminService.updateApiKeyStatus(apiKey, status);
            return CommonResult.success();
        });
    }

    @DeleteMapping("/apikeys/{id}")
    public Mono<CommonResult<Void>> deleteApiKey(@PathVariable Long id) {
        return Mono.fromCallable(() -> {
            adminService.deleteApiKey(id);
            return CommonResult.success();
        });
    }

    // ==================== 模型配置与连通性探活 ====================

    @GetMapping("/models")
    public Mono<CommonResult<List<ModelConfig>>> listModels() {
        return Mono.fromCallable(() -> CommonResult.success(adminService.listModels()));
    }

    @PostMapping("/models")
    public Mono<CommonResult<ModelConfig>> addModel(@RequestBody ModelConfig modelConfig) {
        return Mono.fromCallable(() -> CommonResult.success(adminService.addModel(modelConfig)));
    }

    @PutMapping("/models")
    public Mono<CommonResult<ModelConfig>> updateModel(@RequestBody ModelConfig modelConfig) {
        return Mono.fromCallable(() -> CommonResult.success(adminService.updateModel(modelConfig)));
    }

    @DeleteMapping("/models/{id}")
    public Mono<CommonResult<Void>> deleteModel(@PathVariable Long id) {
        return Mono.fromCallable(() -> {
            adminService.deleteModel(id);
            return CommonResult.success();
        });
    }

    /**
     * 连通性测试：向填写的真实上游模型发送探活请求
     */
    @PostMapping("/models/test-connection")
    public Mono<CommonResult<Map<String, Object>>> testModelConnection(@RequestBody ModelConfig modelConfig) {
        long start = System.currentTimeMillis();
        return modelEngineService.testModelConnectivity(modelConfig)
                .map(ok -> {
                    long cost = System.currentTimeMillis() - start;
                    Map<String, Object> map = new HashMap<>();
                    map.put("connected", ok);
                    map.put("latencyMs", cost);
                    map.put("message", ok ? "连接成功，大模型响应正常" : "连接失败，请检查 BaseURL 或 API Key");
                    return CommonResult.success(map);
                });
    }

    // ==================== 看板统计与审计 ====================

    @GetMapping("/dashboard/stats")
    public Mono<CommonResult<Map<String, Object>>> getDashboardStats() {
        return Mono.fromCallable(() -> CommonResult.success(adminService.getDashboardStats()));
    }

    @GetMapping("/dashboard/audits")
    public Mono<CommonResult<List<ChatAudit>>> getRecentAudits(@RequestParam(defaultValue = "50") int limit) {
        return Mono.fromCallable(() -> CommonResult.success(adminService.listRecentAudits(limit)));
    }

    @GetMapping("/dashboard/usage")
    public Mono<CommonResult<List<UsageDaily>>> getDailyUsage(@RequestParam(defaultValue = "30") int limit) {
        return Mono.fromCallable(() -> CommonResult.success(adminService.listDailyUsage(limit)));
    }

    // ==================== 模型权限申请与审批 ====================

    @PostMapping("/applies")
    public Mono<CommonResult<com.chatling.common.model.ModelApply>> submitApply(@RequestBody com.chatling.common.model.ModelApply apply) {
        return Mono.fromCallable(() -> CommonResult.success(adminService.submitApply(apply)));
    }

    @GetMapping("/applies")
    public Mono<CommonResult<List<com.chatling.common.model.ModelApply>>> listApplies(@RequestParam(required = false) Integer status) {
        return Mono.fromCallable(() -> CommonResult.success(adminService.listModelApplies(status)));
    }

    @PutMapping("/applies/{id}/approve")
    public Mono<CommonResult<Void>> approveApply(@PathVariable Long id) {
        return Mono.fromCallable(() -> {
            adminService.approveApply(id);
            return CommonResult.success();
        });
    }

    @PutMapping("/applies/{id}/reject")
    public Mono<CommonResult<Void>> rejectApply(@PathVariable Long id) {
        return Mono.fromCallable(() -> {
            adminService.rejectApply(id);
            return CommonResult.success();
        });
    }
}
