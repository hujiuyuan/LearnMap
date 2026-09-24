-- 知图 LearnMap 核心数据模型（Phase 1a 第一批）
-- 兼容 MySQL 8 与 H2（MySQL 模式）。JSON 类字段统一用 TEXT 存，便于两边兼容。

-- ========== 用户与角色 ==========
CREATE TABLE app_user (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(64)  NOT NULL,
    display_name  VARCHAR(128) NOT NULL,
    role          VARCHAR(16)  NOT NULL,           -- LEARNER / TUTOR / OPS
    created_at    DATETIME     NOT NULL,
    CONSTRAINT uk_app_user_username UNIQUE (username)
);

-- 辅导者管理名下学习者（学习者可以不绑定）
CREATE TABLE tutor_learner (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    tutor_id    BIGINT   NOT NULL,
    learner_id  BIGINT   NOT NULL,
    created_at  DATETIME NOT NULL,
    CONSTRAINT uk_tutor_learner UNIQUE (tutor_id, learner_id),
    CONSTRAINT fk_tl_tutor   FOREIGN KEY (tutor_id)   REFERENCES app_user (id),
    CONSTRAINT fk_tl_learner FOREIGN KEY (learner_id) REFERENCES app_user (id)
);

-- ========== 书 ==========
CREATE TABLE book (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    title          VARCHAR(255) NOT NULL,
    author         VARCHAR(255),
    owner_id       BIGINT       NOT NULL,          -- 上传者
    uploader_role  VARCHAR(16)  NOT NULL,          -- LEARNER / TUTOR / OPS
    visibility     VARCHAR(16)  NOT NULL,          -- PRIVATE / TUTOR_GROUP / PUBLIC
    review_mode    VARCHAR(32)  NOT NULL,          -- NONE / REVIEW_BEFORE_PUBLISH
    content_kind   VARCHAR(16)  NOT NULL,          -- TEXT / IMAGE（图片类要先 OCR）
    file_format    VARCHAR(16)  NOT NULL,          -- EPUB / TXT / MARKDOWN / PDF / DOCX / IMAGES
    file_key       VARCHAR(512),                   -- 对象存储里的原书文件
    status         VARCHAR(16)  NOT NULL,          -- UPLOADED / PROCESSING / READY / FAILED
    status_message VARCHAR(1024),
    created_at     DATETIME     NOT NULL,
    CONSTRAINT fk_book_owner FOREIGN KEY (owner_id) REFERENCES app_user (id)
);

-- 目录结构：章 → 节 → 小节
CREATE TABLE book_section (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    book_id    BIGINT       NOT NULL,
    parent_id  BIGINT,
    title      VARCHAR(512) NOT NULL,
    level      INT          NOT NULL,              -- 1 = 章
    ordinal    INT          NOT NULL,              -- 全书内的顺序
    created_at DATETIME     NOT NULL,
    CONSTRAINT fk_section_book   FOREIGN KEY (book_id)   REFERENCES book (id),
    CONSTRAINT fk_section_parent FOREIGN KEY (parent_id) REFERENCES book_section (id)
);

-- 原文块：每个节点都要能回溯到原文出处
CREATE TABLE source_chunk (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    book_id    BIGINT   NOT NULL,
    section_id BIGINT,
    ordinal    INT      NOT NULL,
    content    TEXT     NOT NULL,
    page_from  INT,
    page_to    INT,
    created_at DATETIME NOT NULL,
    CONSTRAINT fk_chunk_book    FOREIGN KEY (book_id)    REFERENCES book (id),
    CONSTRAINT fk_chunk_section FOREIGN KEY (section_id) REFERENCES book_section (id)
);

-- ========== 图谱 ==========
-- 一本书的书本图谱（MASTER，可多个版本）+ 每个学习者自己的副本（LEARNER_COPY）
CREATE TABLE knowledge_graph (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    book_id          BIGINT      NOT NULL,
    kind             VARCHAR(16) NOT NULL,         -- MASTER / LEARNER_COPY
    version_no       INT         NOT NULL,         -- MASTER 的第几次生成；副本沿用来源版本号
    owner_learner_id BIGINT,                       -- LEARNER_COPY 的主人
    source_graph_id  BIGINT,                       -- 副本从哪个 MASTER 复制
    status           VARCHAR(16) NOT NULL,         -- DRAFT / PUBLISHED / SUPERSEDED
    review_status    VARCHAR(16) NOT NULL,         -- UNREVIEWED / REVIEWED
    created_at       DATETIME    NOT NULL,
    CONSTRAINT fk_graph_book    FOREIGN KEY (book_id)          REFERENCES book (id),
    CONSTRAINT fk_graph_owner   FOREIGN KEY (owner_learner_id) REFERENCES app_user (id),
    CONSTRAINT fk_graph_source  FOREIGN KEY (source_graph_id)  REFERENCES knowledge_graph (id)
);
CREATE INDEX idx_graph_owner_book ON knowledge_graph (owner_learner_id, book_id);

CREATE TABLE knowledge_node (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    graph_id          BIGINT       NOT NULL,
    origin_node_id    BIGINT,                      -- 副本节点对应的 MASTER 节点
    section_id        BIGINT,
    name              VARCHAR(255) NOT NULL,
    definition        TEXT,
    node_type         VARCHAR(16)  NOT NULL,       -- FACT / CONCEPT / PRINCIPLE / METHOD / SYNTHESIS
    source_confidence DOUBLE       NOT NULL,       -- 抽取置信度 0~1
    review_status     VARCHAR(16)  NOT NULL,       -- PENDING / CONFIRMED / REJECTED
    deleted           BOOLEAN      NOT NULL DEFAULT FALSE, -- 学习者在自己副本里删除
    ordinal           INT          NOT NULL,       -- 书内出现顺序
    created_at        DATETIME     NOT NULL,
    CONSTRAINT fk_node_graph   FOREIGN KEY (graph_id)       REFERENCES knowledge_graph (id),
    CONSTRAINT fk_node_origin  FOREIGN KEY (origin_node_id) REFERENCES knowledge_node (id),
    CONSTRAINT fk_node_section FOREIGN KEY (section_id)     REFERENCES book_section (id)
);
CREATE INDEX idx_node_graph ON knowledge_node (graph_id);

-- PREREQUISITE：from 是 to 的前置；RELATED；CONTRASTS（易混淆）
CREATE TABLE node_relation (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    graph_id      BIGINT      NOT NULL,
    from_node_id  BIGINT      NOT NULL,
    to_node_id    BIGINT      NOT NULL,
    relation_type VARCHAR(16) NOT NULL,
    confidence    DOUBLE      NOT NULL,
    evidence      TEXT,                            -- 哪段原文、哪个信号
    review_status VARCHAR(16) NOT NULL,            -- PENDING / CONFIRMED / REJECTED
    disputed      BOOLEAN     NOT NULL DEFAULT FALSE, -- 学习者标记“这条前置关系不对”
    created_at    DATETIME    NOT NULL,
    CONSTRAINT fk_rel_graph FOREIGN KEY (graph_id)     REFERENCES knowledge_graph (id),
    CONSTRAINT fk_rel_from  FOREIGN KEY (from_node_id) REFERENCES knowledge_node (id),
    CONSTRAINT fk_rel_to    FOREIGN KEY (to_node_id)   REFERENCES knowledge_node (id)
);
CREATE INDEX idx_rel_graph ON node_relation (graph_id);

CREATE TABLE node_source (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    node_id    BIGINT   NOT NULL,
    chunk_id   BIGINT   NOT NULL,
    created_at DATETIME NOT NULL,
    CONSTRAINT fk_ns_node  FOREIGN KEY (node_id)  REFERENCES knowledge_node (id),
    CONSTRAINT fk_ns_chunk FOREIGN KEY (chunk_id) REFERENCES source_chunk (id)
);

-- ========== 题库（跟着书生成，挂在 MASTER 节点上）==========
CREATE TABLE question (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    book_id       BIGINT      NOT NULL,
    node_id       BIGINT      NOT NULL,            -- 主考查节点（MASTER 图谱里的）
    question_type VARCHAR(16) NOT NULL,            -- SINGLE_CHOICE / TRUE_FALSE / FILL_BLANK / STEP_FILL
    stem          TEXT        NOT NULL,
    options_json  TEXT,
    answer        TEXT        NOT NULL,
    difficulty    DOUBLE      NOT NULL,            -- 0~1，上线后按真实作答校准
    source        VARCHAR(16) NOT NULL,            -- BOOK / AI / MANUAL / WRONG_BOOK
    verified      BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at    DATETIME    NOT NULL,
    CONSTRAINT fk_q_book FOREIGN KEY (book_id) REFERENCES book (id),
    CONSTRAINT fk_q_node FOREIGN KEY (node_id) REFERENCES knowledge_node (id)
);
CREATE INDEX idx_q_node ON question (node_id);

-- 一题多解
CREATE TABLE question_solution (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    question_id BIGINT       NOT NULL,
    name        VARCHAR(255) NOT NULL,
    ordinal     INT          NOT NULL,
    created_at  DATETIME     NOT NULL,
    CONSTRAINT fk_qs_question FOREIGN KEY (question_id) REFERENCES question (id)
);

-- 解法步骤 ↔ 知识点
CREATE TABLE solution_step (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    solution_id BIGINT   NOT NULL,
    ordinal     INT      NOT NULL,
    description TEXT     NOT NULL,
    node_id     BIGINT   NOT NULL,                 -- MASTER 节点
    answer      TEXT,                              -- 分步填空时该步的答案
    created_at  DATETIME NOT NULL,
    CONSTRAINT fk_step_solution FOREIGN KEY (solution_id) REFERENCES question_solution (id),
    CONSTRAINT fk_step_node     FOREIGN KEY (node_id)     REFERENCES knowledge_node (id)
);

-- ========== 证据与掌握度 ==========
-- 所有学习活动统一写成证据；node_id 指向学习者自己副本里的节点
CREATE TABLE evidence (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    learner_id      BIGINT      NOT NULL,
    node_id         BIGINT      NOT NULL,
    activity_type   VARCHAR(16) NOT NULL,          -- DIAGNOSTIC / PRACTICE / CARD / FEYNMAN / INTERLEAVED / QUIZ / RETEST
    question_id     BIGINT,
    solution_id     BIGINT,
    result          VARCHAR(16) NOT NULL,          -- CORRECT / INCORRECT / PARTIAL
    score           DOUBLE      NOT NULL,          -- 0~1
    difficulty      DOUBLE      NOT NULL,
    duration_sec    INT,
    error_type      VARCHAR(24),                   -- KNOWLEDGE_GAP / CONCEPT_CONFUSION / CALCULATION / READING / CARELESS / WRONG_METHOD
    self_confidence VARCHAR(16),                   -- SURE / UNSURE / DONT_KNOW
    created_at      DATETIME    NOT NULL,
    CONSTRAINT fk_ev_learner  FOREIGN KEY (learner_id)  REFERENCES app_user (id),
    CONSTRAINT fk_ev_node     FOREIGN KEY (node_id)     REFERENCES knowledge_node (id),
    CONSTRAINT fk_ev_question FOREIGN KEY (question_id) REFERENCES question (id),
    CONSTRAINT fk_ev_solution FOREIGN KEY (solution_id) REFERENCES question_solution (id)
);
CREATE INDEX idx_ev_learner_node ON evidence (learner_id, node_id);

-- 知识画像：每个节点的 Mastery / Confidence（由证据按规则计算，不用 AI）
CREATE TABLE node_mastery (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    learner_id     BIGINT   NOT NULL,
    node_id        BIGINT   NOT NULL,
    mastery        DOUBLE   NOT NULL,
    confidence     DOUBLE   NOT NULL,
    evidence_count INT      NOT NULL,
    updated_at     DATETIME NOT NULL,
    CONSTRAINT uk_mastery UNIQUE (learner_id, node_id),
    CONSTRAINT fk_m_learner FOREIGN KEY (learner_id) REFERENCES app_user (id),
    CONSTRAINT fk_m_node    FOREIGN KEY (node_id)    REFERENCES knowledge_node (id)
);
