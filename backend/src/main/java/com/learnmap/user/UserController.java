package com.learnmap.user;

import com.learnmap.auth.AuthProperties;
import com.learnmap.auth.AuthService;
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
    private final AuthService auth;
    private final AuthProperties authProps;

    public record CreateUserRequest(@NotBlank String username, @NotBlank String displayName, @NotNull Role role,
                                    String password) {
    }

    public record UserView(Long id, String username, String displayName, Role role) {
        public static UserView of(AppUser u) {
            return new UserView(u.getId(), u.getUsername(), u.getDisplayName(), u.getRole());
        }
    }

    /**
     * 直接建账号：运维用来建运维账号等。开发 / 测试模式（允许 X-User-Id）下不限制，方便演示和接口测试。
     * 普通用户注册走 /api/auth/register。
     */
    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public UserView create(@RequestHeader(value = CurrentUser.HEADER, required = false) Long userId,
                           @Valid @RequestBody CreateUserRequest req) {
        if (!authProps.allowUserIdHeader() && (userId == null || userService.require(userId).getRole() != Role.OPS)) {
            throw new ForbiddenException("只有运维可以直接建账号，请用注册");
        }
        AppUser u = userService.create(req.username(), req.displayName(), req.role());
        if (req.password() != null && !req.password().isBlank()) {
            auth.setPassword(u, req.password());
        }
        return UserView.of(u);
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
