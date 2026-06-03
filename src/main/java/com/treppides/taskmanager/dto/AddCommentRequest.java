package com.treppides.taskmanager.dto;

public class AddCommentRequest {

    private String commentText;
    private String createdBy;

    public String getCommentText() { return commentText; }
    public void setCommentText(String commentText) { this.commentText = commentText; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
}