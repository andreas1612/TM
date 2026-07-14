package com.treppides.taskmanager.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class UpdateStatusRequest {

    @NotBlank @Size(max = 50)
    private String status;

    private String changedBy;
    private Integer timeSpentMinutes;

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getChangedBy() { return changedBy; }
    public void setChangedBy(String changedBy) { this.changedBy = changedBy; }

    public Integer getTimeSpentMinutes() { return timeSpentMinutes; }
    public void setTimeSpentMinutes(Integer timeSpentMinutes) { this.timeSpentMinutes = timeSpentMinutes; }
}
