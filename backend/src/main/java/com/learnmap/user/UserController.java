package com.learnmap.user;

import com.learnmap.common.CurrentUser;
import com.learnmap.common.ForbiddenException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    public record CreateUserRequest(@NotBlank String username, @NotBlank String displayName, @NotNull Role role) {
    }

    public record UserView(Long id, String username, String displayName, Role role) {
        static UserView of(AppUser u) {
            return new UserView(u.getId(), u.getUsername(), u.getDisplayName(), u.getRole());
        }
    }

    /** 开发阶段的注册入口；接入真正的登录后替换。 */
    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public UserView create(@Valid @RequestBody CreateUserRequest req) {
        return UserView.of(userService.create(req.username(), req.displayName(), req.role()));
    }

    @GetMapping("/me")
    public UserView me(@RequestHeader(CurrentUser.HEADER) Long userId) {
        return UserView.of(userService.require(userId));
    }

    @PostMapping("/tutor/learners/{learnerId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void bindLearner(@RequestHeader(CurrentUser.HEADER) Long userId, @PathVariable Long learnerId) {
        userService.bindLearner(userId, learnerId);
    }

    @GetMapping("/tutor/learners")
    public List<UserView> myLearners(@RequestHeader(CurrentUser.HEADER) Long userId) {
        if (userService.require(userId).getRole() != Role.TUTOR) {
            throw new ForbiddenException("只有辅导者有名下学习者");
        }
        return userService.learnersOf(userId).stream().map(UserView::of).toList();
    }
}
