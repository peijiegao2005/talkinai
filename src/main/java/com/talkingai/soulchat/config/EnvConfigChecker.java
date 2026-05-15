package com.talkingai.soulchat.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * 环境配置检查器
 * 启动时检查关键配置是否正确加载
 */
@Slf4j
@Component
public class EnvConfigChecker implements CommandLineRunner {

    @Value("${llm.api-key:}")
    private String llmApiKey;

    @Value("${llm.base-url:}")
    private String llmBaseUrl;

    @Value("${llm.model:}")
    private String llmModel;

    @Override
    public void run(String... args) {
        log.info("========== 环境配置检查 ==========");

        // 检查 LLM 配置
        if (llmApiKey == null || llmApiKey.isEmpty()) {
            log.warn("❌ LLM API Key 未配置");
            log.warn("   请检查 .env 文件是否存在且包含 OPENAI_API_KEY");
            log.warn("   或者手动设置环境变量: $env:OPENAI_API_KEY=\"your-key\"");
        } else {
            log.info("✅ LLM API Key 已配置");
            log.info("   前缀: {}...", llmApiKey.substring(0, Math.min(10, llmApiKey.length())));
        }

        log.info("   Base URL: {}", llmBaseUrl);
        log.info("   Model: {}", llmModel);

        // 检查 .env 文件是否被加载
        String envApiKey = System.getenv("OPENAI_API_KEY");
        if (envApiKey != null && !envApiKey.isEmpty()) {
            log.info("✅ 系统环境变量 OPENAI_API_KEY 已设置");
        } else {
            log.warn("⚠️ 系统环境变量 OPENAI_API_KEY 未设置");
            log.warn("   如果使用了 spring-dotenv，请检查 .env 文件是否在项目根目录");
        }

        log.info("========== 配置检查结束 ==========");
    }
}
