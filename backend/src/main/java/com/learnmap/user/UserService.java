package com.learnmap.user;

import com.learnmap.common.ForbiddenException;
import com.learnmap.common.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final AppUserRepository users;
    private final TutorLearnerRepository tutorLearners;

    @Transactional
    public AppUser create(String username, String displayName, Role role) {
        if (users.findByUsername(username).isPresent()) {
            throw new IllegalArgumentException("用户名已存在：" + username);
        }
        return users.save(new AppUser(username, displayName, role));
    }

    public AppUser require(Long id) {
        return users.findById(id).orElseThrow(() -> NotFoundException.of("用户", id));
    }

    @Transactional
    public void bindLearner(Long tutorId, Long learnerId) {
        AppUser tutor = require(tutorId);
        AppUser learner = require(learnerId);
        if (tutor.getRole() != Role.TUTOR) {
            throw new IllegalArgumentException("只有辅导者可以绑定学习者");
        }
        if (learner.getRole() != Role.LEARNER) {
            throw new IllegalArgumentException("只能绑定学习者");
        }
        if (!tutorLearners.existsByTutorIdAndLearnerId(tutorId, learnerId)) {
            tutorLearners.save(new TutorLearner(tutorId, learnerId));
        }
    }

    public List<AppUser> learnersOf(Long tutorId) {
        return users.findAllById(tutorLearners.findByTutorId(tutorId).stream().map(TutorLearner::getLearnerId).toList());
    }

    public List<Long> tutorIdsOf(Long learnerId) {
        return tutorLearners.findByLearnerId(learnerId).stream().map(TutorLearner::getTutorId).toList();
    }

    public boolean isTutorOf(Long tutorId, Long learnerId) {
        return tutorLearners.existsByTutorIdAndLearnerId(tutorId, learnerId);
    }

    /** 本人、名下学习者的辅导者、运维可以查看学习者的数据。 */
    public void checkCanView(AppUser actor, Long learnerId) {
        if (actor.getId().equals(learnerId) || actor.getRole() == Role.OPS) {
            return;
        }
        if (isTutorOf(actor.getId(), learnerId)) {
            return;
        }
        throw new ForbiddenException("无权查看该学习者的数据");
    }
}
