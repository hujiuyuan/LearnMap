package com.learnmap.user;

import com.learnmap.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Entity
@Table(name = "tutor_learner")
public class TutorLearner extends BaseEntity {

    @Column(name = "tutor_id", nullable = false)
    private Long tutorId;

    @Column(name = "learner_id", nullable = false)
    private Long learnerId;

    public TutorLearner(Long tutorId, Long learnerId) {
        this.tutorId = tutorId;
        this.learnerId = learnerId;
    }
}
