package com.treppides.taskmanager.dto;

import java.util.List;

/**
 * Detailed, task-level report for a single employee over a date range.
 */
public class EmployeeReportDetail {

    private String email;
    private String fullName;
    private List<TaskDetailRow> tasks;

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public List<TaskDetailRow> getTasks() { return tasks; }
    public void setTasks(List<TaskDetailRow> tasks) { this.tasks = tasks; }
}
