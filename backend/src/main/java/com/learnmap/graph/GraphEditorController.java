package com.learnmap.graph;

import com.learnmap.common.CurrentUser;
import com.learnmap.graph.GraphEditService.NewNode;
import com.learnmap.graph.GraphEditService.NodeDetail;
import com.learnmap.graph.GraphEditService.NodeEdit;
import com.learnmap.graph.GraphUpdateService.ApplyResult;
import com.learnmap.graph.GraphUpdateService.DiffView;
import com.learnmap.graph.GraphUpdateService.RegenerateResult;
import com.learnmap.graph.GraphUpdateService.UpdateView;
import com.learnmap.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 图谱编辑器、版本差异与合并 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class GraphEditorController {

    private final GraphEditService editor;
    private final GraphUpdateService updates;
    private final UserService users;

    public record ReviewRequest(ItemReviewStatus status) {
    }

    public record RelationRequest(Long fromNodeId, Long toNodeId, RelationType type) {
    }

    public record DecisionRequest(MergeDecision decision) {
    }

    @GetMapping("/graphs/{graphId}/nodes/{nodeId}")
    public NodeDetail detail(@RequestHeader(CurrentUser.HEADER) Long userId, @PathVariable Long graphId, @PathVariable Long nodeId) {
        return editor.detail(users.require(userId), graphId, nodeId);
    }

    // ---------- 编辑器（书本图谱） ----------

    @PatchMapping("/editor/graphs/{graphId}/nodes/{nodeId}")
    public NodeDetail updateNode(@RequestHeader(CurrentUser.HEADER) Long userId, @PathVariable Long graphId,
                                 @PathVariable Long nodeId, @RequestBody NodeEdit req) {
        return editor.updateNode(users.require(userId), graphId, nodeId, req);
    }

    @PostMapping("/editor/graphs/{graphId}/nodes/{nodeId}/review")
    public NodeDetail reviewNode(@RequestHeader(CurrentUser.HEADER) Long userId, @PathVariable Long graphId,
                                 @PathVariable Long nodeId, @RequestBody ReviewRequest req) {
        return editor.reviewNode(users.require(userId), graphId, nodeId, req.status());
    }

    @PostMapping("/editor/graphs/{graphId}/nodes")
    @ResponseStatus(HttpStatus.CREATED)
    public NodeDetail addNode(@RequestHeader(CurrentUser.HEADER) Long userId, @PathVariable Long graphId, @RequestBody NewNode req) {
        return editor.addNode(users.require(userId), graphId, req);
    }

    @PostMapping("/editor/graphs/{graphId}/relations")
    public NodeDetail addRelation(@RequestHeader(CurrentUser.HEADER) Long userId, @PathVariable Long graphId,
                                  @RequestBody RelationRequest req) {
        return editor.addRelation(users.require(userId), graphId, req.fromNodeId(), req.toNodeId(),
                req.type() == null ? RelationType.PREREQUISITE : req.type());
    }

    @PostMapping("/editor/graphs/{graphId}/relations/{relationId}/review")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reviewRelation(@RequestHeader(CurrentUser.HEADER) Long userId, @PathVariable Long graphId,
                               @PathVariable Long relationId, @RequestBody ReviewRequest req) {
        editor.reviewRelation(users.require(userId), graphId, relationId, req.status());
    }

    @DeleteMapping("/editor/graphs/{graphId}/relations/{relationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRelation(@RequestHeader(CurrentUser.HEADER) Long userId, @PathVariable Long graphId,
                               @PathVariable Long relationId) {
        editor.deleteRelation(users.require(userId), graphId, relationId);
    }

    // ---------- 版本 ----------

    /** 重新生成这本书的图谱（新版本）；返回和当前已发布版本的差异 */
    @PostMapping("/books/{bookId}/regenerate")
    public RegenerateResult regenerate(@RequestHeader(CurrentUser.HEADER) Long userId, @PathVariable Long bookId) {
        return updates.regenerate(users.require(userId), bookId);
    }

    @GetMapping("/graphs/{graphId}/diff")
    public DiffView diff(@RequestHeader(CurrentUser.HEADER) Long userId, @PathVariable Long graphId, @RequestParam Long against) {
        return updates.masterDiff(users.require(userId), graphId, against);
    }

    /** 学习者副本：书有没有新版本、差在哪、谁来选 */
    @GetMapping("/graphs/{graphId}/update")
    public UpdateView pendingUpdate(@RequestHeader(CurrentUser.HEADER) Long userId, @PathVariable Long graphId) {
        return updates.pendingUpdate(users.require(userId), graphId);
    }

    @PostMapping("/graphs/{graphId}/update")
    public ApplyResult apply(@RequestHeader(CurrentUser.HEADER) Long userId, @PathVariable Long graphId,
                             @RequestBody DecisionRequest req) {
        return updates.apply(users.require(userId), graphId, req.decision());
    }
}
