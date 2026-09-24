package com.learnmap.plan;

import com.learnmap.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "study_goal")
public class StudyGoal extends BaseEntity {

    @Column(name = "learner_id", nullable = false)
    private Long learnerId;

    @Column(name = "graph_id", nullable = false)
    private Long graphId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private GoalScope scope;

    @Column(name = "chapter_ids", columnDefinition = "TEXT")
    private String chapterIds;

    @Column(nullable = false)
    private LocalDate deadline;

    @Column(name = "weekday_minutes", nullable = false)
    private int weekdayMinutes;

    @Column(name = "weekend_minutes", nullable = false)
    private int weekendMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "main_style", nullable = false, length = 16)
    private MainStyle mainStyle;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Rhythm rhythm;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(columnDefinition = "TEXT")
    private String warning;

    @Column(name = "planned_at")
    private LocalDateTime plannedAt;

    public List<Long> chapters() {
        if (chapterIds == null || chapterIds.isBlank()) {
            return List.of();
        }
        return Arrays.stream(chapterIds.split(",")).map(String::trim).filter(s -> !s.isEmpty()).map(Long::valueOf).toList();
    }
}
