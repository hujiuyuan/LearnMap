-- 学习目标、学习计划（每日任务）、番茄钟

CREATE TABLE study_goal (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    learner_id       BIGINT      NOT NULL,
    graph_id         BIGINT      NOT NULL,
    scope            VARCHAR(16) NOT NULL,          -- BOOK / CHAPTERS / WEAK
    chapter_ids      TEXT,                          -- scope=CHAPTERS 时的章 id，逗号分隔
    deadline         DATE        NOT NULL,          -- 考试日期等
    weekday_minutes  INT         NOT NULL,
    weekend_minutes  INT         NOT NULL,
    main_style       VARCHAR(16) NOT NULL,          -- UNDERSTAND / MEMORIZE / PRACTICE
    rhythm           VARCHAR(16) NOT NULL,          -- POMODORO / MICRO / LONG
    active           BOOLEAN     NOT NULL,
    created_by       BIGINT      NOT NULL,          -- 学习者本人或他的辅导者
    warning          TEXT,                          -- 最近一次排计划的提示（例如考前学不完）
    planned_at       DATETIME,
    created_at       DATETIME    NOT NULL,
    CONSTRAINT fk_goal_learner FOREIGN KEY (learner_id) REFERENCES app_user (id),
    CONSTRAINT fk_goal_graph   FOREIGN KEY (graph_id)   REFERENCES knowledge_graph (id)
);
CREATE INDEX idx_goal_learner ON study_goal (learner_id, active);

CREATE TABLE plan_task (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    goal_id     BIGINT       NOT NULL,
    learner_id  BIGINT       NOT NULL,
    plan_date   DATE         NOT NULL,
    block_no    INT          NOT NULL,              -- 第几个番茄 / 微学习块
    ordinal     INT          NOT NULL,
    task_type   VARCHAR(24)  NOT NULL,
    node_id     BIGINT,
    card_count  INT,
    minutes     INT          NOT NULL,
    reason      VARCHAR(255),                       -- 为什么给这个节点配这个活动
    status      VARCHAR(16)  NOT NULL,              -- TODO / DONE / SKIPPED
    done_at     DATETIME,
    created_at  DATETIME     NOT NULL,
    CONSTRAINT fk_task_goal    FOREIGN KEY (goal_id)    REFERENCES study_goal (id),
    CONSTRAINT fk_task_learner FOREIGN KEY (learner_id) REFERENCES app_user (id),
    CONSTRAINT fk_task_node    FOREIGN KEY (node_id)    REFERENCES knowledge_node (id)
);
CREATE INDEX idx_task_day ON plan_task (learner_id, plan_date);

CREATE TABLE pomodoro_session (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    learner_id      BIGINT   NOT NULL,
    task_id         BIGINT,
    planned_minutes INT      NOT NULL,
    started_at      DATETIME NOT NULL,
    ended_at        DATETIME,
    completed       BOOLEAN  NOT NULL DEFAULT FALSE,
    interruptions   INT      NOT NULL DEFAULT 0,
    created_at      DATETIME NOT NULL,
    CONSTRAINT fk_pomo_learner FOREIGN KEY (learner_id) REFERENCES app_user (id),
    CONSTRAINT fk_pomo_task    FOREIGN KEY (task_id)    REFERENCES plan_task (id)
);
CREATE INDEX idx_pomo_learner ON pomodoro_session (learner_id, started_at);
