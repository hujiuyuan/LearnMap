package com.learnmap.card;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface ReviewCardRepository extends JpaRepository<ReviewCard, Long> {

    List<ReviewCard> findByLearnerIdAndDueAtLessThanEqualOrderByDueAtAsc(Long learnerId, LocalDateTime now, Pageable page);

    List<ReviewCard> findByLearnerIdAndDueAtBefore(Long learnerId, LocalDateTime before);

    long countByLearnerIdAndDueAtLessThanEqual(Long learnerId, LocalDateTime now);
}
