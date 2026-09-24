package com.learnmap.evidence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EvidenceRepository extends JpaRepository<Evidence, Long> {

    List<Evidence> findByLearnerIdAndNodeIdOrderByIdAsc(Long learnerId, Long nodeId);
}
