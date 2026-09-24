package com.learnmap.demo;

import com.learnmap.auth.AuthService;
import com.learnmap.book.BookService;
import com.learnmap.book.ReviewMode;
import com.learnmap.graph.GraphService;
import com.learnmap.user.AppUser;
import com.learnmap.user.AppUserRepository;
import com.learnmap.user.Role;
import com.learnmap.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * 本地开发时导入演示数据：一个运维、一个辅导者、一个学习者，一本示例预置书（一元二次方程，自编内容），
 * 并把书分配给学习者。只在 learnmap.demo.enabled=true 时运行。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "learnmap.demo", name = "enabled", havingValue = "true")
public class DemoDataLoader implements ApplicationRunner {

    private final AppUserRepository userRepo;
    private final UserService users;
    private final BookService books;
    private final GraphService graphs;
    private final AuthService auth;

    static final String DEMO_PASSWORD = "demo123";

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (userRepo.count() > 0) {
            return;
        }
        AppUser ops = users.create("ops", "平台运维", Role.OPS);
        AppUser tutor = users.create("tutor", "王老师", Role.TUTOR);
        AppUser learner = users.create("learner", "小明", Role.LEARNER);
        users.bindLearner(tutor.getId(), learner.getId());
        // 演示账号的密码只在 dev 模式（内存数据库）里用
        for (AppUser u : new AppUser[]{ops, tutor, learner}) {
            auth.setPassword(u, DEMO_PASSWORD);
        }

        byte[] content = new ClassPathResource("demo/quadratic-equation.md").getContentAsByteArray();
        var imported = books.importBook(ops, "一元二次方程（示例）", "LearnMap", "quadratic-equation.md", content, ReviewMode.NONE);
        var copy = graphs.assign(tutor, imported.book().getId(), learner.getId());
        log.info("演示数据已导入：运维 id={}，辅导者 id={}，学习者 id={}，书 id={}，学习者图谱 id={}",
                ops.getId(), tutor.getId(), learner.getId(), imported.book().getId(), copy.id());
    }
}
