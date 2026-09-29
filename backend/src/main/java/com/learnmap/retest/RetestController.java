package com.learnmap.retest;

import com.learnmap.common.CurrentUser;
import com.learnmap.evidence.AnswerService.AnswerResult;
import com.learnmap.retest.RetestService.RetestView;
import com.learnmap.user.UserService;
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
@RequestMapping("/api/retests")
@RequiredArgsConstructor
public class RetestController {

    private final RetestService retests;
    private final UserService users;

    public record RetestAnswer(String answer) {
    }

    @GetMapping("/due")
    public List<RetestView> due(@RequestHeader(CurrentUser.HEADER) Long userId) {
        return retests.due(users.require(userId));
    }

    @PostMapping("/{id}/answer")
    public AnswerResult answer(@RequestHeader(CurrentUser.HEADER) Long userId, @PathVariable Long id, @RequestBody RetestAnswer req) {
        return retests.answer(users.require(userId), id, req.answer());
    }
}
