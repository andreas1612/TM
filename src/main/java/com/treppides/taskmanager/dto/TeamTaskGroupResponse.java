package com.treppides.taskmanager.dto;

import java.util.List;

public class TeamTaskGroupResponse {

    private String groupKey;
    private String groupName;
    private String groupType;
    private Integer teamId;
    private Integer departmentId;
    private List<TaskResponse> tasks;

    public TeamTaskGroupResponse(
            String groupKey,
            String groupName,
            String groupType,
            Integer teamId,
            Integer departmentId,
            List<TaskResponse> tasks
    ) {
        this.groupKey = groupKey;
        this.groupName = groupName;
        this.groupType = groupType;
        this.teamId = teamId;
        this.departmentId = departmentId;
        this.tasks = tasks;
    }

    public String getGroupKey() { return groupKey; }
    public String getGroupName() { return groupName; }
    public String getGroupType() { return groupType; }
    public Integer getTeamId() { return teamId; }
    public Integer getDepartmentId() { return departmentId; }
    public List<TaskResponse> getTasks() { return tasks; }
}
