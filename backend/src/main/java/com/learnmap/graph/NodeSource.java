package com.learnmap.graph;

import com.learnmap.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Entity
@Table(name = "node_source")
public class NodeSource extends BaseEntity {

    @Column(name = "node_id", nullable = false)
    private Long nodeId;

    @Column(name = "chunk_id", nullable = false)
    private Long chunkId;

    public NodeSource(Long nodeId, Long chunkId) {
        this.nodeId = nodeId;
        this.chunkId = chunkId;
    }
}
