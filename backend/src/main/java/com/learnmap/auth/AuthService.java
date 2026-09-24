package com.learnmap.auth;

import com.learnmap.common.ForbiddenException;
import com.learnmap.user.AppUser;
import com.learnmap.user.AppUserRepository;
import com.learnmap.user.Role;
import com.learnmap.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;

/** 用户名 + 密码登录，登录后发随机令牌（存库，可注销）。不做短信、第三方登录等复杂登录。 */
@Service
@RequiredArgsConstructor
public class AuthService {

    static final int MIN_PASSWORD = 6;

    private final AppUserRepository userRepo;
    private final UserService users;
    private final AuthTokenRepository tokens;
    private final AuthProperties props;
    private final Clock clock;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final SecureRandom random = new SecureRandom();

    public record Session(String token, LocalDateTime expiresAt, AppUser user) {
    }

    /** 自助注册只能注册学习者或辅导者；运维账号由已有运维创建 */
    @Transactional
    public Session register(String username, String password, String displayName, Role role) {
        if (role == Role.OPS) {
            throw new ForbiddenException("不能自助注册运维账号");
        }
        checkPassword(password);
        if (userRepo.findByUsername(username.strip()).isPresent()) {
            throw new IllegalArgumentException("用户名已被占用");
        }
        AppUser u = users.create(username.strip(), displayName == null || displayName.isBlank() ? username.strip() : displayName.strip(),
                role == null ? Role.LEARNER : role);
        u.setPasswordHash(encoder.encode(password));
        return issue(u);
    }

    @Transactional
    public Session login(String username, String password) {
        AppUser u = userRepo.findByUsername(username == null ? "" : username.strip())
                .filter(x -> x.getPasswordHash() != null && encoder.matches(password == null ? "" : password, x.getPasswordHash()))
                .orElseThrow(() -> new IllegalArgumentException("用户名或密码不对"));
        return issue(u);
    }

    @Transactional
    public void logout(String token) {
        tokens.findByToken(token).ifPresent(tokens::delete);
    }

    @Transactional
    public void setPassword(AppUser user, String password) {
        checkPassword(password);
        user.setPasswordHash(encoder.encode(password));
        userRepo.save(user);
    }

    /** 令牌 → 用户 id（过期的无效） */
    @Transactional(readOnly = true)
    public Optional<Long> resolve(String token) {
        return tokens.findByToken(token).filter(t -> t.getExpiresAt().isAfter(LocalDateTime.now(clock))).map(AuthToken::getUserId);
    }

    private Session issue(AppUser u) {
        byte[] b = new byte[24];
        random.nextBytes(b);
        AuthToken t = new AuthToken();
        t.setToken(HexFormat.of().formatHex(b));
        t.setUserId(u.getId());
        t.setExpiresAt(LocalDateTime.now(clock).plusDays(props.days()));
        tokens.save(t);
        return new Session(t.getToken(), t.getExpiresAt(), u);
    }

    private static void checkPassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD) {
            throw new IllegalArgumentException("密码至少 " + MIN_PASSWORD + " 位");
        }
    }
}
