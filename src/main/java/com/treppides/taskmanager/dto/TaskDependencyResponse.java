package com.treppides.taskmanager.dto;

import com.treppides.taskmanager.entities.TaskDependency;
import java.util.List;

public class TaskDependencyResponse {

    private Integer dependencyId;
    private Integer taskId;
    private Integer dependsOnTaskId;
    private String dependsOnTitle;
    private String dependsOnStatus;
    private String dependsOnPriority;
    private java.time.LocalDate dependsOnDueDate;
    private String dependencyType;
    private List<TaskDependencyResponse> dependencies;

    public TaskDependencyResponse(
            TaskDependency dependency,
            List<TaskDependencyResponse> dependencies
    ) {
        this.dependencyId = dependency.getDependencyId();
        this.taskId = dependency.getTask().getTaskId();
        this.dependsOnTaskId = dependency.getDependsOnTask().getTaskId();
        this.dependsOnTitle = dependency.getDependsOnTask().getTitle();
        this.dependsOnStatus = dependency.getDependsOnTask().getStatus();
        this.dependsOnPriority = dependency.getDependsOnTask().getPriority();
        this.dependsOnDueDate = dependency.getDependsOnTask().getDueDate();
        this.dependencyType = dependency.getDependencyType();
        this.dependencies = dependencies;
    }

    public Integer getDependencyId() { return dependencyId; }
    public Integer getTaskId() { return taskId; }
    public Integer getDependsOnTaskId() { return dependsOnTaskId; }
    public String getDependsOnTitle() { return dependsOnTitle; }
    public String getDependsOnStatus() { return dependsOnStatus; }
    public String getDependsOnPriority() { return dependsOnPriority; }
    public java.time.LocalDate getDependsOnDueDate() { return dependsOnDueDate; }
    public String getDependencyType() { return dependencyType; }
    public List<TaskDependencyResponse> getDependencies() { return dependencies; }
}
