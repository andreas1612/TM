package com.treppides.taskmanager.dto;

/**
 * Projection for completed-tasks-per-group within a date range.
 * A "group" is a team, or (for employees with no team) their department.
 * groupKey uniquely identifies the group (e.g. "team:5" / "dept:11"); groupType is TEAM or DEPARTMENT.
 */
public interface GroupCompletionStat {
    String getGroupKey();
    String getGroupName();
    String getGroupType();
    Long getCompletedCount();
}
