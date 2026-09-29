package com.learnmap.palace;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PalaceWalkRepository extends JpaRepository<PalaceWalk, Long> {

    List<PalaceWalk> findByPalaceIdOrderByIdDesc(Long palaceId);
}
