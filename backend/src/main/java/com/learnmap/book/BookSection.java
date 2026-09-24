package com.learnmap.book;

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
@Table(name = "book_section")
public class BookSection extends BaseEntity {

    @Column(name = "book_id", nullable = false)
    private Long bookId;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(nullable = false, length = 512)
    private String title;

    /** 1 = 章，2 = 节，3 = 小节 */
    @Column(nullable = false)
    private int level;

    @Column(nullable = false)
    private int ordinal;
}
