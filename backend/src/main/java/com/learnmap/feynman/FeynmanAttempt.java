package com.learnmap.feynman;

import com.learnmap.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "feynman_attempt")
public class FeynmanAttempt extends BaseEntity {

    @Column(name = "learner_id", nullable = false)
    private Long learnerId;

    @Column(name = "node_id", nullable = false)
    private Long nodeId;

    @Column(name = "parent_attempt_id")
    private Long parentAttemptId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String explanation;

    @Column(name = "from_image", nullable = false)
    private boolean fromImage;

    @Column(nullable = false)
    private double score;

    @Column(name = "covered_json", columnDefinition = "TEXT")
    private String coveredJson;

    @Column(name = "missing_json", columnDefinition = "TEXT")
    private String missingJson;

    @Column(name = "incorrect_json", columnDefinition = "TEXT")
    private String incorrectJson;

    @Column(name = "follow_ups_json", columnDefinition = "TEXT")
    private String followUpsJson;

    @Column(nullable = false, length = 32)
    private String grader;

    @Column(name = "needs_review", nullable = false)
    private boolean needsReview;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_score")
    private Double reviewedScore;

    @Column(name = "review_note", length = 500)
    private String reviewNote;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;
}
