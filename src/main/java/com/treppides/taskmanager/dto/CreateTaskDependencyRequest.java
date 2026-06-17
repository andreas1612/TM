package com.treppides.taskmanager.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class CreateTaskDependencyRequest {

    @NotNull
    private Integer dependsOnTaskId;

    @Size(max = 50)
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
