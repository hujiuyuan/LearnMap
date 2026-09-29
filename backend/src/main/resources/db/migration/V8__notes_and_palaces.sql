-- 1b：笔记（康奈尔 / 卡片盒 / MOC，挂到图谱节点）和记忆宫殿

CREATE TABLE note (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    owner_id         BIGINT       NOT NULL,
    kind             VARCHAR(16)  NOT NULL,          -- CORNELL / ZETTEL / MOC
    title            VARCHAR(200) NOT NULL,
    cues             TEXT,                           -- 康奈尔线索栏：一行一个问题或关键词
    body             TEXT,                           -- 康奈尔笔记栏 / 卡片正文 / MOC 正文，[[标题]] 是双向链接
    summary          TEXT,                           -- 康奈尔总结栏
    summary_feedback TEXT,                           -- AI 对总结栏的反馈（JSON）
    book_id          BIGINT,
    section_id       BIGINT,                         -- MOC 对应的章
    updated_at       DATETIME     NOT NULL,
    created_at       DATETIME     NOT NULL,
    CONSTRAINT fk_note_owner FOREIGN KEY (owner_id) REFERENCES app_user (id)
);
CREATE INDEX idx_note_owner ON note (owner_id, kind);

CREATE TABLE note_node (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    note_id     BIGINT   NOT NULL,
    node_id     BIGINT   NOT NULL,                   -- 学习者副本里的节点
    created_at  DATETIME NOT NULL,
    CONSTRAINT uk_note_node UNIQUE (note_id, node_id),
    CONSTRAINT fk_nn_note FOREIGN KEY (note_id) REFERENCES note (id),
    CONSTRAINT fk_nn_node FOREIGN KEY (node_id) REFERENCES knowledge_node (id)
);
CREATE INDEX idx_note_node_node ON note_node (node_id);

-- 双向链接只存一条，两个方向都能查到
CREATE TABLE note_link (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    from_note_id  BIGINT      NOT NULL,
    to_note_id    BIGINT      NOT NULL,
    source        VARCHAR(16) NOT NULL,              -- WIKI（正文里写了 [[标题]]）/ MANUAL / SUGGESTED（采纳了 AI 建议）
    created_at    DATETIME    NOT NULL,
    CONSTRAINT uk_note_link UNIQUE (from_note_id, to_note_id),
    CONSTRAINT fk_nl_from FOREIGN KEY (from_note_id) REFERENCES note (id),
    CONSTRAINT fk_nl_to   FOREIGN KEY (to_note_id)   REFERENCES note (id)
);
CREATE INDEX idx_note_link_to ON note_link (to_note_id);

-- 康奈尔线索栏生成的主动回忆卡片记下来自哪篇笔记
ALTER TABLE review_card ADD COLUMN note_id BIGINT;

CREATE TABLE memory_palace (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    owner_id        BIGINT       NOT NULL,
    name            VARCHAR(100) NOT NULL,
    place           VARCHAR(200),                    -- 用的是哪个熟悉的地方
    graph_id        BIGINT,                          -- 挂的内容来自哪张图谱
    status          VARCHAR(16)  NOT NULL,           -- BUILDING（定地点）/ DRILLING（走熟路线）/ READY（建成，可以挂内容）
    forward_ok      BOOLEAN      NOT NULL,
    backward_ok     BOOLEAN      NOT NULL,
    random_ok       INT          NOT NULL,           -- 随机问“下一个是什么”连续答对的次数
    state           VARCHAR(16)  NOT NULL,           -- 重走宫殿的 FSRS 状态
    stability       DOUBLE       NOT NULL,
    difficulty      DOUBLE       NOT NULL,
    reps            INT          NOT NULL,
    due_at          DATETIME,
    last_walk_at    DATETIME,
    created_at      DATETIME     NOT NULL,
    CONSTRAINT fk_palace_owner FOREIGN KEY (owner_id) REFERENCES app_user (id)
);

CREATE TABLE palace_locus (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    palace_id   BIGINT       NOT NULL,
    ordinal     INT          NOT NULL,
    name        VARCHAR(100) NOT NULL,
    created_at  DATETIME     NOT NULL,
    CONSTRAINT uk_locus UNIQUE (palace_id, ordinal),
    CONSTRAINT fk_locus_palace FOREIGN KEY (palace_id) REFERENCES memory_palace (id)
);

CREATE TABLE palace_placement (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    palace_id   BIGINT   NOT NULL,
    locus_id    BIGINT   NOT NULL,
    node_id     BIGINT,                              -- 挂的是图谱节点时填
    content     TEXT     NOT NULL,                   -- 要记住的内容
    image       TEXT,                                -- 学习者最终用的画面
    ai_image    TEXT,                                -- AI 建议的画面
    created_at  DATETIME NOT NULL,
    CONSTRAINT uk_placement_locus UNIQUE (locus_id),
    CONSTRAINT fk_pp_palace FOREIGN KEY (palace_id) REFERENCES memory_palace (id),
    CONSTRAINT fk_pp_locus  FOREIGN KEY (locus_id)  REFERENCES palace_locus (id)
);

CREATE TABLE palace_walk (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    palace_id   BIGINT      NOT NULL,
    kind        VARCHAR(16) NOT NULL,                -- FORWARD / BACKWARD / NEXT（走熟路线）/ RECALL（回忆内容）
    correct     INT         NOT NULL,
    total       INT         NOT NULL,
    created_at  DATETIME    NOT NULL,
    CONSTRAINT fk_walk_palace FOREIGN KEY (palace_id) REFERENCES memory_palace (id)
);
