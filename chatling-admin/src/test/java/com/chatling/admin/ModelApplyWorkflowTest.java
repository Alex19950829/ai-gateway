package com.chatling.admin;

import com.chatling.admin.controller.AdminApiController;
import com.chatling.common.model.ApiKey;
import com.chatling.common.model.CommonResult;
import com.chatling.common.model.ModelApply;
import com.chatling.engine.service.ModelEngineService;
import com.chatling.gateway.repository.ChatlingDao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import javax.sql.DataSource;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class ModelApplyWorkflowTest {

    private ChatlingDao chatlingDao;
    private AdminApiController adminApiController;

    @BeforeEach
    public void setUp() {
        DataSource dataSource = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .addScript("classpath:schema-test.sql")
                .build();
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        chatlingDao = new ChatlingDao(jdbcTemplate);
        adminApiController = new AdminApiController(chatlingDao, null);
    }

    @Test
    public void testApplyAndApproveWorkflow() {
        String testKey = "sk-chatling-user-test999";
        ApiKey key = ApiKey.builder()
                .apiKey(testKey)
                .keyName("测试Key")
                .ownerName("lisi")
                .department("招聘事业部")
                .allowedModels("chatling-turbo") // 初始只允许访问自研模型
                .tpmLimit(60000)
                .qpsLimit(20)
                .totalQuota(-1L)
                .usedQuota(0L)
                .status(1)
                .build();
        chatlingDao.insertApiKey(key);

        // 1. 用户申请开通 qwen-max
        ModelApply apply = ModelApply.builder()
                .applicantName("lisi")
                .department("招聘事业部")
                .apiKey(testKey)
                .modelName("qwen-max")
                .applyReason("招聘职位JD生成")
                .build();

        CommonResult<ModelApply> submitRes = adminApiController.submitApply(apply).block();
        assertNotNull(submitRes);
        assertEquals(0, submitRes.getCode());

        List<ModelApply> pendingList = adminApiController.listApplies(0).block().getData();
        assertFalse(pendingList.isEmpty());
        Long applyId = pendingList.get(0).getId();

        // 2. 管理员审批通过
        CommonResult<Void> approveRes = adminApiController.approveApply(applyId).block();
        assertNotNull(approveRes);
        assertEquals(0, approveRes.getCode());

        // 3. 验证 Key 的 allowed_models 自动追加了 qwen-max
        ApiKey updatedKey = chatlingDao.findByApiKey(testKey).get();
        assertTrue(updatedKey.getAllowedModels().contains("qwen-max"));
        assertTrue(updatedKey.getAllowedModels().contains("chatling-turbo"));
        assertEquals("chatling-turbo,qwen-max", updatedKey.getAllowedModels());
    }
}
