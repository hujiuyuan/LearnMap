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
@Table(name = "graph_merge_decision")
public class GraphMergeRecord extends BaseEntity {

    @Column(name = "copy_graph_id", nullable = false)
    private Long copyGraphId;

    @Column(name = "from_graph_id", nullable = false)
    private Long fromGraphId;

    @Column(name = "to_graph_id", nullable = false)
    private Long toGraphId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MergeDecision decision;

    @Column(name = "decided_by", nullable = false)
    private Long decidedBy;

    @Column(columnDefinition = "TEXT")
    private String summary;
}
