package com.learnmap.mastery;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface NodeMasteryRepository extends JpaRepository<NodeMastery, Long> {

    Optional<NodeMastery> findByLearnerIdAndNodeId(Long learnerId, Long nodeId);

    List<NodeMastery> findByLearnerIdAndNodeIdIn(Long learnerId, Collection<Long> nodeIds);
}
