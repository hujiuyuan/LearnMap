package com.learnmap.diagnostic;

import com.learnmap.common.CurrentUser;
import com.learnmap.diagnostic.DiagnosticService.AnswerOutcome;
import com.learnmap.diagnostic.DiagnosticService.Report;
import com.learnmap.diagnostic.DiagnosticService.SessionView;
import com.learnmap.evidence.SelfConfidence;
import com.learnmap.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class DiagnosticController {

    private final DiagnosticService diagnostics;
    private final UserService users;

    public record DiagnosticAnswer(String answer, SelfConfidence selfConfidence, Integer durationSec) {
    }

    /** 开始（或继续未完成的）入门诊断 */
    @PostMapping("/graphs/{graphId}/diagnostics")
    public SessionView start(@RequestHeader(CurrentUser.HEADER) Long userId, @PathVariable Long graphId) {
        return diagnostics.start(users.require(userId), graphId);
    }

    @GetMapping("/diagnostics/{sessionId}")
    public SessionView get(@RequestHeader(CurrentUser.HEADER) Long userId, @PathVariable Long sessionId) {
        return diagnostics.get(users.require(userId), sessionId);
    }

    @PostMapping("/diagnostics/{sessionId}/items/{itemId}/answer")
    public AnswerOutcome answer(@RequestHeader(CurrentUser.HEADER) Long userId, @PathVariable Long sessionId,
                                @PathVariable Long itemId, @RequestBody DiagnosticAnswer req) {
        return diagnostics.answer(users.require(userId), sessionId, itemId, req.answer(), req.selfConfidence(), req.durationSec());
    }

    /** 诊断报告：学习者本人、他的辅导者、运维可以看 */
    @GetMapping("/diagnostics/{sessionId}/report")
    public Report report(@RequestHeader(CurrentUser.HEADER) Long userId, @PathVariable Long sessionId) {
        return diagnostics.report(users.require(userId), sessionId);
    }
}
