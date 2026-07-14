package com.treppides.taskmanager.repositories;

import com.treppides.taskmanager.entities.TaskHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface TaskHistoryRepository extends JpaRepository<TaskHistory, Integer> {
    List<TaskHistory> findByTask_TaskIdOrderByChangedAtAsc(Integer taskId);

    Optional<TaskHistory> findFirstByTask_TaskIdAndFieldChangedAndNewValueOrderByChangedAtAsc(
            Integer taskId, String fieldChanged, String newValue);
}