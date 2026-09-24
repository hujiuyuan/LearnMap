-- 同一本书生成了新版本图谱时，学习者副本的“合并 / 保留旧版 / 换成新版”选择

CREATE TABLE graph_merge_decision (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    copy_graph_id   BIGINT      NOT NULL,           -- 学习者副本
    from_graph_id   BIGINT      NOT NULL,           -- 副本原来基于的书本图谱版本
    to_graph_id     BIGINT      NOT NULL,           -- 新的书本图谱版本
    decision        VARCHAR(16) NOT NULL,           -- MERGE / KEEP / REPLACE
    decided_by      BIGINT      NOT NULL,
    summary         TEXT,
    created_at      DATETIME    NOT NULL,
    CONSTRAINT fk_merge_copy FOREIGN KEY (copy_graph_id) REFERENCES knowledge_graph (id),
    CONSTRAINT fk_merge_from FOREIGN KEY (from_graph_id) REFERENCES knowledge_graph (id),
    CONSTRAINT fk_merge_to   FOREIGN KEY (to_graph_id)   REFERENCES knowledge_graph (id)
);
CREATE INDEX idx_merge_copy ON graph_merge_decision (copy_graph_id, to_graph_id);
