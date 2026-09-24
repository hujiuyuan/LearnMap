package com.learnmap.evidence;

import com.learnmap.common.CurrentUser;
import com.learnmap.evidence.AnswerService.AnswerRequest;
import com.learnmap.evidence.AnswerService.AnswerResult;
import com.learnmap.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AnswerController {

    private final AnswerService answers;
    private final UserService users;

    @PostMapping("/answers")
    public AnswerResult answer(@RequestHeader(CurrentUser.HEADER) Long userId, @RequestBody AnswerRequest req) {
        return answers.record(users.require(userId), req);
    }
}
