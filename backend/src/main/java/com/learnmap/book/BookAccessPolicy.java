package com.learnmap.book;

import com.learnmap.common.ForbiddenException;
import com.learnmap.user.AppUser;
import com.learnmap.user.Role;
import com.learnmap.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 书的可见范围（产品方案 2.5）：
 * 学习者上传 → 本人和他的辅导者；辅导者上传 → 辅导者和名下学习者；预置书 → 所有人；运维能看全部。
 */
@Component
@RequiredArgsConstructor
public class BookAccessPolicy {

    private final UserService users;

    public boolean canSee(AppUser actor, Book book) {
        if (actor.getRole() == Role.OPS || book.getVisibility() == Visibility.PUBLIC || book.getOwnerId().equals(actor.getId())) {
            return true;
        }
        return switch (book.getVisibility()) {
            // 辅导者的书：名下学习者可见
            case TUTOR_GROUP -> users.tutorIdsOf(actor.getId()).contains(book.getOwnerId());
            // 学习者自己的书：他的辅导者可见
            case PRIVATE -> actor.getRole() == Role.TUTOR && users.tutorIdsOf(book.getOwnerId()).contains(actor.getId());
            case PUBLIC -> true;
        };
    }

    public void checkCanSee(AppUser actor, Book book) {
        if (!canSee(actor, book)) {
            throw new ForbiddenException("无权查看这本书");
        }
    }

    /** 上传者和运维可以校对、发布、重新生成 */
    public void checkCanManage(AppUser actor, Book book) {
        if (actor.getRole() != Role.OPS && !book.getOwnerId().equals(actor.getId())) {
            throw new ForbiddenException("只有上传者或运维可以管理这本书的图谱");
        }
    }

    public static Visibility visibilityFor(Role uploaderRole) {
        return switch (uploaderRole) {
            case LEARNER -> Visibility.PRIVATE;
            case TUTOR -> Visibility.TUTOR_GROUP;
            case OPS -> Visibility.PUBLIC;
        };
    }
}
