package com.chatling.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {
        "com.chatling.common",
        "com.chatling.engine",
        "com.chatling.gateway",
        "com.chatling.admin",
        "com.chatling.bootstrap"
})
public class ChatlingGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChatlingGatewayApplication.class, args);
        System.out.println("=================================================================");
        System.out.println("🚀 灵犀 AI 平台与网关系统 (Chatling Gateway) 启动成功！");
        System.out.println("📱 浏览器访问控制台与体验广场: http://localhost:8088");
        System.out.println("⚡ 开放 OpenAI 兼容 API 入口: http://localhost:8088/v1/chat/completions");
        System.out.println("🔑 默认管理员测试 Key: sk-chatling-admin-demo888");
        System.out.println("=================================================================");
    }
}
