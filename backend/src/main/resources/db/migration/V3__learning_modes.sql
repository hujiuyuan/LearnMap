-- 学习方式：费曼讲解、刻意练习、交错练习、第一性原理（目标拆解 + 递归补缺），以及延时复测排期

CREATE TABLE feynman_attempt (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    learner_id         BIGINT   NOT NULL,
    node_id            BIGINT   NOT NULL,          -- 副本节点
    parent_attempt_id  BIGINT,                     -- 回答追问时指向上一次讲解
    explanation        TEXT     NOT NULL,          -- 打字内容，或照片 OCR 出来的文字
    from_image         BOOLEAN  NOT NULL DEFAULT FALSE,
    score              DOUBLE   NOT NULL,
    covered_json       TEXT,
    missing_json       TEXT,
    incorrect_json     TEXT,
    follow_ups_json    TEXT,
    grader             VARCHAR(32) NOT NULL,       -- 哪个 AI 提供方评的分
    needs_review       BOOLEAN  NOT NULL DEFAULT FALSE, -- 辅导者可复核
    created_at         DATETIME NOT NULL,
    CONSTRAINT fk_fa_learner FOREIGN KEY (learner_id) REFERENCES app_user (id),
    CONSTRAINT fk_fa_node    FOREIGN KEY (node_id)    REFERENCES knowledge_node (id),
    CONSTRAINT fk_fa_parent  FOREIGN KEY (parent_attempt_id) REFERENCES feynman_attempt (id)
);

-- 练习会话：刻意练习 / 交错练习 / 第一性原理 共用
CREATE TABLE practice_session (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    learner_id     BIGINT      NOT NULL,
    graph_id       BIGINT      NOT NULL,
    mode           VARCHAR(16) NOT NULL,           -- DELIBERATE / INTERLEAVED / FIRST_PRINCIPLES
    target_node_id BIGINT,                         -- 刻意练习的根因节点 / 第一性原理的目标节点
    phase          VARCHAR(16) NOT NULL,           -- 当前阶段，见 PracticePhase
    step           INT         NOT NULL,           -- 阶段内第几批
    status         VARCHAR(16) NOT NULL,           -- IN_PROGRESS / COMPLETED
    state_json     TEXT,                           -- 模式自己的状态（补缺路径、深度等）
    summary        TEXT,
    completed_at   DATETIME,
    created_at     DATETIME    NOT NULL,
    CONSTRAINT fk_ps_learner FOREIGN KEY (learner_id)     REFERENCES app_user (id),
    CONSTRAINT fk_ps_graph   FOREIGN KEY (graph_id)       REFERENCES knowledge_graph (id),
    CONSTRAINT fk_ps_target  FOREIGN KEY (target_node_id) REFERENCES knowledge_node (id)
);
CREATE INDEX idx_ps_learner ON practice_session (learner_id, graph_id);

CREATE TABLE practice_item (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id  BIGINT      NOT NULL,
    phase       VARCHAR(16) NOT NULL,
    step        INT         NOT NULL,
    ordinal     INT         NOT NULL,
    node_id     BIGINT      NOT NULL,
    question_id BIGINT      NOT NULL,
    answered    BOOLEAN     NOT NULL DEFAULT FALSE,
    correct     BOOLEAN,
    error_type  VARCHAR(24),
    answered_at DATETIME,
    created_at  DATETIME    NOT NULL,
    CONSTRAINT fk_pi_session  FOREIGN KEY (session_id)  REFERENCES practice_session (id),
    CONSTRAINT fk_pi_node     FOREIGN KEY (node_id)     REFERENCES knowledge_node (id),
    CONSTRAINT fk_pi_question FOREIGN KEY (question_id) REFERENCES question (id)
);
CREATE INDEX idx_pi_session ON practice_item (session_id);

-- 延时复测：学完 3 / 7 / 21 天测保持率；刻意练习 3 天后复验
CREATE TABLE scheduled_retest (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    learner_id   BIGINT      NOT NULL,
    node_id      BIGINT      NOT NULL,
    reason       VARCHAR(24) NOT NULL,             -- RETENTION_3D / RETENTION_7D / RETENTION_21D / DELIBERATE_RECHECK
    source_mode  VARCHAR(16),                      -- 学这个节点时用的学习方式，用于学习方式画像
    due_at       DATETIME    NOT NULL,
    done         BOOLEAN     NOT NULL DEFAULT FALSE,
    correct      BOOLEAN,
    done_at      DATETIME,
    created_at   DATETIME    NOT NULL,
    CONSTRAINT fk_rt_learner FOREIGN KEY (learner_id) REFERENCES app_user (id),
    CONSTRAINT fk_rt_node    FOREIGN KEY (node_id)    REFERENCES knowledge_node (id)
);
CREATE INDEX idx_rt_due ON scheduled_retest (learner_id, done, due_at);
