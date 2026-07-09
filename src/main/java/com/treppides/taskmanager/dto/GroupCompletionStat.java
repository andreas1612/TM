package com.treppides.taskmanager.dto;

/**
 * Projection for completed-tasks-per-group (team or department) within a date range.
 * groupId/groupName are aliased to the team or department id/name by each query.
 */
public interface GroupCompletionStat {
    Integer getGroupId();
    String getGroupName();
    Long getCompletedCount();
}
