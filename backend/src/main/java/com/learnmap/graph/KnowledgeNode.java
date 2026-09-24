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
@Table(name = "knowledge_node")
public class KnowledgeNode extends BaseEntity {

    @Column(name = "graph_id", nullable = false)
    private Long graphId;

    @Column(name = "origin_node_id")
    private Long originNodeId;

    @Column(name = "section_id")
    private Long sectionId;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String definition;

    @Enumerated(EnumType.STRING)
    @Column(name = "node_type", nullable = false, length = 16)
    private NodeType nodeType;

    @Column(name = "source_confidence", nullable = false)
    private double sourceConfidence;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false, length = 16)
    private ItemReviewStatus reviewStatus;

    @Column(nullable = false)
    private boolean deleted;

    @Column(nullable = false)
    private int ordinal;

    /** 复制到学习者副本：保留内容，记下来源节点。 */
    public KnowledgeNode copyTo(Long targetGraphId) {
        KnowledgeNode n = new KnowledgeNode();
        n.graphId = targetGraphId;
        n.originNodeId = getId();
        n.sectionId = sectionId;
        n.name = name;
        n.definition = definition;
        n.nodeType = nodeType;
        n.sourceConfidence = sourceConfidence;
        n.reviewStatus = reviewStatus;
        n.deleted = deleted;
        n.ordinal = ordinal;
        return n;
    }
}
