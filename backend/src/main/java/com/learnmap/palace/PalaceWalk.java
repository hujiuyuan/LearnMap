package com.learnmap.palace;

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
@Table(name = "palace_walk")
public class PalaceWalk extends BaseEntity {

    @Column(name = "palace_id", nullable = false)
    private Long palaceId;

    /** FORWARD / BACKWARD / NEXT / RECALL */
    @Column(nullable = false, length = 16)
    private String kind;

    @Column(nullable = false)
    private int correct;

    @Column(nullable = false)
    private int total;

    public PalaceWalk(Long palaceId, String kind, int correct, int total) {
        this.palaceId = palaceId;
        this.kind = kind;
        this.correct = correct;
        this.total = total;
    }
}
