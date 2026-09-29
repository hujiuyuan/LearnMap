package com.learnmap.feynman;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface FeynmanAttemptRepository extends JpaRepository<FeynmanAttempt, Long> {

    List<FeynmanAttempt> findByLearnerIdAndNodeIdOrderByIdDesc(Long learnerId, Long nodeId);

    List<FeynmanAttempt> findByLearnerIdOrderByIdDesc(Long learnerId);

    List<FeynmanAttempt> findByLearnerIdInAndNeedsReviewTrueOrderByIdDesc(Collection<Long> learnerIds);

    long countByLearnerIdAndNeedsReviewTrue(Long learnerId);
}
