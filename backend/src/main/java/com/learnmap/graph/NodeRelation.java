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
@Table(name = "node_relation")
public class NodeRelation extends BaseEntity {

    @Column(name = "graph_id", nullable = false)
    private Long graphId;

    @Column(name = "from_node_id", nullable = false)
    private Long fromNodeId;

    @Column(name = "to_node_id", nullable = false)
    private Long toNodeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "relation_type", nullable = false, length = 16)
    private RelationType relationType;

    @Column(nullable = false)
    private double confidence;

    @Column(columnDefinition = "TEXT")
    private String evidence;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false, length = 16)
    private ItemReviewStatus reviewStatus;

    @Column(nullable = false)
    private boolean disputed;
}
