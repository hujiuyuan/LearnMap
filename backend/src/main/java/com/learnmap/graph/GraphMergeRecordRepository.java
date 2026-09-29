package com.learnmap.graph;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GraphMergeRecordRepository extends JpaRepository<GraphMergeRecord, Long> {

    boolean existsByCopyGraphIdAndToGraphId(Long copyGraphId, Long toGraphId);

    List<GraphMergeRecord> findByCopyGraphIdOrderByIdDesc(Long copyGraphId);
}
