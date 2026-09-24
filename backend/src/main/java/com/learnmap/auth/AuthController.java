package com.learnmap.auth;

import com.learnmap.auth.AuthService.Session;
import com.learnmap.common.CurrentUser;
import com.learnmap.user.Role;
import com.learnmap.user.UserController.UserView;
import com.learnmap.user.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService auth;
    private final UserService users;

    public record RegisterRequest(@NotBlank String username, @NotBlank String password, String displayName, Role role) {
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record PasswordRequest(@NotBlank String password) {
    }

    public record SessionView(String token, LocalDateTime expiresAt, UserView user) {
        static SessionView of(Session s) {
            return new SessionView(s.token(), s.expiresAt(), UserView.of(s.user()));
        }
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public SessionView register(@Valid @RequestBody RegisterRequest req) {
        return SessionView.of(auth.register(req.username(), req.password(), req.displayName(), req.role()));
    }

    @PostMapping("/login")
    public SessionView login(@Valid @RequestBody LoginRequest req) {
        return SessionView.of(auth.login(req.username(), req.password()));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestHeader(value = "Authorization", required = false) String header) {
        if (header != null && header.startsWith("Bearer ")) {
            auth.logout(header.substring(7).strip());
        }
    }

    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void password(@RequestHeader(CurrentUser.HEADER) Long userId, @Valid @RequestBody PasswordRequest req) {
        auth.setPassword(users.require(userId), req.password());
    }
}
