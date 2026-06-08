package com.treppides.taskmanager.dto;

import com.treppides.taskmanager.entities.Task;
import com.treppides.taskmanager.entities.TaskAssignment;

import java.time.LocalDate;
import java.util.List;

public class TaskResponse {

    private Integer taskId;
    private String title;
    private String description;
    private String status;
    private String priority;
    private LocalDate dueDate;
    private List<String> assignedTo;
    private String client;

    public TaskResponse(Task task, List<TaskAssignment> assignments) {
        this.taskId = task.getTaskId();
        this.title = task.getTitle();
        this.description = task.getDescription();
        this.status = task.getStatus();
        this.priority = task.getPriority();
        this.dueDate = task.getDueDate();
        this.client = task.getClient();

        this.assignedTo = assignments.stream()
                .map(a -> a.getAssignedTo().getEmail())
                .toList();
    }

    public Integer getTaskId() { return taskId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getStatus() { return status; }
    public String getPriority() { return priority; }
    public LocalDate getDueDate() { return dueDate; }
    public List<String> getAssignedTo() { return assignedTo; }
    public String getClient() { return client;}
}