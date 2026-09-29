package com.learnmap.plan;

import com.learnmap.common.CurrentUser;
import com.learnmap.plan.PlanService.DayPlan;
import com.learnmap.plan.PlanService.GoalRequest;
import com.learnmap.plan.PlanService.GoalView;
import com.learnmap.plan.PlanService.TaskView;
import com.learnmap.plan.PomodoroService.PomodoroView;
import com.learnmap.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class PlanController {

    private final PlanService plans;
    private final PomodoroService pomodoros;
    private final UserService users;

    public record MarkRequest(TaskStatus status) {
    }

    public record PomodoroStart(Long taskId, Integer minutes) {
    }

    public record PomodoroFinish(boolean completed, int interruptions) {
    }

    @PostMapping("/api/goals")
    public DayPlan createGoal(@RequestHeader(CurrentUser.HEADER) Long userId, @RequestBody GoalRequest req) {
        return plans.createGoal(users.require(userId), req);
    }

    @GetMapping("/api/learners/{learnerId}/goals")
    public List<GoalView> goals(@RequestHeader(CurrentUser.HEADER) Long userId, @PathVariable Long learnerId) {
        return plans.goalsOf(users.require(userId), learnerId);
    }

    /** 某天的计划（默认今天），按番茄块分组，附未来几天的概况 */
    @GetMapping("/api/learners/{learnerId}/plan")
    public DayPlan plan(@RequestHeader(CurrentUser.HEADER) Long userId, @PathVariable Long learnerId,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return plans.today(users.require(userId), learnerId, date);
    }

    @PostMapping("/api/learners/{learnerId}/plan/replan")
    public DayPlan replan(@RequestHeader(CurrentUser.HEADER) Long userId, @PathVariable Long learnerId) {
        return plans.replan(users.require(userId), learnerId);
    }

    @PostMapping("/api/plan/tasks/{taskId}")
    public TaskView mark(@RequestHeader(CurrentUser.HEADER) Long userId, @PathVariable Long taskId, @RequestBody MarkRequest req) {
        return plans.mark(users.require(userId), taskId, req.status() == null ? TaskStatus.DONE : req.status());
    }

    @PostMapping("/api/pomodoros")
    public PomodoroView startPomodoro(@RequestHeader(CurrentUser.HEADER) Long userId, @RequestBody PomodoroStart req) {
        return pomodoros.start(users.require(userId), req.taskId(), req.minutes());
    }

    @PostMapping("/api/pomodoros/{id}/finish")
    public PomodoroView finishPomodoro(@RequestHeader(CurrentUser.HEADER) Long userId, @PathVariable Long id,
                                       @RequestBody PomodoroFinish req) {
        return pomodoros.finish(users.require(userId), id, req.completed(), req.interruptions());
    }

    @GetMapping("/api/pomodoros")
    public List<PomodoroView> pomodoros(@RequestHeader(CurrentUser.HEADER) Long userId) {
        return pomodoros.history(users.require(userId));
    }
}
