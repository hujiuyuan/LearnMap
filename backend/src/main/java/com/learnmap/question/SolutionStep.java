package com.learnmap.question;

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
@Table(name = "solution_step")
public class SolutionStep extends BaseEntity {

    @Column(name = "solution_id", nullable = false)
    private Long solutionId;

    @Column(nullable = false)
    private int ordinal;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    /** 这一步用到的知识点，指向 MASTER 图谱 */
    @Column(name = "node_id", nullable = false)
    private Long nodeId;

    @Column(columnDefinition = "TEXT")
    private String answer;
}
