package com.chatling.admin.controller;

import com.chatling.common.factor.FactorDefinition;
import com.chatling.common.model.CommonResult;
import com.chatling.common.policy.ModelPolicyDefinition;
import com.chatling.common.rule.BaseRuleExecutor;
import com.chatling.common.rule.RuleDefinition;
import com.chatling.engine.factor.FactorEngine;
import com.chatling.engine.policy.ModelPolicyManager;
import com.chatling.engine.rule.RuleExecutorManager;
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
public class FactorRuleAdminController {

    @Resource
    private FactorEngine factorEngine;

    @Resource
    private RuleExecutorManager ruleExecutorManager;

    @Resource
    private ModelPolicyManager modelPolicyManager;

    // ==================== 1. 特征变量 (Factors) API ====================

    @GetMapping("/factors")
    public Mono<CommonResult<Collection<FactorDefinition>>> listFactors() {
        return Mono.fromCallable(() -> CommonResult.success(factorEngine.getAllFactors()));
    }

    @PostMapping("/factors")
    public Mono<CommonResult<FactorDefinition>> saveFactor(@RequestBody FactorDefinition definition) {
        return Mono.fromCallable(() -> {
            factorEngine.registerFactor(definition);
            return CommonResult.success(definition);
        });
    }

    @DeleteMapping("/factors/{code}")
    public Mono<CommonResult<Void>> deleteFactor(@PathVariable String code) {
        return Mono.fromCallable(() -> {
            factorEngine.removeFactor(code);
            return CommonResult.success();
        });
    }

    // ==================== 2. Groovy 规则 (Rules) API ====================

    @GetMapping("/rules")
    public Mono<CommonResult<Collection<RuleDefinition>>> listRules() {
        return Mono.fromCallable(() -> CommonResult.success(ruleExecutorManager.getAllRules()));
    }

    @PostMapping("/rules")
    public Mono<CommonResult<RuleDefinition>> saveRule(@RequestBody RuleDefinition definition) {
        return Mono.fromCallable(() -> {
            // 立即尝试编译校验语法
            ruleExecutorManager.getOrCompileExecutor(definition);
            ruleExecutorManager.registerRule(definition);
            return CommonResult.success(definition);
        });
    }

    @DeleteMapping("/rules/{code}")
    public Mono<CommonResult<Void>> deleteRule(@PathVariable String code) {
        return Mono.fromCallable(() -> {
            ruleExecutorManager.removeRule(code);
            return CommonResult.success();
        });
    }

    @PostMapping("/rules/test-syntax")
    public Mono<CommonResult<String>> testRuleSyntax(@RequestBody RuleDefinition definition) {
        return Mono.fromCallable(() -> {
            try {
                BaseRuleExecutor executor = ruleExecutorManager.getOrCompileExecutor(definition);
                return CommonResult.success("Groovy 脚本编译成功！实例化对象: " + executor.getClass().getSimpleName());
            } catch (Exception e) {
                return CommonResult.fail("语法校验失败: " + e.getMessage());
            }
        });
    }

    @PostMapping("/rules/dry-run")
    public Mono<CommonResult<Map<String, Object>>> dryRunRule(@RequestBody Map<String, Object> req) {
        return Mono.fromCallable(() -> {
            long startTime = System.currentTimeMillis();
            try {
                String script = (String) req.get("groovyScript");
                Map<String, Object> factors = (Map<String, Object>) req.getOrDefault("factors", new HashMap<>());
                
                // 将数值类型规范化转换为 Long / Double
                Map<String, Object> normalizedFactors = new HashMap<>();
                for (Map.Entry<String, Object> entry : factors.entrySet()) {
                    Object val = entry.getValue();
                    if (val instanceof Number) {
                        normalizedFactors.put(entry.getKey(), ((Number) val).longValue());
                    } else if (val instanceof String && ((String) val).matches("^\\d+$")) {
                        normalizedFactors.put(entry.getKey(), Long.parseLong((String) val));
                    } else {
                        normalizedFactors.put(entry.getKey(), val);
                    }
                }

                RuleDefinition mockDef = new RuleDefinition();
                mockDef.setRuleCode("dry_run_rule_" + System.currentTimeMillis());
                mockDef.setGroovyScript(script);

                BaseRuleExecutor executor = ruleExecutorManager.getOrCompileExecutor(mockDef);
                long compileTime = System.currentTimeMillis() - startTime;

                long execStart = System.currentTimeMillis();
                com.chatling.common.rule.RuleDecision decision = executor.executeRule(normalizedFactors);
                long execTime = System.currentTimeMillis() - execStart;

                Map<String, Object> result = new HashMap<>();
                result.put("passed", decision.isPass());
                result.put("action", decision.getAction());
                result.put("message", decision.getMessage() != null ? decision.getMessage() : "放行 (PASS)");
                result.put("fallbackModel", decision.getFallbackModel());
                result.put("compileTimeMs", compileTime);
                result.put("execTimeMs", execTime);
                return CommonResult.success(result);
            } catch (Exception e) {
                return CommonResult.fail("规则运行异常: " + e.getMessage());
            }
        });
    }

    // ==================== 3. 模型策略 (Model Policies) API ====================

    @GetMapping("/model-policies")
    public Mono<CommonResult<Collection<ModelPolicyDefinition>>> listModelPolicies() {
        return Mono.fromCallable(() -> CommonResult.success(modelPolicyManager.getAllPolicies()));
    }

    @GetMapping("/model-policies/{modelName}")
    public Mono<CommonResult<ModelPolicyDefinition>> getModelPolicy(@PathVariable String modelName) {
        return Mono.fromCallable(() -> CommonResult.success(modelPolicyManager.getPolicy(modelName)));
    }

    @PostMapping("/model-policies")
    public Mono<CommonResult<ModelPolicyDefinition>> saveModelPolicy(@RequestBody ModelPolicyDefinition policy) {
        return Mono.fromCallable(() -> {
            modelPolicyManager.registerPolicy(policy);
            return CommonResult.success(policy);
        });
    }

    @DeleteMapping("/model-policies/{modelName}")
    public Mono<CommonResult<Void>> deleteModelPolicy(@PathVariable String modelName) {
        return Mono.fromCallable(() -> {
            modelPolicyManager.removePolicy(modelName);
            return CommonResult.success();
        });
    }

    // ==================== 4. AI RAG 知识库管理 API ====================

    @Resource
    private com.chatling.engine.rag.RagKnowledgeService ragKnowledgeService;

    @Resource
    private com.chatling.engine.security.AliyunGreenSecurityService aliyunGreenSecurityService;

    @GetMapping("/rag/docs")
    public Mono<CommonResult<Collection<com.chatling.common.rag.KnowledgeDoc>>> listRagDocs() {
        return Mono.fromCallable(() -> CommonResult.success(ragKnowledgeService.getAllDocs()));
    }

    @PostMapping("/rag/docs")
    public Mono<CommonResult<com.chatling.common.rag.KnowledgeDoc>> saveRagDoc(@RequestBody com.chatling.common.rag.KnowledgeDoc doc) {
        return Mono.fromCallable(() -> {
            if (doc.getDocId() == null || doc.getDocId().isEmpty()) {
                doc.setDocId("doc-" + UUID.randomUUID().toString().substring(0, 8));
            }
            ragKnowledgeService.addDoc(doc);
            return CommonResult.success(doc);
        });
    }

    @DeleteMapping("/rag/docs/{docId}")
    public Mono<CommonResult<Void>> deleteRagDoc(@PathVariable String docId) {
        return Mono.fromCallable(() -> {
            ragKnowledgeService.deleteDoc(docId);
            return CommonResult.success();
        });
    }

    // ==================== 5. 阿里云绿网 2.0 内容安全机审 API ====================

    @PostMapping("/security/check")
    public Mono<CommonResult<com.chatling.common.security.ModerationResult>> testSecurityCheck(@RequestBody Map<String, String> body) {
        return Mono.fromCallable(() -> {
            String text = body.getOrDefault("text", "");
            return CommonResult.success(aliyunGreenSecurityService.checkContent(text));
        });
    }
}
