package com.learnmap.graph;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface NodeSourceRepository extends JpaRepository<NodeSource, Long> {

    List<NodeSource> findByNodeIdIn(Collection<Long> nodeIds);
}
