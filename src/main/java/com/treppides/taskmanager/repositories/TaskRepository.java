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
        JOIN EMPLOYEES currentUser ON currentUser.EMAIL = :email
        WHERE e.TEAMID = currentUser.TEAMID
          AND e.ISACTIVE = 1
    """, nativeQuery = true)
    List<Task> findTasksForTeam(@Param("email") String email);

    @Query(value = """
        SELECT DISTINCT candidate.*
        FROM Tasks candidate
        JOIN TaskAssignments candidateAssignment
            ON candidate.TaskId = candidateAssignment.TaskId
        JOIN EMPLOYEES candidateEmployee
            ON candidateAssignment.AssignedTo = candidateEmployee.EMAIL
        JOIN TaskAssignments currentAssignment
            ON currentAssignment.TaskId = :taskId
        JOIN EMPLOYEES currentEmployee
            ON currentAssignment.AssignedTo = currentEmployee.EMAIL
        WHERE candidate.TaskId <> :taskId
          AND candidateEmployee.ISACTIVE = 1
          AND (
              (
                  currentEmployee.TEAMID IS NOT NULL
                  AND candidateEmployee.TEAMID = currentEmployee.TEAMID
              )
              OR (
                  currentEmployee.TEAMID IS NULL
                  AND candidateEmployee.TEAMID IS NULL
                  AND candidateEmployee.DEPARTMENTID = currentEmployee.DEPARTMENTID
              )
          )
    """, nativeQuery = true)
    List<Task> findDependencyCandidatesForTask(@Param("taskId") Integer taskId);

    List<Task> findByDueDateIsNotNullAndStatusNotIn(List<String> statuses);
}
