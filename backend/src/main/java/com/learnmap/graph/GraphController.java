package com.learnmap.graph;

import com.learnmap.common.CurrentUser;
import com.learnmap.graph.GraphService.GraphSummary;
import com.learnmap.graph.GraphService.GraphView;
import com.learnmap.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class GraphController {

    private final GraphService graphService;
    private final UserService users;

    @GetMapping("/books/{bookId}/graphs")
    public List<GraphSummary> masters(@RequestHeader(CurrentUser.HEADER) Long userId, @PathVariable Long bookId) {
        return graphService.mastersOf(users.require(userId), bookId);
    }

    /** 把书分配给学习者（不传 learnerId 就是分配给自己） */
    @PostMapping("/books/{bookId}/assign")
    public GraphSummary assign(@RequestHeader(CurrentUser.HEADER) Long userId, @PathVariable Long bookId,
                               @RequestParam(required = false) Long learnerId) {
        return graphService.assign(users.require(userId), bookId, learnerId == null ? userId : learnerId);
    }

    @GetMapping("/learners/{learnerId}/graphs")
    public List<GraphSummary> learnerGraphs(@RequestHeader(CurrentUser.HEADER) Long userId, @PathVariable Long learnerId) {
        return graphService.learnerGraphs(users.require(userId), learnerId);
    }

    @GetMapping("/graphs/{graphId}")
    public GraphView view(@RequestHeader(CurrentUser.HEADER) Long userId, @PathVariable Long graphId) {
        return graphService.view(users.require(userId), graphId);
    }

    @PostMapping("/graphs/{graphId}/publish")
    public GraphSummary publish(@RequestHeader(CurrentUser.HEADER) Long userId, @PathVariable Long graphId,
                                @RequestParam(defaultValue = "false") boolean reviewed) {
        return graphService.publish(users.require(userId), graphId, reviewed);
    }

    @DeleteMapping("/graphs/{graphId}/nodes/{nodeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteNode(@RequestHeader(CurrentUser.HEADER) Long userId, @PathVariable Long graphId, @PathVariable Long nodeId) {
        graphService.deleteNode(users.require(userId), graphId, nodeId);
    }

    /** 学习者标记“这条前置关系不对”，只影响自己的副本 */
    @PostMapping("/graphs/{graphId}/relations/{relationId}/dispute")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void dispute(@RequestHeader(CurrentUser.HEADER) Long userId, @PathVariable Long graphId, @PathVariable Long relationId) {
        graphService.disputeRelation(users.require(userId), graphId, relationId);
    }
}
