package com.learnmap.palace;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PalacePlacementRepository extends JpaRepository<PalacePlacement, Long> {

    List<PalacePlacement> findByPalaceId(Long palaceId);
}
