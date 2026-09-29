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
@Table(name = "palace_locus")
public class PalaceLocus extends BaseEntity {

    @Column(name = "palace_id", nullable = false)
    private Long palaceId;

    @Column(nullable = false)
    private int ordinal;

    @Column(nullable = false, length = 100)
    private String name;
}
