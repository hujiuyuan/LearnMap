package com.learnmap.palace;

import com.learnmap.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "palace_placement")
public class PalacePlacement extends BaseEntity {

    @Column(name = "palace_id", nullable = false)
    private Long palaceId;

    @Column(name = "locus_id", nullable = false)
    private Long locusId;

    @Column(name = "node_id")
    private Long nodeId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(columnDefinition = "TEXT")
    private String image;

    @Column(name = "ai_image", columnDefinition = "TEXT")
    private String aiImage;
}
