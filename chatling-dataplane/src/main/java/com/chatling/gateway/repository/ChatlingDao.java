package com.chatling.gateway.repository;

import com.chatling.common.model.ApiKey;
import com.chatling.common.model.ChatAudit;
import com.chatling.common.model.ModelConfig;
import com.chatling.common.model.UsageDaily;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Repository
@NoArgsConstructor
@AllArgsConstructor
public class ChatlingDao {

    @Resource
    private JdbcTemplate jdbcTemplate;

    // ==================== API Key ====================

    public List<ApiKey> listApiKeys() {
        String sql = "SELECT * FROM t_api_key ORDER BY id DESC";
        return jdbcTemplate.query(sql, new BeanPropertyRowMapper<>(ApiKey.class));
    }

    public Optional<ApiKey> findByApiKey(String apiKey) {
        String sql = "SELECT * FROM t_api_key WHERE api_key = ?";
        List<ApiKey> list = jdbcTemplate.query(sql, new BeanPropertyRowMapper<>(ApiKey.class), apiKey);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public int insertApiKey(ApiKey key) {
        String sql = "INSERT INTO t_api_key (api_key, key_name, owner_name, department, allowed_models, tpm_limit, qps_limit, total_quota, used_quota, status) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        return jdbcTemplate.update(sql, key.getApiKey(), key.getKeyName(), key.getOwnerName(), key.getDepartment(),
                key.getAllowedModels(), key.getTpmLimit(), key.getQpsLimit(), key.getTotalQuota(), key.getUsedQuota(), key.getStatus());
    }

    public int updateApiKeyStatus(String apiKey, int status) {
        String sql = "UPDATE t_api_key SET status = ? WHERE api_key = ?";
        return jdbcTemplate.update(sql, status, apiKey);
    }

    public int deleteApiKey(Long id) {
        String sql = "DELETE FROM t_api_key WHERE id = ?";
        return jdbcTemplate.update(sql, id);
    }

    public void incrUsedQuota(String apiKey, int tokens) {
        String sql = "UPDATE t_api_key SET used_quota = used_quota + ? WHERE api_key = ?";
        jdbcTemplate.update(sql, tokens, apiKey);
    }

    // ==================== Model Config ====================

    public List<ModelConfig> listModelConfigs() {
        String sql = "SELECT * FROM t_model_config ORDER BY id ASC";
        return jdbcTemplate.query(sql, new BeanPropertyRowMapper<>(ModelConfig.class));
    }

    public Optional<ModelConfig> findModelConfig(String modelName) {
        String sql = "SELECT * FROM t_model_config WHERE model_name = ? AND status = 1";
        List<ModelConfig> list = jdbcTemplate.query(sql, new BeanPropertyRowMapper<>(ModelConfig.class), modelName);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public int insertModelConfig(ModelConfig config) {
        String sql = "INSERT INTO t_model_config (model_name, display_name, provider_type, base_url, api_secret, fallback_model, timeout_ms, status, description) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        return jdbcTemplate.update(sql, config.getModelName(), config.getDisplayName(), config.getProviderType(),
                config.getBaseUrl(), config.getApiSecret(), config.getFallbackModel(), config.getTimeoutMs(), config.getStatus(), config.getDescription());
    }

    public int updateModelConfig(ModelConfig config) {
        String sql = "UPDATE t_model_config SET display_name = ?, provider_type = ?, base_url = ?, api_secret = ?, fallback_model = ?, timeout_ms = ?, status = ?, description = ? WHERE model_name = ?";
        return jdbcTemplate.update(sql, config.getDisplayName(), config.getProviderType(), config.getBaseUrl(),
                config.getApiSecret(), config.getFallbackModel(), config.getTimeoutMs(), config.getStatus(), config.getDescription(), config.getModelName());
    }

    public int deleteModelConfig(Long id) {
        String sql = "DELETE FROM t_model_config WHERE id = ?";
        return jdbcTemplate.update(sql, id);
    }

    // ==================== Audit & Usage ====================

    public void insertAudit(ChatAudit audit) {
        String sql = "INSERT INTO t_chat_audit (request_id, api_key, owner_name, model_name, ttft_ms, total_cost_ms, prompt_tokens, completion_tokens, http_status, error_msg) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        jdbcTemplate.update(sql, audit.getRequestId(), audit.getApiKey(), audit.getOwnerName(), audit.getModelName(),
                audit.getTtftMs(), audit.getTotalCostMs(), audit.getPromptTokens(), audit.getCompletionTokens(), audit.getHttpStatus(), audit.getErrorMsg());
    }

    public List<ChatAudit> listRecentAudits(int limit) {
        String sql = "SELECT * FROM t_chat_audit ORDER BY id DESC LIMIT ?";
        return jdbcTemplate.query(sql, new BeanPropertyRowMapper<>(ChatAudit.class), limit);
    }

    public void recordDailyUsage(UsageDaily usage) {
        String today = (usage.getStatDate() != null && !usage.getStatDate().isEmpty())
                ? usage.getStatDate()
                : LocalDate.now().toString();
        long promptToks = usage.getPromptTokens() != null ? usage.getPromptTokens() : 0L;
        long compToks = usage.getCompletionTokens() != null ? usage.getCompletionTokens() : 0L;
        long totalTokens = usage.getTotalTokens() != null ? usage.getTotalTokens() : (promptToks + compToks);

        String checkSql = "SELECT count(*) FROM t_usage_daily WHERE stat_date = ? AND api_key = ? AND model_name = ?";
        Integer count = jdbcTemplate.queryForObject(checkSql, Integer.class, today, usage.getApiKey(), usage.getModelName());
        if (count != null && count > 0) {
            String updateSql = "UPDATE t_usage_daily SET request_count = request_count + 1, prompt_tokens = prompt_tokens + ?, " +
                    "completion_tokens = completion_tokens + ?, total_tokens = total_tokens + ? WHERE stat_date = ? AND api_key = ? AND model_name = ?";
            jdbcTemplate.update(updateSql, promptToks, compToks, totalTokens, today, usage.getApiKey(), usage.getModelName());
        } else {
            String insertSql = "INSERT INTO t_usage_daily (stat_date, owner_name, department, api_key, model_name, request_count, prompt_tokens, completion_tokens, total_tokens) " +
                    "VALUES (?, ?, ?, ?, ?, 1, ?, ?, ?)";
            jdbcTemplate.update(insertSql, today, usage.getOwnerName(), usage.getDepartment(), usage.getApiKey(), usage.getModelName(), promptToks, compToks, totalTokens);
        }
    }

    public List<UsageDaily> listDailyUsage(int limit) {
        String sql = "SELECT * FROM t_usage_daily ORDER BY stat_date DESC, id DESC LIMIT ?";
        return jdbcTemplate.query(sql, new BeanPropertyRowMapper<>(UsageDaily.class), limit);
    }

    public Long getTotalTokensSum() {
        String sql = "SELECT COALESCE(SUM(total_tokens), 0) FROM t_usage_daily";
        return jdbcTemplate.queryForObject(sql, Long.class);
    }

    public Integer getTotalRequestsSum() {
        String sql = "SELECT COALESCE(SUM(request_count), 0) FROM t_usage_daily";
        return jdbcTemplate.queryForObject(sql, Integer.class);
    }

    public Double getAvgTtft() {
        String sql = "SELECT COALESCE(AVG(ttft_ms), 0) FROM t_chat_audit WHERE http_status = 200";
        return jdbcTemplate.queryForObject(sql, Double.class);
    }

    // ==================== Model Apply & Approval ====================

    public void insertModelApply(com.chatling.common.model.ModelApply apply) {
        String sql = "INSERT INTO t_model_apply (applicant_name, department, api_key, model_name, apply_reason, status) VALUES (?, ?, ?, ?, ?, ?)";
        jdbcTemplate.update(sql, apply.getApplicantName(), apply.getDepartment(), apply.getApiKey(), apply.getModelName(), apply.getApplyReason(), apply.getStatus() != null ? apply.getStatus() : 0);
    }

    public List<com.chatling.common.model.ModelApply> listModelApplies(Integer status) {
        if (status == null) {
            String sql = "SELECT * FROM t_model_apply ORDER BY id DESC";
            return jdbcTemplate.query(sql, new BeanPropertyRowMapper<>(com.chatling.common.model.ModelApply.class));
        } else {
            String sql = "SELECT * FROM t_model_apply WHERE status = ? ORDER BY id DESC";
            return jdbcTemplate.query(sql, new BeanPropertyRowMapper<>(com.chatling.common.model.ModelApply.class), status);
        }
    }

    public Optional<com.chatling.common.model.ModelApply> findModelApplyById(Long id) {
        String sql = "SELECT * FROM t_model_apply WHERE id = ?";
        List<com.chatling.common.model.ModelApply> list = jdbcTemplate.query(sql, new BeanPropertyRowMapper<>(com.chatling.common.model.ModelApply.class), id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public void updateModelApplyStatus(Long id, int status) {
        String sql = "UPDATE t_model_apply SET status = ?, updated_time = CURRENT_TIMESTAMP WHERE id = ?";
        jdbcTemplate.update(sql, status, id);
    }

    public void appendAllowedModel(String apiKey, String modelName) {
        Optional<ApiKey> keyOpt = findByApiKey(apiKey);
        if (keyOpt.isPresent()) {
            ApiKey key = keyOpt.get();
            String current = key.getAllowedModels();
            if (current == null || current.trim().isEmpty() || "*".equals(current.trim())) {
                current = modelName;
            } else {
                List<String> list = new ArrayList<>(Arrays.asList(current.split(",")));
                if (!list.contains(modelName)) {
                    list.add(modelName);
                }
                current = String.join(",", list);
            }
            String sql = "UPDATE t_api_key SET allowed_models = ?, updated_time = CURRENT_TIMESTAMP WHERE api_key = ?";
            jdbcTemplate.update(sql, current, apiKey);
        }
    }
}

