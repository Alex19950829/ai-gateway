package com.chatling.bootstrap.config;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

/**
 * 本地环境真实商用大模型密钥自动装载器
 * 自动从 gitignore 忽略的本地 data/secrets.properties 文件中加载真实 Key 并写入本地 H2 数据库，
 * 彻底杜绝在 yml / schema.sql 中出现明文导致 Git 泄露。
 */
@Slf4j
@Component
@Order(100)
@NoArgsConstructor
@AllArgsConstructor
public class LocalDatabaseSecretLoader implements CommandLineRunner {

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        File secretFile = new File("data/secrets.properties");
        if (!secretFile.exists()) {
            log.info("==> [LocalSecretLoader] data/secrets.properties not found, skip auto secret injection.");
            return;
        }

        Properties props = new Properties();
        try (InputStream in = new FileInputStream(secretFile)) {
            props.load(in);
            log.info("==> [LocalSecretLoader] Loaded {} secret keys from local data/secrets.properties.", props.size());

            int updated = 0;
            for (String modelName : props.stringPropertyNames()) {
                String realSecret = props.getProperty(modelName);
                if (realSecret != null && !realSecret.trim().isEmpty()) {
                    String updateSql = "UPDATE t_model_config SET api_secret = ? WHERE model_name = ? AND (api_secret IS NULL OR api_secret LIKE 'your_%' OR api_secret = '')";
                    int rows = jdbcTemplate.update(updateSql, realSecret.trim(), modelName.trim());
                    if (rows > 0) {
                        updated += rows;
                        log.info("==> [LocalSecretLoader] Auto injected real secret for model [{}] into local DB (secretLen={})", modelName, realSecret.trim().length());
                    }
                }
            }
            log.info("==> [LocalSecretLoader] Local DB secret initialization finished. (Updated {} models)", updated);
        } catch (Exception e) {
            log.warn("==> [LocalSecretLoader] Failed to read data/secrets.properties: {}", e.getMessage());
        }
    }
}
