package com.learnmap.plan;

import com.learnmap.common.ForbiddenException;
import com.learnmap.common.NotFoundException;
import com.learnmap.user.AppUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/** 番茄钟记录：计时本身在前端，这里记下开始、结束、有没有坚持完、被打断几次，供行为画像使用 */
@Service
@RequiredArgsConstructor
public class PomodoroService {

    private final PomodoroSessionRepository sessions;
    private final PlanTaskRepository tasks;
    private final Clock clock;

    public record PomodoroView(Long id, Long taskId, int plannedMinutes, LocalDateTime startedAt, LocalDateTime endedAt,
                               boolean completed, int interruptions) {
        static PomodoroView of(PomodoroSession s) {
            return new PomodoroView(s.getId(), s.getTaskId(), s.getPlannedMinutes(), s.getStartedAt(), s.getEndedAt(),
                    s.isCompleted(), s.getInterruptions());
        }
    }

    @Transactional
    public PomodoroView start(AppUser actor, Long taskId, Integer minutes) {
        if (taskId != null) {
            PlanTask t = tasks.findById(taskId).orElseThrow(() -> NotFoundException.of("任务", taskId));
            if (!t.getLearnerId().equals(actor.getId())) {
                throw new ForbiddenException("只能给自己的任务计时");
            }
        }
        PomodoroSession s = new PomodoroSession();
        s.setLearnerId(actor.getId());
        s.setTaskId(taskId);
        s.setPlannedMinutes(minutes == null || minutes <= 0 ? Rhythm.POMODORO.blockMinutes : minutes);
        s.setStartedAt(LocalDateTime.now(clock));
        return PomodoroView.of(sessions.save(s));
    }

    @Transactional
    public PomodoroView finish(AppUser actor, Long id, boolean completed, int interruptions) {
        PomodoroSession s = sessions.findById(id).orElseThrow(() -> NotFoundException.of("番茄钟", id));
        if (!s.getLearnerId().equals(actor.getId())) {
            throw new ForbiddenException("只能结束自己的番茄钟");
        }
        if (s.getEndedAt() != null) {
            throw new IllegalStateException("这个番茄钟已经结束了");
        }
        s.setEndedAt(LocalDateTime.now(clock));
        s.setCompleted(completed);
        s.setInterruptions(Math.max(0, interruptions));
        return PomodoroView.of(s);
    }

    public List<PomodoroView> history(AppUser actor) {
        return sessions.findByLearnerIdOrderByStartedAtDesc(actor.getId()).stream().map(PomodoroView::of).toList();
    }
}
