-- 测试用 H2 数据库表初始化脚本
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

MERGE INTO `t_model_config` (`id`, `model_name`, `display_name`, `provider_type`, `base_url`, `api_secret`, `fallback_model`, `timeout_ms`, `status`, `description`) 
KEY(`model_name`) VALUES 
(1, 'chatling-turbo', '灵犀自研大模型 (ChatLing-Turbo)', 'mock', '', '', NULL, 60000, 1, '测试模型');
ALTER TABLE `t_model_config` ALTER COLUMN `id` RESTART WITH 10;

CREATE TABLE IF NOT EXISTS `t_model_apply` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `applicant_name` VARCHAR(32) NOT NULL,
  `department` VARCHAR(64) NOT NULL,
  `api_key` VARCHAR(64) NOT NULL,
  `model_name` VARCHAR(64) NOT NULL,
  `apply_reason` VARCHAR(256),
  `status` INT NOT NULL DEFAULT 0,
  `created_time` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  `updated_time` TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

