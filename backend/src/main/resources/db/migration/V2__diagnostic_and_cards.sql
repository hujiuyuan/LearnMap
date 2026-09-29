-- 入门诊断（三轮：广度扫描 → 定向诊断 → 验证）与复习卡片（FSRS 排期）

CREATE TABLE diagnostic_session (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    learner_id    BIGINT      NOT NULL,
    graph_id      BIGINT      NOT NULL,            -- 学习者自己的图谱副本
    current_round INT         NOT NULL,            -- 1 广度扫描 / 2 定向诊断 / 3 验证
    status        VARCHAR(16) NOT NULL,            -- IN_PROGRESS / COMPLETED
    completed_at  DATETIME,
    created_at    DATETIME    NOT NULL,
    CONSTRAINT fk_ds_learner FOREIGN KEY (learner_id) REFERENCES app_user (id),
    CONSTRAINT fk_ds_graph   FOREIGN KEY (graph_id)   REFERENCES knowledge_graph (id)
);
CREATE INDEX idx_ds_learner ON diagnostic_session (learner_id, graph_id);

CREATE TABLE diagnostic_item (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id  BIGINT   NOT NULL,
    round_no    INT      NOT NULL,
    ordinal     INT      NOT NULL,
    node_id     BIGINT   NOT NULL,                 -- 副本节点
    question_id BIGINT   NOT NULL,
    answered    BOOLEAN  NOT NULL DEFAULT FALSE,
    correct     BOOLEAN,
    answered_at DATETIME,
    created_at  DATETIME NOT NULL,
    CONSTRAINT fk_di_session  FOREIGN KEY (session_id)  REFERENCES diagnostic_session (id),
    CONSTRAINT fk_di_node     FOREIGN KEY (node_id)     REFERENCES knowledge_node (id),
    CONSTRAINT fk_di_question FOREIGN KEY (question_id) REFERENCES question (id)
);
CREATE INDEX idx_di_session ON diagnostic_item (session_id);

-- 复习卡片：每个节点自动生成，带 FSRS 排期状态
CREATE TABLE review_card (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    learner_id     BIGINT       NOT NULL,
    node_id        BIGINT       NOT NULL,          -- 副本节点
    front          TEXT         NOT NULL,
    back           TEXT         NOT NULL,
    source         VARCHAR(16)  NOT NULL,          -- AUTO（按节点生成）/ CORNELL（康奈尔线索栏，1b）
    state          VARCHAR(16)  NOT NULL,          -- NEW / REVIEW / RELEARNING
    stability      DOUBLE       NOT NULL,          -- FSRS 记忆稳定性（天）
    difficulty     DOUBLE       NOT NULL,          -- FSRS 难度 1~10
    reps           INT          NOT NULL,
    lapses         INT          NOT NULL,
    due_at         DATETIME     NOT NULL,
    last_review_at DATETIME,
    created_at     DATETIME     NOT NULL,
    CONSTRAINT fk_card_learner FOREIGN KEY (learner_id) REFERENCES app_user (id),
    CONSTRAINT fk_card_node    FOREIGN KEY (node_id)    REFERENCES knowledge_node (id)
);
CREATE INDEX idx_card_due ON review_card (learner_id, due_at);
