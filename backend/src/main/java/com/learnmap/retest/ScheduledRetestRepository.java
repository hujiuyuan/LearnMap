package com.learnmap.retest;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface ScheduledRetestRepository extends JpaRepository<ScheduledRetest, Long> {

    List<ScheduledRetest> findByLearnerIdAndDoneFalseAndDueAtLessThanEqualOrderByDueAtAsc(Long learnerId, LocalDateTime now);

    List<ScheduledRetest> findByLearnerIdAndDoneFalseOrderByDueAtAsc(Long learnerId);

    List<ScheduledRetest> findByLearnerIdAndDoneTrue(Long learnerId);

    List<ScheduledRetest> findByLearnerIdAndNodeIdOrderByIdDesc(Long learnerId, Long nodeId);

    boolean existsByLearnerIdAndNodeIdAndReasonAndDoneFalse(Long learnerId, Long nodeId, RetestReason reason);
}
