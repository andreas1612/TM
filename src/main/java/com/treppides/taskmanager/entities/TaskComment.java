package com.treppides.taskmanager.entities;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "TaskComments")
public class TaskComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "CommentId")
    private Integer commentId;

    @ManyToOne
    @JoinColumn(name = "TaskId", nullable = false)
    private Task task;

    @ManyToOne
    @JoinColumn(name = "CreatedBy", referencedColumnName = "Email", nullable = false)
    private Employee createdBy;

    @Column(name = "CommentText", nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String commentText;

    @Column(name = "CreatedAt", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Integer getCommentId() { return commentId; }
    public void setCommentId(Integer commentId) { this.commentId = commentId; }

    public Task getTask() { return task; }
    public void setTask(Task task) { this.task = task; }

    public Employee getCreatedBy() { return createdBy; }
    public void setCreatedBy(Employee createdBy) { this.createdBy = createdBy; }

    public String getCommentText() { return commentText; }
    public void setCommentText(String commentText) { this.commentText = commentText; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}