package com.learnmap.note;

import com.learnmap.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "note_node")
public class NoteNode extends BaseEntity {

    @Column(name = "note_id", nullable = false)
    private Long noteId;

    @Column(name = "node_id", nullable = false)
    private Long nodeId;

    public NoteNode(Long noteId, Long nodeId) {
        this.noteId = noteId;
        this.nodeId = nodeId;
    }
}
