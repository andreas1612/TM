package com.treppides.taskmanager.entities;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "TaskChecklistItems", schema = "dbo")
public class TaskChecklistItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ChecklistItemId")
    private Integer checklistItemId;

    @ManyToOne
    @JoinColumn(name = "TaskId", nullable = false)
    private Task task;

    @Column(name = "ItemText", nullable = false)
    private String itemText;

    @Column(name = "IsCompleted", nullable = false)
    private Boolean isCompleted = false;

    @Column(name = "SortOrder")
    private Integer sortOrder;

    @Column(name = "CreatedAt")
    private LocalDateTime createdAt;

    @PrePersist
    public void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }

        if (isCompleted == null) {
            isCompleted = false;
        }
    }

    public Integer getChecklistItemId() {
        return checklistItemId;
    }

    public void setChecklistItemId(Integer checklistItemId) {
        this.checklistItemId = checklistItemId;
    }

    public Task getTask() {
        return task;
    }

    public void setTask(Task task) {
        this.task = task;
    }

    public String getItemText() {
        return itemText;
    }

    public void setItemText(String itemText) {
        this.itemText = itemText;
    }

    public Boolean getIsCompleted() {
        return isCompleted;
    }

    public void setIsCompleted(Boolean completed) {
        isCompleted = completed;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}