package com.learnmap.auth;

import com.learnmap.user.AppUserRepository;
import com.learnmap.user.AppUser;
import com.learnmap.user.Role;
import com.learnmap.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 正式部署时的第一个运维账号：设置环境变量 LEARNMAP_ADMIN_PASSWORD 后，库里还没有运维账号就建一个 admin。
 * 不设就不建，密码不写进代码。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrap implements ApplicationRunner {

    private final AppUserRepository userRepo;
    private final UserService users;
    private final AuthService auth;

    @Value("${learnmap.auth.admin-password:}")
    private String adminPassword;

    @Override
    public void run(ApplicationArguments args) {
        if (adminPassword == null || adminPassword.isBlank()) {
            return;
        }
        if (userRepo.findAll().stream().anyMatch(u -> u.getRole() == Role.OPS)) {
            return;
        }
        AppUser admin = users.create("admin", "平台运维", Role.OPS);
        auth.setPassword(admin, adminPassword);
        log.info("已创建运维账号 admin");
    }
}
