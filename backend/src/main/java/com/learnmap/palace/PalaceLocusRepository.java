package com.learnmap.palace;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PalaceLocusRepository extends JpaRepository<PalaceLocus, Long> {

    List<PalaceLocus> findByPalaceIdOrderByOrdinal(Long palaceId);
}
