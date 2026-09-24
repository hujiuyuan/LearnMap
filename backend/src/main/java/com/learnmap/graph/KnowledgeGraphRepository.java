package com.learnmap.graph;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface KnowledgeGraphRepository extends JpaRepository<KnowledgeGraph, Long> {

    List<KnowledgeGraph> findByBookIdAndKindOrderByVersionNoDesc(Long bookId, GraphKind kind);

    Optional<KnowledgeGraph> findFirstByBookIdAndKindAndStatusOrderByVersionNoDesc(Long bookId, GraphKind kind, GraphStatus status);

    Optional<KnowledgeGraph> findByOwnerLearnerIdAndBookIdAndKind(Long learnerId, Long bookId, GraphKind kind);

    List<KnowledgeGraph> findByOwnerLearnerIdAndKind(Long learnerId, GraphKind kind);
}
