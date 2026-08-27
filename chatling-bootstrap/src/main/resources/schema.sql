-- 1. API Key 凭证与权限表
CREATE TABLE IF NOT EXISTS `t_api_key` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `api_key` VARCHAR(64) NOT NULL UNIQUE,
  `key_name` VARCHAR(64) NOT NULL,
  `owner_name` VARCHAR(32) NOT NULL,
  `department` VARCHAR(64) NOT NULL,
  `allowed_models` VARCHAR(512) NOT NULL DEFAULT '*',
  `tpm_limit` INT NOT NULL DEFAULT 60000,
  `qps_limit` INT NOT NULL DEFAULT 20,
  `total_quota` BIGINT NOT NULL DEFAULT -1,
  `used_quota` BIGINT NOT NULL DEFAULT 0,
  `status` INT NOT NULL DEFAULT 1,
  `created_time` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  `updated_time` TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 2. 模型配置与路由表
CREATE TABLE IF NOT EXISTS `t_model_config` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `model_name` VARCHAR(64) NOT NULL UNIQUE,
  `display_name` VARCHAR(64) NOT NULL,
  `provider_type` VARCHAR(32) NOT NULL,
  `base_url` VARCHAR(256) NOT NULL,
  `api_secret` VARCHAR(256),
  `fallback_model` VARCHAR(64),
  `timeout_ms` INT NOT NULL DEFAULT 60000,
  `status` INT NOT NULL DEFAULT 1,
  `description` VARCHAR(256),
  `created_time` TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 3. 每日用量统计表
CREATE TABLE IF NOT EXISTS `t_usage_daily` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `stat_date` VARCHAR(10) NOT NULL,
  `owner_name` VARCHAR(32) NOT NULL,
  `department` VARCHAR(64) NOT NULL,
  `api_key` VARCHAR(64) NOT NULL,
  `model_name` VARCHAR(64) NOT NULL,
  `request_count` INT NOT NULL DEFAULT 0,
  `prompt_tokens` BIGINT NOT NULL DEFAULT 0,
  `completion_tokens` BIGINT NOT NULL DEFAULT 0,
  `total_tokens` BIGINT NOT NULL DEFAULT 0
);

-- 4. 调用审计流水日志表
CREATE TABLE IF NOT EXISTS `t_chat_audit` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `request_id` VARCHAR(64) NOT NULL,
  `api_key` VARCHAR(64) NOT NULL,
  `owner_name` VARCHAR(32) NOT NULL,
  `model_name` VARCHAR(64) NOT NULL,
  `ttft_ms` INT NOT NULL DEFAULT 0,
  `total_cost_ms` INT NOT NULL DEFAULT 0,
  `prompt_tokens` INT NOT NULL DEFAULT 0,
  `completion_tokens` INT NOT NULL DEFAULT 0,
  `http_status` INT NOT NULL DEFAULT 200,
  `error_msg` VARCHAR(512),
  `created_time` TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 初始化默认模型配置 (精选 6 大真实商用大模型矩阵，密钥由管理员在控制台维护或环境变量注入)
MERGE INTO `t_model_config` (`id`, `model_name`, `display_name`, `provider_type`, `base_url`, `api_secret`, `fallback_model`, `timeout_ms`, `status`, `description`) 
KEY(`id`) VALUES 
(1, 'glm-4-flash', '智谱 GLM-4 Flash (官方免费极速大模型)', 'zhipu', 'https://open.bigmodel.cn/api/paas/v4', 'your_zhipu_api_key_here', 'qwen-plus', 60000, 1, '智谱 AI 官方推出的极速免费商用大模型，中文推理与代码生成能力优异'),
(2, 'qwen-plus', '阿里通义千问 Plus (阿里云官方旗舰)', 'dashscope', 'https://dashscope.aliyuncs.com/compatible-mode/v1', 'your_dashscope_api_key_here', 'glm-4-flash', 60000, 1, '阿里云通义千问 Plus 旗舰商用模型，中文综合能力与逻辑理解极强'),
(3, 'gemini-2.5-flash', 'Google Gemini 2.5 Flash (谷歌官方极速多模态)', 'gemini', 'https://generativelanguage.googleapis.com/v1beta/openai', 'your_gemini_api_key_here', 'glm-4-flash', 60000, 1, 'Google 官方极速多模态旗舰大模型'),
(4, 'moonshot-v1-8k', 'Kimi (月之暗面 Moonshot-v1-8k)', 'openai', 'https://api.moonshot.cn/v1', 'your_moonshot_api_key_here', 'glm-4-flash', 60000, 1, '月之暗面 Kimi 官方商用模型，拥有超强中文理解与长文本推理能力'),
(5, 'ep-m-20260414104415-9rcgn', '火山方舟 Coding Plan (DeepSeek/Doubao)', 'volcengine', 'https://ark.cn-beijing.volces.com/api/v3', 'your_volcengine_api_key_here', 'glm-4-flash', 60000, 1, '火山引擎方舟 Coding Plan 官方专属推理接入点'),
(6, 'deepseek-chat', 'DeepSeek-V3 官方商业大模型', 'deepseek', 'https://api.deepseek.com', 'your_deepseek_api_key_here', 'glm-4-flash', 60000, 1, 'DeepSeek 官方商业旗舰模型，拥有极强代码与中文推理能力');

-- 初始化默认管理员测试 Key (已开通全部 6 大模型权限)
MERGE INTO `t_api_key` (`id`, `api_key`, `key_name`, `owner_name`, `department`, `allowed_models`, `tpm_limit`, `qps_limit`, `total_quota`, `used_quota`, `status`) 
KEY(`api_key`) VALUES 
(1, 'sk-chatling-admin-demo888', '系统演示体验Key', 'admin', 'AI研发部', 'glm-4-flash,qwen-plus,gemini-2.5-flash,moonshot-v1-8k,ep-m-20260414104415-9rcgn,deepseek-chat', 100000, 50, 10000000, 3250, 1);

-- 5. 模型权限审批工单表
CREATE TABLE IF NOT EXISTS `t_model_apply` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `applicant_name` VARCHAR(32) NOT NULL,
  `department` VARCHAR(64) NOT NULL,
  `api_key` VARCHAR(64) NOT NULL,
  `model_name` VARCHAR(64) NOT NULL,
  `apply_reason` VARCHAR(256),
  `status` INT NOT NULL DEFAULT 0, -- 0-待审批, 1-已通过, 2-已驳回
  `created_time` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  `updated_time` TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 初始化一条待审批测试单
MERGE INTO `t_model_apply` (`id`, `applicant_name`, `department`, `api_key`, `model_name`, `apply_reason`, `status`)
KEY(`id`) VALUES
(1, 'zhangsan', '房产事业部', 'sk-chatling-admin-demo888', 'deepseek-v3', '房源描述生成场景测试，需申请 DeepSeek 权限', 0);

