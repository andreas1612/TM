package com.treppides.taskmanager.dto;

/**
 * Combined roll-up row for a team or department.
 * completedCount is within the requested date range; assigned/open/overdue are a current snapshot.
 * Each task is counted once per group (COUNT DISTINCT), regardless of how many members it is assigned to.
 */
public class GroupStatsResponse {

    private String groupKey;
    private String groupName;
    private String groupType;
    private long completedCount;
    private long assignedCount;
    private long openCount;
    private long overdueCount;

    public String getGroupKey() { return groupKey; }
    public void setGroupKey(String groupKey) { this.groupKey = groupKey; }

    public String getGroupType() { return groupType; }
    public void setGroupType(String groupType) { this.groupType = groupType; }

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
