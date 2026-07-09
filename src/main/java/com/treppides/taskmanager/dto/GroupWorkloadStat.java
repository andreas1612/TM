package com.treppides.taskmanager.dto;

/**
 * Projection for current (snapshot) workload per group (team or department).
 * groupId/groupName are aliased to the team or department id/name by each query.
 */
public interface GroupWorkloadStat {
    Integer getGroupId();
    String getGroupName();
    Long getAssignedCount();
    Long getOpenCount();
    Long getOverdueCount();
}
