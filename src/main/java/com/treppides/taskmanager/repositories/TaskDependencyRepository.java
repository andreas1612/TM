package com.treppides.taskmanager.repositories;

import com.treppides.taskmanager.entities.TaskDependency;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskDependencyRepository extends JpaRepository<TaskDependency, Integer> {

    List<TaskDependency> findByTask_TaskId(Integer taskId);

    boolean existsByTask_TaskIdAndDependsOnTask_TaskId(Integer taskId, Integer dependsOnTaskId);
}