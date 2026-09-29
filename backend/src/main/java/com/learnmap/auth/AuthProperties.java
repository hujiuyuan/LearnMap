package com.learnmap.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param allowUserIdHeader 开发 / 测试时允许直接用请求头 X-User-Id 指定当前用户（dev 模式的演示切换、接口测试用）；
 *                          生产环境必须关掉，只认登录令牌
 * @param tokenDays         登录令牌有效天数
 */
@ConfigurationProperties(prefix = "learnmap.auth")
public record AuthProperties(boolean allowUserIdHeader, Integer tokenDays) {

    public int days() {
        return tokenDays == null || tokenDays <= 0 ? 30 : tokenDays;
    }
}
