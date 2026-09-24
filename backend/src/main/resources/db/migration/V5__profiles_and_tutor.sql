-- 画像与辅导者：题目的能力标注、辅导者 / 系统对节点的标记、费曼讲解的人工复核

ALTER TABLE question ADD COLUMN abilities VARCHAR(255);  -- 逗号分隔的 Ability

CREATE TABLE learner_flag (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    learner_id   BIGINT      NOT NULL,
    node_id      BIGINT      NOT NULL,              -- 学习者图谱副本上的节点
    kind         VARCHAR(24) NOT NULL,              -- DIAGNOSIS_WRONG / REALLY_WEAK / STUCK
    note         VARCHAR(500),
    created_by   BIGINT,                            -- 空 = 系统自动标记
    resolved     BOOLEAN     NOT NULL DEFAULT FALSE,
    resolved_at  DATETIME,
    created_at   DATETIME    NOT NULL,
    CONSTRAINT fk_flag_learner FOREIGN KEY (learner_id) REFERENCES app_user (id),
    CONSTRAINT fk_flag_node    FOREIGN KEY (node_id)    REFERENCES knowledge_node (id)
);
CREATE INDEX idx_flag_learner ON learner_flag (learner_id, resolved);

ALTER TABLE feynman_attempt ADD COLUMN reviewed_by BIGINT;
ALTER TABLE feynman_attempt ADD COLUMN reviewed_score DOUBLE;
ALTER TABLE feynman_attempt ADD COLUMN review_note VARCHAR(500);
ALTER TABLE feynman_attempt ADD COLUMN reviewed_at DATETIME;
