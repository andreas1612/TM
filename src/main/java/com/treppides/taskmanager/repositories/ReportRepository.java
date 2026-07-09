package com.treppides.taskmanager.repositories;

import com.treppides.taskmanager.dto.EmployeeCompletionStat;
import com.treppides.taskmanager.dto.EmployeeWorkloadStat;
import com.treppides.taskmanager.entities.TaskHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface ReportRepository extends JpaRepository<TaskHistory, Integer> {

    /**
     * Counts tasks that reached a completed status within [start, end) per assigned employee.
     * A task with multiple assignees is counted once for each assignee.
     * COUNT(DISTINCT TaskId) collapses repeated completions of the same task in the window.
     */
    @Query(value = """
        SELECT e.EMAIL AS email,
               e.FULLNAME AS fullName,
               e.DEPARTMENTID AS departmentId,
               e.TEAMID AS teamId,
               COUNT(DISTINCT h.TaskId) AS completedCount
        FROM TaskHistory h
        JOIN TaskAssignments ta ON ta.TaskId = h.TaskId
        JOIN EMPLOYEES e ON ta.AssignedTo = e.EMAIL
        WHERE h.FieldChanged = 'Status'
          AND h.NewValue IN ('COMPLETED', 'DONE')
          AND h.ChangedAt >= :start
          AND h.ChangedAt < :end
        GROUP BY e.EMAIL, e.FULLNAME, e.DEPARTMENTID, e.TEAMID
        ORDER BY completedCount DESC
    """, nativeQuery = true)
    List<EmployeeCompletionStat> findCompletedPerEmployee(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    /**
     * Current (snapshot) workload per assigned employee, ignoring archived tasks.
     * assigned = all non-archived tasks assigned to the employee.
     * open     = assigned tasks not in a terminal status.
     * overdue  = open tasks whose DueDate is before :today.
     */
    @Query(value = """
        SELECT e.EMAIL AS email,
               e.FULLNAME AS fullName,
               e.DEPARTMENTID AS departmentId,
               e.TEAMID AS teamId,
               COUNT(DISTINCT t.TaskId) AS assignedCount,
               COUNT(DISTINCT CASE
                     WHEN t.Status NOT IN ('COMPLETED', 'DONE', 'CANCELLED')
                     THEN t.TaskId END) AS openCount,
               COUNT(DISTINCT CASE
                     WHEN t.Status NOT IN ('COMPLETED', 'DONE', 'CANCELLED')
                          AND t.DueDate IS NOT NULL
                          AND t.DueDate < :today
                     THEN t.TaskId END) AS overdueCount
        FROM Tasks t
        JOIN TaskAssignments ta ON ta.TaskId = t.TaskId
        JOIN EMPLOYEES e ON ta.AssignedTo = e.EMAIL
        WHERE COALESCE(t.IsArchived, 0) = 0
        GROUP BY e.EMAIL, e.FULLNAME, e.DEPARTMENTID, e.TEAMID
    """, nativeQuery = true)
    List<EmployeeWorkloadStat> findWorkloadPerEmployee(@Param("today") LocalDate today);
}
