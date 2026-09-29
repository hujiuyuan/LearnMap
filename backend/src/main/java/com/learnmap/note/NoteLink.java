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
@Table(name = "note_link")
public class NoteLink extends BaseEntity {

    public static final String WIKI = "WIKI";
    public static final String MANUAL = "MANUAL";
    public static final String SUGGESTED = "SUGGESTED";

    @Column(name = "from_note_id", nullable = false)
    private Long fromNoteId;

    @Column(name = "to_note_id", nullable = false)
    private Long toNoteId;

    @Column(nullable = false, length = 16)
    private String source;

    public NoteLink(Long fromNoteId, Long toNoteId, String source) {
        this.fromNoteId = fromNoteId;
        this.toNoteId = toNoteId;
        this.source = source;
    }
}
