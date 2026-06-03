package com.treppides.taskmanager.entities;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "TaskAssignments",
    uniqueConstraints = @UniqueConstraint(columnNames = {"TaskId", "AssignedTo"})
)
public class TaskAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "TaskAssignmentId")
    private Integer taskAssignmentId;

    @ManyToOne
    @JoinColumn(name = "TaskId", nullable = false)
    private Task task;

    @ManyToOne
    @JoinColumn(name = "AssignedTo", referencedColumnName = "Email", nullable = false)
    private Employee assignedTo;

    @Column(name = "AssignedAt", nullable = false)
    private LocalDateTime assignedAt;

    @PrePersist
    public void prePersist() {
        if (assignedAt == null) {
            assignedAt = LocalDateTime.now();
        }
    }

    public Integer getTaskAssignmentId() { return taskAssignmentId; }
    public void setTaskAssignmentId(Integer taskAssignmentId) { this.taskAssignmentId = taskAssignmentId; }

    public Task getTask() { return task; }
    public void setTask(Task task) { this.task = task; }

    public Employee getAssignedTo() { return assignedTo; }
    public void setAssignedTo(Employee assignedTo) { this.assignedTo = assignedTo; }

    public LocalDateTime getAssignedAt() { return assignedAt; }
    public void setAssignedAt(LocalDateTime assignedAt) { this.assignedAt = assignedAt; }
}