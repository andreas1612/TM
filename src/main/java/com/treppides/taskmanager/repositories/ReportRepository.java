package com.treppides.taskmanager.repositories;

import com.treppides.taskmanager.dto.EmployeeCompletionStat;
import com.treppides.taskmanager.dto.EmployeeWorkloadStat;
import com.treppides.taskmanager.dto.GroupCompletionStat;
import com.treppides.taskmanager.dto.GroupWorkloadStat;
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
     * overdue  = non-archived tasks past their DueDate that either are still open
     *            OR were completed after the DueDate (missed the deadline). Because a
     *            completed-late task is overdue but not open, overdue is NOT a subset of open.
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
                     WHEN t.DueDate IS NOT NULL
                          AND t.DueDate < :today
                          AND (
                              t.Status NOT IN ('COMPLETED', 'DONE', 'CANCELLED')
                              OR (comp.lastCompletedDate IS NOT NULL
                                  AND comp.lastCompletedDate > t.DueDate)
                          )
                     THEN t.TaskId END) AS overdueCount
        FROM Tasks t
        JOIN TaskAssignments ta ON ta.TaskId = t.TaskId
        JOIN EMPLOYEES e ON ta.AssignedTo = e.EMAIL
        LEFT JOIN (
            SELECT h2.TaskId AS taskId,
                   MAX(CAST(h2.ChangedAt AS DATE)) AS lastCompletedDate
            FROM TaskHistory h2
            WHERE h2.FieldChanged = 'Status'
              AND h2.NewValue IN ('COMPLETED', 'DONE')
            GROUP BY h2.TaskId
        ) comp ON comp.taskId = t.TaskId
        WHERE COALESCE(t.IsArchived, 0) = 0
        GROUP BY e.EMAIL, e.FULLNAME, e.DEPARTMENTID, e.TEAMID
    """, nativeQuery = true)
    List<EmployeeWorkloadStat> findWorkloadPerEmployee(@Param("today") LocalDate today);

    // ----- Team roll-ups (each task counted once per team) -----

    @Query(value = """
        SELECT e.TEAMID AS groupId,
               tm.NAME AS groupName,
               COUNT(DISTINCT h.TaskId) AS completedCount
        FROM TaskHistory h
        JOIN TaskAssignments ta ON ta.TaskId = h.TaskId
        JOIN EMPLOYEES e ON ta.AssignedTo = e.EMAIL
        LEFT JOIN TEAMS tm ON tm.ID = e.TEAMID
        WHERE h.FieldChanged = 'Status'
          AND h.NewValue IN ('COMPLETED', 'DONE')
          AND h.ChangedAt >= :start
          AND h.ChangedAt < :end
        GROUP BY e.TEAMID, tm.NAME
    """, nativeQuery = true)
    List<GroupCompletionStat> findCompletedPerTeam(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query(value = """
        SELECT e.TEAMID AS groupId,
               tm.NAME AS groupName,
               COUNT(DISTINCT t.TaskId) AS assignedCount,
               COUNT(DISTINCT CASE
                     WHEN t.Status NOT IN ('COMPLETED', 'DONE', 'CANCELLED')
                     THEN t.TaskId END) AS openCount,
               COUNT(DISTINCT CASE
                     WHEN t.DueDate IS NOT NULL
                          AND t.DueDate < :today
                          AND (
                              t.Status NOT IN ('COMPLETED', 'DONE', 'CANCELLED')
                              OR (comp.lastCompletedDate IS NOT NULL
                                  AND comp.lastCompletedDate > t.DueDate)
                          )
                     THEN t.TaskId END) AS overdueCount
        FROM Tasks t
        JOIN TaskAssignments ta ON ta.TaskId = t.TaskId
        JOIN EMPLOYEES e ON ta.AssignedTo = e.EMAIL
        LEFT JOIN TEAMS tm ON tm.ID = e.TEAMID
        LEFT JOIN (
            SELECT h2.TaskId AS taskId,
                   MAX(CAST(h2.ChangedAt AS DATE)) AS lastCompletedDate
            FROM TaskHistory h2
            WHERE h2.FieldChanged = 'Status'
              AND h2.NewValue IN ('COMPLETED', 'DONE')
            GROUP BY h2.TaskId
        ) comp ON comp.taskId = t.TaskId
        WHERE COALESCE(t.IsArchived, 0) = 0
        GROUP BY e.TEAMID, tm.NAME
    """, nativeQuery = true)
    List<GroupWorkloadStat> findWorkloadPerTeam(@Param("today") LocalDate today);

    // ----- Department roll-ups (each task counted once per department) -----

    @Query(value = """
        SELECT e.DEPARTMENTID AS groupId,
               d.NAME AS groupName,
               COUNT(DISTINCT h.TaskId) AS completedCount
        FROM TaskHistory h
        JOIN TaskAssignments ta ON ta.TaskId = h.TaskId
        JOIN EMPLOYEES e ON ta.AssignedTo = e.EMAIL
        LEFT JOIN DEPARTMENTS d ON d.ID = e.DEPARTMENTID
        WHERE h.FieldChanged = 'Status'
          AND h.NewValue IN ('COMPLETED', 'DONE')
          AND h.ChangedAt >= :start
          AND h.ChangedAt < :end
        GROUP BY e.DEPARTMENTID, d.NAME
    """, nativeQuery = true)
    List<GroupCompletionStat> findCompletedPerDepartment(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query(value = """
        SELECT e.DEPARTMENTID AS groupId,
               d.NAME AS groupName,
               COUNT(DISTINCT t.TaskId) AS assignedCount,
               COUNT(DISTINCT CASE
                     WHEN t.Status NOT IN ('COMPLETED', 'DONE', 'CANCELLED')
                     THEN t.TaskId END) AS openCount,
               COUNT(DISTINCT CASE
                     WHEN t.DueDate IS NOT NULL
                          AND t.DueDate < :today
                          AND (
                              t.Status NOT IN ('COMPLETED', 'DONE', 'CANCELLED')
                              OR (comp.lastCompletedDate IS NOT NULL
                                  AND comp.lastCompletedDate > t.DueDate)
                          )
                     THEN t.TaskId END) AS overdueCount
        FROM Tasks t
        JOIN TaskAssignments ta ON ta.TaskId = t.TaskId
        JOIN EMPLOYEES e ON ta.AssignedTo = e.EMAIL
        LEFT JOIN DEPARTMENTS d ON d.ID = e.DEPARTMENTID
        LEFT JOIN (
            SELECT h2.TaskId AS taskId,
                   MAX(CAST(h2.ChangedAt AS DATE)) AS lastCompletedDate
            FROM TaskHistory h2
            WHERE h2.FieldChanged = 'Status'
              AND h2.NewValue IN ('COMPLETED', 'DONE')
            GROUP BY h2.TaskId
        ) comp ON comp.taskId = t.TaskId
        WHERE COALESCE(t.IsArchived, 0) = 0
        GROUP BY e.DEPARTMENTID, d.NAME
    """, nativeQuery = true)
    List<GroupWorkloadStat> findWorkloadPerDepartment(@Param("today") LocalDate today);
}
