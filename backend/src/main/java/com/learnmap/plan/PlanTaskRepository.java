package com.learnmap.plan;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface PlanTaskRepository extends JpaRepository<PlanTask, Long> {

    List<PlanTask> findByGoalIdAndPlanDateOrderByBlockNoAscOrdinalAsc(Long goalId, LocalDate date);

    List<PlanTask> findByGoalIdAndPlanDateBetweenOrderByPlanDateAscBlockNoAscOrdinalAsc(Long goalId, LocalDate from, LocalDate to);

    List<PlanTask> findByGoalIdAndStatus(Long goalId, TaskStatus status);

    List<PlanTask> findByLearnerId(Long learnerId);

    void deleteByGoalIdAndStatusAndPlanDateGreaterThanEqual(Long goalId, TaskStatus status, LocalDate from);
}
