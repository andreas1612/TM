package com.treppides.taskmanager.dto;

/**
 * Projection for current (snapshot) workload per group.
 * A "group" is a team, or (for employees with no team) their department.
 * groupKey uniquely identifies the group (e.g. "team:5" / "dept:11"); groupType is TEAM or DEPARTMENT.
 */
public interface GroupWorkloadStat {
    String getGroupKey();
    String getGroupName();
    String getGroupType();
    Long getAssignedCount();
    Long getOpenCount();
    Long getOverdueCount();
}
