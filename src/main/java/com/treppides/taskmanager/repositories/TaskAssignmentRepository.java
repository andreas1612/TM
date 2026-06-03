package com.treppides.taskmanager.repositories;

import com.treppides.taskmanager.entities.TaskAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TaskAssignmentRepository extends JpaRepository<TaskAssignment, Integer> {
    List<TaskAssignment> findByAssignedTo_Email(String email);
    List<TaskAssignment> findByTask_TaskId(Integer taskId);
    void deleteByTask_TaskId(Integer taskId);
}