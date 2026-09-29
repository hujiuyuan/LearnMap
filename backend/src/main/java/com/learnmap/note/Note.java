package com.learnmap.note;

import com.learnmap.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "note")
public class Note extends BaseEntity {

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private NoteKind kind;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String cues;

    @Column(columnDefinition = "TEXT")
    private String body;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(name = "summary_feedback", columnDefinition = "TEXT")
    private String summaryFeedback;

    @Column(name = "book_id")
    private Long bookId;

    @Column(name = "section_id")
    private Long sectionId;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
