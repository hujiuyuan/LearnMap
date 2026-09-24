package com.learnmap.question;

import com.learnmap.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 一题多解中的一条解法，例如“配方法求顶点” */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "question_solution")
public class QuestionSolution extends BaseEntity {

    @Column(name = "question_id", nullable = false)
    private Long questionId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int ordinal;
}
