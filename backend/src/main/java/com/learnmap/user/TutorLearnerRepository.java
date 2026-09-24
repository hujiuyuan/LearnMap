package com.learnmap.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TutorLearnerRepository extends JpaRepository<TutorLearner, Long> {

    boolean existsByTutorIdAndLearnerId(Long tutorId, Long learnerId);

    List<TutorLearner> findByTutorId(Long tutorId);

    List<TutorLearner> findByLearnerId(Long learnerId);
}
