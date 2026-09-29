package com.learnmap.palace;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface MemoryPalaceRepository extends JpaRepository<MemoryPalace, Long> {

    List<MemoryPalace> findByOwnerIdOrderByIdDesc(Long ownerId);

    List<MemoryPalace> findByOwnerIdAndDueAtLessThanEqual(Long ownerId, LocalDateTime now);
}
