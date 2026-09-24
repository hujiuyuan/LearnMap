package com.learnmap.question;

import com.learnmap.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Arrays;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "question")
public class Question extends BaseEntity {

    @Column(name = "book_id", nullable = false)
    private Long bookId;

    /** 主考查节点，指向 MASTER 图谱 */
    @Column(name = "node_id", nullable = false)
    private Long nodeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "question_type", nullable = false, length = 16)
    private QuestionType questionType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String stem;

    /** 选项，JSON 数组 */
    @Column(name = "options_json", columnDefinition = "TEXT")
    private String optionsJson;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String answer;

    @Column(nullable = false)
    private double difficulty;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private QuestionSource source;

    @Column(nullable = false)
    private boolean verified;

    /** 逗号分隔的 {@link Ability} */
    private String abilities;

    public List<Ability> abilityList() {
        if (abilities == null || abilities.isBlank()) {
            return List.of();
        }
        return Arrays.stream(abilities.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                .map(Ability::valueOf).toList();
    }
}
