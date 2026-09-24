package com.learnmap.question;

import com.learnmap.common.CurrentUser;
import com.learnmap.common.NotFoundException;
import com.learnmap.graph.GraphService;
import com.learnmap.graph.KnowledgeNode;
import com.learnmap.graph.KnowledgeNodeRepository;
import com.learnmap.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class QuestionController {

    private final GraphService graphs;
    private final KnowledgeNodeRepository nodes;
    private final QuestionRepository questions;
    private final UserService users;

    /** 不含答案，答案在作答后返回 */
    public record QuestionView(Long id, QuestionType type, String stem, String optionsJson, double difficulty,
                               QuestionSource source) {
    }

    /** 某个节点的题目；学习者副本里的节点会找到对应的书本图谱节点上的题 */
    @GetMapping("/graphs/{graphId}/nodes/{nodeId}/questions")
    public List<QuestionView> ofNode(@RequestHeader(CurrentUser.HEADER) Long userId,
                                     @PathVariable Long graphId, @PathVariable Long nodeId) {
        graphs.checkCanView(users.require(userId), graphId);
        KnowledgeNode node = nodes.findById(nodeId).filter(n -> n.getGraphId().equals(graphId))
                .orElseThrow(() -> NotFoundException.of("节点", nodeId));
        Long masterNodeId = node.getOriginNodeId() != null ? node.getOriginNodeId() : node.getId();
        return questions.findByNodeIdIn(List.of(masterNodeId)).stream()
                .map(q -> new QuestionView(q.getId(), q.getQuestionType(), q.getStem(), q.getOptionsJson(),
                        q.getDifficulty(), q.getSource()))
                .toList();
    }
}
