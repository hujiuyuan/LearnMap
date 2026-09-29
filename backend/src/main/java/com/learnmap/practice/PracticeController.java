package com.learnmap.practice;

import com.learnmap.common.CurrentUser;
import com.learnmap.evidence.ErrorType;
import com.learnmap.evidence.SelfConfidence;
import com.learnmap.practice.PracticeService.AnswerOutcome;
import com.learnmap.practice.PracticeService.SessionSummary;
import com.learnmap.practice.PracticeService.SessionView;
import com.learnmap.user.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PracticeController {

    private final PracticeService practice;
    private final UserService users;

    /** @param targetNodeId 刻意练习的根因节点、第一性原理的目标节点；交错练习可以不填 */
    public record StartRequest(@NotNull PracticeMode mode, Long targetNodeId) {
    }

    public record PracticeAnswer(String answer, ErrorType errorType, SelfConfidence selfConfidence, Integer durationSec) {
    }

    @PostMapping("/graphs/{graphId}/practice")
    public SessionView start(@RequestHeader(CurrentUser.HEADER) Long userId, @PathVariable Long graphId,
                             @Valid @RequestBody StartRequest req) {
        return practice.start(users.require(userId), graphId, req.mode(), req.targetNodeId());
    }

    @GetMapping("/practice/{sessionId}")
    public SessionView get(@RequestHeader(CurrentUser.HEADER) Long userId, @PathVariable Long sessionId) {
        return practice.get(users.require(userId), sessionId);
    }

    @PostMapping("/practice/{sessionId}/items/{itemId}/answer")
    public AnswerOutcome answer(@RequestHeader(CurrentUser.HEADER) Long userId, @PathVariable Long sessionId,
                                @PathVariable Long itemId, @RequestBody PracticeAnswer req) {
        return practice.answer(users.require(userId), sessionId, itemId, req.answer(), req.errorType(),
                req.selfConfidence(), req.durationSec());
    }

    @GetMapping("/learners/{learnerId}/practice")
    public List<SessionSummary> history(@RequestHeader(CurrentUser.HEADER) Long userId, @PathVariable Long learnerId) {
        return practice.history(users.require(userId), learnerId);
    }
}
