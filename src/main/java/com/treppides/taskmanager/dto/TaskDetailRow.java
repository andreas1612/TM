package com.treppides.taskmanager.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * One task line in an employee's detailed report.
 * A task is included if it is currently open OR was completed within the report's date range.
 */
public class TaskDetailRow {

    private Integer taskId;
    private String title;
    private String status;
    private String priority;
    private String client;
    private LocalDate dueDate;
    private List<String> assignedTo;   // full names of the people the task is assigned to
    private LocalDate completedAt;      // date the task last reached a completed status (null if not completed)
    private boolean completed;          // completed within the report's date range
    private boolean open;               // currently in a non-terminal status
    private boolean overdue;            // missed deadline (open & past due, or completed after due date)
    private Integer minutesToComplete;  // how long it took (completed tasks)
    private Integer minutesOpen;        // how long it has been open (open tasks)

    public Integer getTaskId() { return taskId; }
    public void setTaskId(Integer taskId) { this.taskId = taskId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public String getClient() { return client; }
    public void setClient(String client) { this.client = client; }

    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }

    public List<String> getAssignedTo() { return assignedTo; }
    public void setAssignedTo(List<String> assignedTo) { this.assignedTo = assignedTo; }

    public LocalDate getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDate completedAt) { this.completedAt = completedAt; }

    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }

    public boolean isOpen() { return open; }
    public void setOpen(boolean open) { this.open = open; }

    public boolean isOverdue() { return overdue; }
    public void setOverdue(boolean overdue) { this.overdue = overdue; }

    public Integer getMinutesToComplete() { return minutesToComplete; }
    public void setMinutesToComplete(Integer minutesToComplete) { this.minutesToComplete = minutesToComplete; }

    public Integer getMinutesOpen() { return minutesOpen; }
    public void setMinutesOpen(Integer minutesOpen) { this.minutesOpen = minutesOpen; }
}
