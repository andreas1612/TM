package com.treppides.taskmanager.dto;

public class UpdateStatusRequest {

    private String status;
    private String changedBy;

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getChangedBy() { return changedBy; }
    public void setChangedBy(String changedBy) { this.changedBy = changedBy; }
}