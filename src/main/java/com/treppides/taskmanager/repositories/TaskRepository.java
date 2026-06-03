package com.treppides.taskmanager.repositories;

import com.treppides.taskmanager.entities.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Integer> {

    @Query(value = """
        SELECT DISTINCT t.*
        FROM Tasks t
        JOIN TaskAssignments ta ON t.TaskId = ta.TaskId
        JOIN EMPLOYEES e ON ta.AssignedTo = e.EMAIL
        WHERE e.SUPERVISORID = :email
    """, nativeQuery = true)
    List<Task> findTasksForTeam(@Param("email") String email);

    List<Task> findByDueDateIsNotNullAndStatusNotIn(List<String> statuses);
}
