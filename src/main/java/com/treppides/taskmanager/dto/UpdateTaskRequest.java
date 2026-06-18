package com.treppides.taskmanager.dto;

import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public class UpdateTaskRequest {

    @Size(max = 500)
    private String title;

    @Size(max = 5000)
    private String description;

    @Size(max = 50)
    private String status;

    @Size(max = 50)
    private String priority;

    private LocalDate dueDate;
    private List<String> assignedTo;

    @Size(max = 200)
    private String client;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }

    public List<String> getAssignedTo() { return assignedTo; }
    public void setAssignedTo(List<String> assignedTo) { this.assignedTo = assignedTo; }

    public String getClient() { return client; }
    public void setClient(String client) { this.client = client; }
}
