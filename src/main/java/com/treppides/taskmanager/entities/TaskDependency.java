package com.treppides.taskmanager.entities;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "TaskDependencies", schema = "dbo")
public class TaskDependency {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "TaskDependencyId")
    private Integer dependencyId;

    @ManyToOne
    @JoinColumn(name = "TaskId", nullable = false)
    private Task task;

    @ManyToOne
    @JoinColumn(name = "DependsOnTaskId", nullable = false)
    private Task dependsOnTask;

    @Column(name = "CreatedAt")
    private LocalDateTime createdAt;

    @Column(name = "DependencyType", nullable = false, length = 50)
    private String dependencyType = "BLOCKING";

    @PrePersist
    public void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Integer getDependencyId() {
        return dependencyId;
    }

    public void setDependencyId(Integer dependencyId) {
        this.dependencyId = dependencyId;
    }

    public Task getTask() {
        return task;
    }

    public void setTask(Task task) {
        this.task = task;
    }

    public Task getDependsOnTask() {
        return dependsOnTask;
    }

    public void setDependsOnTask(Task dependsOnTask) {
        this.dependsOnTask = dependsOnTask;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getDependencyType() {
        return dependencyType;
    }

    public void setDependencyType(String dependencyType) {
        this.dependencyType = dependencyType;
    }
}
