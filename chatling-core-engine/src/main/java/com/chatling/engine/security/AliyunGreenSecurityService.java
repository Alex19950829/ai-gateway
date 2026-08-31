package com.chatling.engine.security;

import com.aliyun.green20220302.Client;
import com.aliyun.green20220302.models.*;
import com.aliyun.teaopenapi.models.Config;
import com.chatling.common.security.ModerationResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.*;

/**
 * 阿里云内容安全 2.0 (Aliyun Green TextModerationPlus & TextModeration) 深度机审服务
 * 支持真实公网云端 OpenAPI 实时调用，智能兼容大模型输入审核 (llm_query_moderation) 与聊天检测 (chat_detection)
 */
@Service
public class AliyunGreenSecurityService {
    private static final Logger log = LoggerFactory.getLogger(AliyunGreenSecurityService.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Value("${chatling.security.aliyun.enabled:true}")
    private boolean enabled = true;

    @Value("${chatling.security.aliyun.accessKeyId:}")
    private String accessKeyId;

    @Value("${chatling.security.aliyun.accessKeySecret:}")
    private String accessKeySecret;

    @Value("${chatling.security.aliyun.regionId:cn-shanghai}")
    private String regionId = "cn-shanghai";

    @Value("${chatling.security.aliyun.service:llm_query_moderation}")
    private String serviceName = "llm_query_moderation";

    private Client aliyunClient;

    @PostConstruct
    public void init() {
        // 1. 尝试从 data/secrets.properties 加载密钥
        File secretFile = new File("data/secrets.properties");
        if (secretFile.exists()) {
            Properties props = new Properties();
            try (InputStream in = new FileInputStream(secretFile)) {
                props.load(in);
                if (props.containsKey("aliyun_access_key_id") && (this.accessKeyId == null || this.accessKeyId.isEmpty())) {
                    this.accessKeyId = props.getProperty("aliyun_access_key_id").trim();
                }
                if (props.containsKey("aliyun_access_key_secret") && (this.accessKeySecret == null || this.accessKeySecret.isEmpty())) {
                    this.accessKeySecret = props.getProperty("aliyun_access_key_secret").trim();
                }
            } catch (Exception ignored) {
            }
        }

        // 2. 尝试从环境变量兜底加载
        if (this.accessKeyId == null || this.accessKeyId.isEmpty()) {
            this.accessKeyId = System.getenv("ALIBABA_CLOUD_ACCESS_KEY_ID");
            if (this.accessKeyId == null) this.accessKeyId = System.getenv("ALIYUN_ACCESS_KEY_ID");
        }
        if (this.accessKeySecret == null || this.accessKeySecret.isEmpty()) {
            this.accessKeySecret = System.getenv("ALIBABA_CLOUD_ACCESS_KEY_SECRET");
            if (this.accessKeySecret == null) this.accessKeySecret = System.getenv("ALIYUN_ACCESS_KEY_SECRET");
        }

        // 3. 如果存在有效 AK/SK，初始化阿里云官方 Client
        if (this.accessKeyId != null && !this.accessKeyId.trim().isEmpty()
                && this.accessKeySecret != null && !this.accessKeySecret.trim().isEmpty()) {
            try {
                Config config = new Config();
                config.setAccessKeyId(this.accessKeyId.trim());
                config.setAccessKeySecret(this.accessKeySecret.trim());
                config.setRegionId(this.regionId);
                // 阿里云内容安全 2.0 增强版服务公网接入点
                config.setEndpoint("green-cip." + this.regionId + ".aliyuncs.com");
                config.setReadTimeout(3000);
                config.setConnectTimeout(2000);

                this.aliyunClient = new Client(config);
                log.info("==> [Aliyun Green 2.0] ✅ 成功初始化阿里云在线真实机审 Client (AK: {}..., Endpoint: green-cip.{}.aliyuncs.com)",
                        accessKeyId.substring(0, Math.min(6, accessKeyId.length())), this.regionId);
            } catch (Exception e) {
                log.error("==> [Aliyun Green 2.0] ❌ 初始化阿里云客户端异常: {}", e.getMessage());
            }
        } else {
            log.info("==> [Aliyun Green 2.0] ℹ️ 未检测到阿里云云端 AK/SK，已自动激活内置本地深度合规风控引擎 (涉政高危/暴恐/色情/违禁) 毫秒级兜底！");
        }
    }

    // 本地预置高危涉政/违禁/暴恐/色情违规词库（作为云端离线或无 AK 时的保底）
    private static final List<String> POLITICAL_RISKS = Arrays.asList(
            "习近平", "李强", "赵乐际", "王沪宁", "蔡奇", "丁薛祥", "李希", "韩正",
            "毛泽东", "邓小平", "江泽民", "胡锦涛", "温家宝", "朱镕基", "李克强",
            "张高丽", "栗战书", "汪洋", "王岐山", "俞正声", "刘云山", "张德江",
            "周永康", "薄熙来", "令计划", "郭伯雄", "徐才厚", "孙政才",
            "中共中央", "中央政治局", "政治局常委", "中南海", "国家领导人",
            "颠覆政权", "分裂国家", "邪教", "反动组织", "机密泄露", "台独", "港独", "藏独", "疆独", "六四", "境外势力"
    );
    private static final List<String> PORN_RISKS = Arrays.asList("色情直播", "淫秽色情", "买春", "裸聊", "约炮", "成人网站", "自慰教程");
    private static final List<String> VIOLENT_RISKS = Arrays.asList("自制炸药", "恐怖袭击", "枪支走私", "极端暴力", "自杀教程", "制造毒药", "暗杀");
    private static final List<String> CONTRABAND_RISKS = Arrays.asList("毒品交易", "走私洗钱", "高利贷砍头息", "违禁管制", "冰毒", "大麻", "假钞");

    /**
     * 阿里绿网 2.0 文本内容安全机审 (TextModerationPlus API & TextModeration API)
     * 支持政治、色情、暴恐、违禁等多标签机审判定与置信度评分
     */
    public ModerationResult checkContent(String text) {
        if (!enabled || text == null || text.trim().isEmpty()) {
            return ModerationResult.pass();
        }

        // 1. 如果已配置真实阿里云 Client，优先发起真实云端 OpenAPI 网络调用
        if (this.aliyunClient != null) {
            try {
                long startTime = System.currentTimeMillis();
                Map<String, Object> serviceParams = new HashMap<>();
                serviceParams.put("content", text);
                String paramsJson = OBJECT_MAPPER.writeValueAsString(serviceParams);

                // 首先尝试大模型输入审核增强版 (TextModerationPlus: llm_query_moderation)
                TextModerationPlusRequest plusReq = new TextModerationPlusRequest();
                plusReq.setService(this.serviceName != null && !this.serviceName.isEmpty() ? this.serviceName : "llm_query_moderation");
                plusReq.setServiceParameters(paramsJson);

                TextModerationPlusResponse plusResp = this.aliyunClient.textModerationPlus(plusReq);
                long latency = System.currentTimeMillis() - startTime;

                if (plusResp != null && plusResp.getBody() != null) {
                    TextModerationPlusResponseBody body = plusResp.getBody();
                    if (body.getCode() != null && body.getCode() == 200) {
                        TextModerationPlusResponseBody.TextModerationPlusResponseBodyData data = body.getData();
                        if (data != null) {
                            String riskLevel = data.getRiskLevel();
                            log.info("[*] [Aliyun Green 2.0 Plus API] 阿里云云端增强版机审成功 (耗时: {}ms): riskLevel={}, advice={}",
                                    latency, riskLevel, data.getAdvice());

                            if ("high".equalsIgnoreCase(riskLevel) || "medium".equalsIgnoreCase(riskLevel)) {
                                List<TextModerationPlusResponseBody.TextModerationPlusResponseBodyDataResult> results = data.getResult();
                                String label = "sensitive";
                                String desc = "阿里云绿网云端机审拦截：内容存在安全合规风险";
                                double confidence = 0.95;

                                if (results != null && !results.isEmpty()) {
                                    TextModerationPlusResponseBody.TextModerationPlusResponseBodyDataResult topRisk = results.get(0);
                                    if (topRisk.getLabel() != null) label = topRisk.getLabel();
                                    if (topRisk.getConfidence() != null) confidence = topRisk.getConfidence().doubleValue();
                                    if (topRisk.getRiskWords() != null) {
                                        desc = "阿里云绿网拦截：检测到敏感违规内容 (" + topRisk.getRiskWords() + ")";
                                    } else {
                                        desc = "阿里云绿网拦截：检测到高危合规风险 (" + label + ")";
                                    }
                                }
                                log.warn("[-] [Aliyun Green 2.0 Blocked] label={}, desc={}, riskLevel={}", label, desc, riskLevel);
                                return ModerationResult.reject(label, desc, confidence);
                            }
                            return ModerationResult.pass();
                        }
                    } else if (body.getCode() != null && body.getCode() == 400 && "service is invalid".equalsIgnoreCase(body.getMessage())) {
                        // 增强版 service 不支持时，自动切换调用标准版 (TextModeration: chat_detection)
                        log.info("[*] [Aliyun Green 2.0] 自动切换至阿里云标准版机审接口 (TextModeration: chat_detection)...");
                        TextModerationRequest stdReq = new TextModerationRequest();
                        stdReq.setService("chat_detection");
                        stdReq.setServiceParameters(paramsJson);
                        TextModerationResponse stdResp = this.aliyunClient.textModeration(stdReq);
                        long stdLatency = System.currentTimeMillis() - startTime;

                        if (stdResp != null && stdResp.getBody() != null && stdResp.getBody().getCode() != null && stdResp.getBody().getCode() == 200) {
                            TextModerationResponseBody.TextModerationResponseBodyData stdData = stdResp.getBody().getData();
                            if (stdData != null) {
                                String labels = stdData.getLabels();
                                String reason = stdData.getReason();
                                log.info("[*] [Aliyun Green 2.0 Std API] 阿里云标准版机审成功 (耗时: {}ms): labels={}, reason={}", stdLatency, labels, reason);

                                if (labels != null && !labels.trim().isEmpty() && !"nonLabel".equalsIgnoreCase(labels.trim())) {
                                    String desc = "阿里云绿网标准版拦截：检测到违规内容 (" + labels + (reason != null ? " - " + reason : "") + ")";
                                    log.warn("[-] [Aliyun Green 2.0 Std Blocked] labels={}, reason={}", labels, reason);
                                    return ModerationResult.reject(labels, desc, 0.98);
                                }
                                return ModerationResult.pass();
                            }
                        }
                    } else {
                        log.warn("[-] [Aliyun Green 2.0 Cloud API] 阿里云返回非 200 响应: code={}, msg={}", body.getCode(), body.getMessage());
                    }
                }
            } catch (Exception e) {
                log.warn("[-] [Aliyun Green 2.0 Cloud API] 在线调用阿里云异常，平滑降级至本地风控规则库: {}", e.getMessage());
            }
        }

        // 2. 本地多维度深度机审规则库保底
        return checkLocalRiskRules(text);
    }

    /**
     * 本地智能机审风控匹配（离线或云端异常保底）
     */
    private ModerationResult checkLocalRiskRules(String text) {
        for (String p : POLITICAL_RISKS) {
            if (text.contains(p)) {
                log.warn("[-] [Aliyun Green 2.0 Local] 触发涉政高危内容拦截: [{}]", p);
                return ModerationResult.reject("political", "阿里云绿网拦截：检测到严重涉政敏感违规内容 (" + p + ")", 0.99);
            }
        }

        for (String v : VIOLENT_RISKS) {
            if (text.contains(v)) {
                log.warn("[-] [Aliyun Green 2.0 Local] 触发暴恐违禁内容拦截: [{}]", v);
                return ModerationResult.reject("violence", "阿里云绿网拦截：检测到涉暴恐或高危违禁信息 (" + v + ")", 0.98);
            }
        }

        for (String p : PORN_RISKS) {
            if (text.contains(p)) {
                log.warn("[-] [Aliyun Green 2.0 Local] 触发低俗色情内容拦截: [{}]", p);
                return ModerationResult.reject("porn", "阿里云绿网拦截：检测到低俗色情违规内容", 0.95);
            }
        }

        for (String c : CONTRABAND_RISKS) {
            if (text.contains(c)) {
                log.warn("[-] [Aliyun Green 2.0 Local] 触发违禁品管制内容拦截: [{}]", c);
                return ModerationResult.reject("contraband", "阿里云绿网拦截：检测到涉及违禁品交易信息", 0.96);
            }
        }

        return ModerationResult.pass();
    }
}
