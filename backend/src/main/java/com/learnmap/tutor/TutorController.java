package com.learnmap.tutor;

import com.learnmap.common.CurrentUser;
import com.learnmap.flag.FlagKind;
import com.learnmap.flag.FlagService;
import com.learnmap.flag.FlagService.FlagView;
import com.learnmap.flag.FlagService.FlaggedNode;
import com.learnmap.profile.LearnerProfile;
import com.learnmap.profile.LearnerProfile.Highlights;
import com.learnmap.profile.ProfileService;
import com.learnmap.tutor.TutorService.LearnerCard;
import com.learnmap.tutor.TutorService.Trace;
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
@RequestMapping("/api")
@RequiredArgsConstructor
public class TutorController {

    private final TutorService tutors;
    private final ProfileService profiles;
    private final FlagService flags;
    private final UserService users;

    public record FlagRequest(FlagKind kind, String note) {
    }

    @GetMapping("/tutor/dashboard")
    public List<LearnerCard> dashboard(@RequestHeader(CurrentUser.HEADER) Long userId) {
        return tutors.dashboard(users.require(userId));
    }

    /** 完整五张画像：辅导者、运维，以及想看数字的学习者本人 */
    @GetMapping("/learners/{learnerId}/profile")
    public LearnerProfile profile(@RequestHeader(CurrentUser.HEADER) Long userId, @PathVariable Long learnerId) {
        return profiles.profile(users.require(userId), learnerId);
    }

    /** 学习者首页只看正向信息 */
    @GetMapping("/learners/{learnerId}/highlights")
    public Highlights highlights(@RequestHeader(CurrentUser.HEADER) Long userId, @PathVariable Long learnerId) {
        return profiles.highlights(users.require(userId), learnerId);
    }

    @GetMapping("/learners/{learnerId}/nodes/{nodeId}/trace")
    public Trace trace(@RequestHeader(CurrentUser.HEADER) Long userId, @PathVariable Long learnerId, @PathVariable Long nodeId) {
        return tutors.trace(users.require(userId), learnerId, nodeId);
    }

    @PostMapping("/learners/{learnerId}/nodes/{nodeId}/flags")
    public FlagView flag(@RequestHeader(CurrentUser.HEADER) Long userId, @PathVariable Long learnerId, @PathVariable Long nodeId,
                         @RequestBody FlagRequest req) {
        return flags.tutorFlag(users.require(userId), learnerId, nodeId, req.kind(), req.note());
    }

    @GetMapping("/learners/{learnerId}/flags")
    public List<FlagView> flags(@RequestHeader(CurrentUser.HEADER) Long userId, @PathVariable Long learnerId) {
        return flags.of(users.require(userId), learnerId);
    }

    @PostMapping("/flags/{flagId}/resolve")
    public FlagView resolve(@RequestHeader(CurrentUser.HEADER) Long userId, @PathVariable Long flagId) {
        return flags.resolve(users.require(userId), flagId);
    }

    /** 运维：哪些书本图谱节点常被辅导者标“诊断不对”，用来发现题目或图谱的问题 */
    @GetMapping("/ops/diagnosis-flags")
    public List<FlaggedNode> flaggedNodes(@RequestHeader(CurrentUser.HEADER) Long userId) {
        return flags.diagnosisWrongByMasterNode(users.require(userId));
    }
}
