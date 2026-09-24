package com.learnmap.question;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface QuestionRepository extends JpaRepository<Question, Long> {

    List<Question> findByNodeIdIn(Collection<Long> nodeIds);
}
