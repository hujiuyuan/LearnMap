package com.learnmap.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param provider 使用哪个 AI 提供方：stub（默认，离线假实现，不调用任何模型）、claude 或 deepseek
 * @param apiKey   从环境变量 LEARNMAP_AI_API_KEY 读取，不写进代码或配置文件
 * @param model    提供方的模型名；claude 默认 claude-opus-5，deepseek 默认 deepseek-chat
 * @param fallbacks claude：模型拒答时是否让服务端自动换模型重试（默认开）
 * @param baseUrl  deepseek：接口地址，默认 https://api.deepseek.com
 */
@ConfigurationProperties(prefix = "learnmap.ai")
public record AiProperties(String provider, String apiKey, String model, Boolean fallbacks, String baseUrl) {
}
