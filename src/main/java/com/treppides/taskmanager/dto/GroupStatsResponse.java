package com.treppides.taskmanager.dto;

/**
 * Combined roll-up row for a team or department.
 * completedCount is within the requested date range; assigned/open/overdue are a current snapshot.
 * Each task is counted once per group (COUNT DISTINCT), regardless of how many members it is assigned to.
 */
public class GroupStatsResponse {

    private Integer groupId;
    private String groupName;
    private long completedCount;
    private long assignedCount;
    private long openCount;
    private long overdueCount;

    public Integer getGroupId() { return groupId; }
    public void setGroupId(Integer groupId) { this.groupId = groupId; }

    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }

    public long getCompletedCount() { return completedCount; }
    public void setCompletedCount(long completedCount) { this.completedCount = completedCount; }

    public long getAssignedCount() { return assignedCount; }
    public void setAssignedCount(long assignedCount) { this.assignedCount = assignedCount; }

    public long getOpenCount() { return openCount; }
    public void setOpenCount(long openCount) { this.openCount = openCount; }

    public long getOverdueCount() { return overdueCount; }
    public void setOverdueCount(long overdueCount) { this.overdueCount = overdueCount; }
}
