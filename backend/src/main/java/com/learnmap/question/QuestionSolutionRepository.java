package com.learnmap.question;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuestionSolutionRepository extends JpaRepository<QuestionSolution, Long> {

    List<QuestionSolution> findByQuestionIdOrderByOrdinal(Long questionId);
}
