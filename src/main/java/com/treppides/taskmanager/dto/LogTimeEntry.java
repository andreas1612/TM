package com.treppides.taskmanager.dto;

/**
 * A single per-task time entry submitted from the daily time-log page:
 * how many minutes the user worked on {@code taskId} today.
 */
public class LogTimeEntry {

    private Integer taskId;
    private Integer minutes;

    public Integer getTaskId() { return taskId; }
    public void setTaskId(Integer taskId) { this.taskId = taskId; }

    public Integer getMinutes() { return minutes; }
    public void setMinutes(Integer minutes) { this.minutes = minutes; }
}
