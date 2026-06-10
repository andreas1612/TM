package com.treppides.taskmanager.dto;

public class CreateTaskDependencyRequest {

    private Integer dependsOnTaskId;
    private String dependencyType;

    public Integer getDependsOnTaskId() {
        return dependsOnTaskId;
    }

    public void setDependsOnTaskId(Integer dependsOnTaskId) {
        this.dependsOnTaskId = dependsOnTaskId;
    }

    public String getDependencyType() {
        return dependencyType;
    }

    public void setDependencyType(String dependencyType) {
        this.dependencyType = dependencyType;
    }
}
