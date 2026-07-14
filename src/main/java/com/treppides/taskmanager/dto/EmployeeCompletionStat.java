package com.treppides.taskmanager.dto;

/**
 * Projection for the "tasks completed per employee" report.
 * Populated by a native query; Spring maps each column alias to the matching getter.
 */
public interface EmployeeCompletionStat {
    String getEmail();
    String getFullName();
    Integer getDepartmentId();
    Integer getTeamId();
    Long getCompletedCount();
}
