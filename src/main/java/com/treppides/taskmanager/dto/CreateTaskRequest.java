package com.treppides.taskmanager.dto;

import java.time.LocalDate;
import java.util.List;

public class CreateTaskRequest {

    private String title;
    private String description;
    private String createdBy;
    private String status;
    private String priority;
    private LocalDate startDate;
    private LocalDate dueDate;
    private Integer parentTaskId;
    private List<String> assignedTo;
    private String client;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }

    public Integer getParentTaskId() { return parentTaskId; }
    public void setParentTaskId(Integer parentTaskId) { this.parentTaskId = parentTaskId; }

    public List<String> getAssignedTo() { return assignedTo; }
    public void setAssignedTo(List<String> assignedTo) { this.assignedTo = assignedTo; }

    public String getClient() { return client; }
    public void setClient(String client) { this.client = client; }
}