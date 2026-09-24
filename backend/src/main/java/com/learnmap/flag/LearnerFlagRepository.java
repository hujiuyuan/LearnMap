package com.learnmap.flag;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface LearnerFlagRepository extends JpaRepository<LearnerFlag, Long> {

    List<LearnerFlag> findByLearnerIdOrderByIdDesc(Long learnerId);

    List<LearnerFlag> findByLearnerIdAndResolvedFalse(Long learnerId);

    List<LearnerFlag> findByLearnerIdAndNodeIdOrderByIdDesc(Long learnerId, Long nodeId);

    List<LearnerFlag> findByKind(FlagKind kind);

    boolean existsByLearnerIdAndNodeIdAndKindAndResolvedFalse(Long learnerId, Long nodeId, FlagKind kind);

    List<LearnerFlag> findByLearnerIdInAndResolvedFalse(Collection<Long> learnerIds);
}
