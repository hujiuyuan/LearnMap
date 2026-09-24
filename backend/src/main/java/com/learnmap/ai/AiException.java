package com.learnmap.ai;

/** 调用 AI 失败（key 不对、限流、服务出错、模型拒答等），接口返回 502 */
public class AiException extends RuntimeException {

    public AiException(String message) {
        super(message);
    }

    public AiException(String message, Throwable cause) {
        super(message, cause);
    }
}
