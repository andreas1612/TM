package com.treppides.taskmanager.entities;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "TaskHistory")
public class TaskHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "HistoryId")
    private Integer historyId;

    @ManyToOne
    @JoinColumn(name = "TaskId", nullable = false)
    private Task task;

    @ManyToOne
    @JoinColumn(name = "ChangedBy", referencedColumnName = "Email", nullable = false)
    private Employee changedBy;

    @Column(name = "FieldChanged", nullable = false, length = 50)
    private String fieldChanged;

    @Column(name = "OldValue", length = 255)
    private String oldValue;

    @Column(name = "NewValue", nullable = false, length = 255)
    private String newValue;

    @Column(name = "ChangedAt", nullable = false)
    private LocalDateTime changedAt;

    @PrePersist
    public void prePersist() {
        if (changedAt == null) {
            changedAt = LocalDateTime.now();
        }
    }

    public Integer getHistoryId() { return historyId; }
    public void setHistoryId(Integer historyId) { this.historyId = historyId; }

    public Task getTask() { return task; }
    public void setTask(Task task) { this.task = task; }

    public Employee getChangedBy() { return changedBy; }
    public void setChangedBy(Employee changedBy) { this.changedBy = changedBy; }

    public String getFieldChanged() { return fieldChanged; }
    public void setFieldChanged(String fieldChanged) { this.fieldChanged = fieldChanged; }

    public String getOldValue() { return oldValue; }
    public void setOldValue(String oldValue) { this.oldValue = oldValue; }

    public String getNewValue() { return newValue; }
    public void setNewValue(String newValue) { this.newValue = newValue; }

    public LocalDateTime getChangedAt() { return changedAt; }
    public void setChangedAt(LocalDateTime changedAt) { this.changedAt = changedAt; }
}