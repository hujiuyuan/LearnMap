package com.learnmap.common;

/**
 * 登录暂未实现（MVP 不做复杂登录）。开发阶段用请求头 X-User-Id 指定当前用户。
 * 控制器通过 {@code @RequestHeader(CurrentUser.HEADER)} 取当前用户，之后接入真正的认证时统一替换。
 */
public final class CurrentUser {

    public static final String HEADER = "X-User-Id";

    private CurrentUser() {
    }
}
