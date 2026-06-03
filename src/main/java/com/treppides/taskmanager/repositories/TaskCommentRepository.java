package com.treppides.taskmanager.repositories;

import com.treppides.taskmanager.entities.TaskComment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TaskCommentRepository extends JpaRepository<TaskComment, Integer> {
    List<TaskComment> findByTask_TaskIdOrderByCreatedAtAsc(Integer taskId);
}