package com.learnmap.card;

import com.learnmap.card.CardService.DueCards;
import com.learnmap.card.CardService.ReviewResult;
import com.learnmap.common.CurrentUser;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cards")
@RequiredArgsConstructor
public class CardController {

    private final CardService cardService;
    private final UserService users;

    public record ReviewRequest(@NotNull Rating rating, Integer durationSec) {
    }

    /** 当前学习者到期的复习卡片 */
    @GetMapping("/due")
    public DueCards due(@RequestHeader(CurrentUser.HEADER) Long userId, @RequestParam(defaultValue = "20") int limit) {
        return cardService.due(users.require(userId), limit);
    }

    @PostMapping("/{cardId}/review")
    public ReviewResult review(@RequestHeader(CurrentUser.HEADER) Long userId, @PathVariable Long cardId,
                               @Valid @RequestBody ReviewRequest req) {
        return cardService.review(users.require(userId), cardId, req.rating(), req.durationSec());
    }
}
