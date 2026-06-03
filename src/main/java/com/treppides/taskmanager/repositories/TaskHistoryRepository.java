package com.treppides.taskmanager.repositories;

import com.treppides.taskmanager.entities.TaskHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TaskHistoryRepository extends JpaRepository<TaskHistory, Integer> {
    List<TaskHistory> findByTask_TaskIdOrderByChangedAtAsc(Integer taskId);
}