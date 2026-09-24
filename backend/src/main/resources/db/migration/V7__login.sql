-- 简单登录：用户名 + 密码，登录后发一个随机令牌

ALTER TABLE app_user ADD COLUMN password_hash VARCHAR(100);

CREATE TABLE auth_token (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    token       VARCHAR(64) NOT NULL,
    user_id     BIGINT      NOT NULL,
    expires_at  DATETIME    NOT NULL,
    created_at  DATETIME    NOT NULL,
    CONSTRAINT uk_token UNIQUE (token),
    CONSTRAINT fk_token_user FOREIGN KEY (user_id) REFERENCES app_user (id)
);
