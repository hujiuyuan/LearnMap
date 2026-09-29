package com.learnmap.feynman;

import com.learnmap.common.CurrentUser;
import com.learnmap.feynman.FeynmanService.AttemptView;
import com.learnmap.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class FeynmanController {

    private final FeynmanService feynman;
    private final UserService users;

    public record ExplainRequest(String explanation, String imageBase64, String imageMimeType, Long parentAttemptId) {
    }

    public record ReviewRequest(double score, String note) {
    }

    @PostMapping("/graphs/{graphId}/nodes/{nodeId}/feynman")
    public AttemptView explain(@RequestHeader(CurrentUser.HEADER) Long userId, @PathVariable Long graphId,
                               @PathVariable Long nodeId, @RequestBody ExplainRequest req) {
        return feynman.explain(users.require(userId), graphId, nodeId, req.explanation(), req.imageBase64(),
                req.imageMimeType(), req.parentAttemptId());
    }

    /** 讲解记录：学习者本人、他的辅导者、运维可以看（辅导者据此复核 AI 评分） */
    @GetMapping("/learners/{learnerId}/feynman")
    public List<AttemptView> history(@RequestHeader(CurrentUser.HEADER) Long userId, @PathVariable Long learnerId,
                                     @RequestParam(required = false) Long nodeId) {
        return feynman.history(users.require(userId), learnerId, nodeId);
    }

    /** 辅导者复核：给出自己的分数和意见 */
    @PostMapping("/feynman/{attemptId}/review")
    public AttemptView review(@RequestHeader(CurrentUser.HEADER) Long userId, @PathVariable Long attemptId,
                              @RequestBody ReviewRequest req) {
        return feynman.review(users.require(userId), attemptId, req.score(), req.note());
    }

    @GetMapping("/tutor/feynman-reviews")
    public List<AttemptView> pendingReviews(@RequestHeader(CurrentUser.HEADER) Long userId) {
        return feynman.pendingReviews(users.require(userId));
    }
}
