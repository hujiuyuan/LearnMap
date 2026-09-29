package com.learnmap.evidence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EvidenceRepository extends JpaRepository<Evidence, Long> {

    List<Evidence> findByLearnerIdAndNodeIdOrderByIdAsc(Long learnerId, Long nodeId);

    List<Evidence> findByLearnerIdOrderByIdAsc(Long learnerId);

    Optional<Evidence> findFirstByLearnerIdOrderByIdDesc(Long learnerId);
}
