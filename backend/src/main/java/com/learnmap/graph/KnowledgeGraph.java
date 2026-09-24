package com.learnmap.graph;

import com.learnmap.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "knowledge_graph")
public class KnowledgeGraph extends BaseEntity {

    @Column(name = "book_id", nullable = false)
    private Long bookId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private GraphKind kind;

    @Column(name = "version_no", nullable = false)
    private int versionNo;

    @Column(name = "owner_learner_id")
    private Long ownerLearnerId;

    @Column(name = "source_graph_id")
    private Long sourceGraphId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private GraphStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false, length = 16)
    private GraphReviewStatus reviewStatus;
}
