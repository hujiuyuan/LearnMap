package com.learnmap.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param provider 使用哪个 AI 提供方：stub（默认，离线假实现）；真实提供方接入后在这里切换
 * @param apiKey   从环境变量 LEARNMAP_AI_API_KEY 读取，不写进代码或配置文件
 * @param model    提供方的模型名
 */
@ConfigurationProperties(prefix = "learnmap.ai")
public record AiProperties(String provider, String apiKey, String model) {
}
