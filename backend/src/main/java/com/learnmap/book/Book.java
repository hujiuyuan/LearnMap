package com.learnmap.book;

import com.learnmap.common.BaseEntity;
import com.learnmap.user.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "book")
public class Book extends BaseEntity {

    @Column(nullable = false)
    private String title;

    private String author;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "uploader_role", nullable = false, length = 16)
    private Role uploaderRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Visibility visibility;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_mode", nullable = false, length = 32)
    private ReviewMode reviewMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "content_kind", nullable = false, length = 16)
    private ContentKind contentKind;

    @Enumerated(EnumType.STRING)
    @Column(name = "file_format", nullable = false, length = 16)
    private FileFormat fileFormat;

    @Column(name = "file_key", length = 512)
    private String fileKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private BookStatus status;

    @Column(name = "status_message", length = 1024)
    private String statusMessage;
}
