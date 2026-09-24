package com.learnmap.graph;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface KnowledgeNodeRepository extends JpaRepository<KnowledgeNode, Long> {

    List<KnowledgeNode> findByGraphIdOrderByOrdinal(Long graphId);

    List<KnowledgeNode> findByGraphIdAndOriginNodeIdIn(Long graphId, Collection<Long> originNodeIds);
}
