package com.treppides.taskmanager.services;

import com.treppides.taskmanager.entities.Task;
import com.treppides.taskmanager.entities.TaskAssignment;
import com.treppides.taskmanager.repositories.TaskAssignmentRepository;
import com.treppides.taskmanager.repositories.TaskRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class TaskReminderScheduler {

    private final TaskRepository taskRepository;
    private final TaskAssignmentRepository taskassignmentRepositroy;
    
    public TaskReminderScheduler(TaskRepository taskRepository, TaskAssignmentRepository taskassignmentRepositroy) {
        this.taskRepository = taskRepository;
        this.taskassignmentRepositroy = taskassignmentRepositroy;
    
    }
}