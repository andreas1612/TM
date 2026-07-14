package com.treppides.taskmanager.dto;

/**
 * Projection for an employee's current (snapshot) workload — as of "today", not date-ranged.
 * Populated by a native query; Spring maps each column alias to the matching getter.
 */
public interface EmployeeWorkloadStat {
    String getEmail();
    String getFullName();
    Integer getDepartmentId();
    Integer getTeamId();
    Long getAssignedCount();
    Long getOpenCount();
    Long getOverdueCount();
}
