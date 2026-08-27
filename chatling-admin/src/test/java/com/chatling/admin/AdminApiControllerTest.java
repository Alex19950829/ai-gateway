package com.chatling.admin;

import com.chatling.admin.controller.AdminApiController;
import com.chatling.admin.service.AdminService;
import com.chatling.common.model.ApiKey;
import com.chatling.common.model.CommonResult;
import com.chatling.common.model.ModelConfig;
import com.chatling.engine.service.ModelEngineService;
import com.chatling.gateway.repository.ChatlingDao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class AdminApiControllerTest {

    private AdminApiController adminApiController;
    private ChatlingDao chatlingDao;

    @BeforeEach
    public void setUp() {
        DataSource dataSource = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .addScript("classpath:schema-test.sql")
                .build();
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        chatlingDao = new ChatlingDao(jdbcTemplate);
        AdminService adminService = new AdminService(chatlingDao);
        adminApiController = new AdminApiController(adminService, org.mockito.Mockito.mock(ModelEngineService.class));
    }

    @Test
    public void testApiKeyLifecycle() {
        // 1. Create Key
        ApiKey req = ApiKey.builder()
                .keyName("测试单元Key")
                .ownerName("testuser")
                .department("搜索推荐部")
                .allowedModels("chatling-turbo,deepseek-v3")
                .tpmLimit(80000)
                .qpsLimit(30)
                .build();

        CommonResult<ApiKey> createRes = adminApiController.createApiKey(req).block();
        assertNotNull(createRes);
        assertEquals(0, createRes.getCode());
        assertNotNull(createRes.getData().getApiKey());
        assertTrue(createRes.getData().getApiKey().startsWith("sk-chatling-"));

        String generatedKey = createRes.getData().getApiKey();

        // 2. List Keys
        CommonResult<List<ApiKey>> listRes = adminApiController.listApiKeys().block();
        assertNotNull(listRes);
        assertFalse(listRes.getData().isEmpty());

        // 3. Update Status to Disabled (0)
        CommonResult<Void> disableRes = adminApiController.updateApiKeyStatus(generatedKey, 0).block();
        assertNotNull(disableRes);
        assertEquals(0, disableRes.getCode());

        ApiKey found = chatlingDao.findByApiKey(generatedKey).orElse(null);
        assertNotNull(found);
        assertEquals(0, found.getStatus());
    }

    @Test
    public void testDashboardStatsAndAudits() {
        CommonResult<Map<String, Object>> statsRes = adminApiController.getDashboardStats().block();
        assertNotNull(statsRes);
        assertEquals(0, statsRes.getCode());
        assertTrue(statsRes.getData().containsKey("totalTokens"));
        assertTrue(statsRes.getData().containsKey("totalRequests"));
        assertTrue(statsRes.getData().containsKey("avgTtftMs"));
    }

    @Test
    public void testModelConfigCrud() {
        ModelConfig config = ModelConfig.builder()
                .modelName("test-llm-v1")
                .displayName("测试模型V1")
                .providerType("mock")
                .baseUrl("http://localhost:8000")
                .build();

        CommonResult<ModelConfig> addRes = adminApiController.addModel(config).block();
        assertNotNull(addRes);
        assertEquals(0, addRes.getCode());

        CommonResult<List<ModelConfig>> listRes = adminApiController.listModels().block();
        assertNotNull(listRes);
        assertTrue(listRes.getData().stream().anyMatch(m -> "test-llm-v1".equals(m.getModelName())));
    }
}
