package com.treppides.taskmanager.dto;

public class CreateTaskDependencyRequest {

    private Integer dependsOnTaskId;

    public Integer getDependsOnTaskId() {
        return dependsOnTaskId;
    }

    public void setDependsOnTaskId(Integer dependsOnTaskId) {
        this.dependsOnTaskId = dependsOnTaskId;
    }
}