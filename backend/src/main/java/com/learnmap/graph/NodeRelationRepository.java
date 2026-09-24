package com.learnmap.graph;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NodeRelationRepository extends JpaRepository<NodeRelation, Long> {

    List<NodeRelation> findByGraphId(Long graphId);
}
