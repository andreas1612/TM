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
    private final TaskAssignmentRepository taskAssignmentRepository;  

    public TaskReminderScheduler(TaskRepository taskRepository, TaskAssignmentRepository taskassignmentRepositroy) {
        this.taskRepository = taskRepository;
        this.taskAssignmentRepository = taskassignmentRepositroy;
    
    }

    @Scheduled(cron = "0 0 8 * * *") 
    public void checkUpComingDueTasks() {

        System.out.println("Scheduler running...");

        List<Task> tasks =
                taskRepository.findByDueDateIsNotNullAndStatusNotIn(
                        List.of("COMPLETED", "CANCELLED")
                );

        LocalDate today =
                LocalDate.now();

        for (Task task : tasks) {

            System.out.println(
                    "Checking task: "
                            + task.getTitle()
                            + " | Due: "
                            + task.getDueDate()
                            + " | Priority: "
                            + task.getPriority()
            );

            String reminderType =
                    getReminderType(task, today);

            System.out.println(
                    "Reminder type: "
                            + reminderType
            );

            if (reminderType != null) {
                notifyAssignees(task, reminderType);
            }
        }
    }

        private String getReminderType(Task task, LocalDate today) {

        if (task.getDueDate() == null) {
            return null;
        }

        LocalDate dueDate = task.getDueDate();
        String priority = task.getPriority();

        if ("HIGH".equals(priority)) {

            if (today.equals(subtractWorkingDays(dueDate, 5))) {
                return "HIGH_5_WORKING_DAYS";
            }

            if (today.equals(dueDate.minusDays(3))) {
                return "HIGH_3_DAYS";
            }

            if (today.equals(dueDate.minusDays(1))) {
                return "HIGH_1_DAY";
            }
        }

        if ("MEDIUM".equals(priority)) {

            if (today.equals(dueDate.minusDays(3))) {
                return "MEDIUM_3_DAYS";
            }

            if (today.equals(dueDate.minusDays(1))) {
                return "MEDIUM_1_DAY";
            }
        }

        if ("LOW".equals(priority)) {

            if (today.equals(dueDate.minusDays(1))) {
                return "LOW_1_DAY";
            }
        }

        return null;
    }

        private LocalDate subtractWorkingDays(LocalDate date, int workingDays) {

        LocalDate result = date;
        int remaining = workingDays;

        while (remaining > 0) {

            result = result.minusDays(1);

            if (result.getDayOfWeek().getValue() < 6) {
                remaining--;
            }
        }

        return result;
    }

        private void notifyAssignees(Task task, String reminderType) {

        List<TaskAssignment> assignments =
                taskAssignmentRepository.findByTask_TaskId(task.getTaskId());

        for (TaskAssignment assignment : assignments) {

            System.out.println(
                    "Reminder would be sent: "
                            + reminderType
                            + " | Task: "
                            + task.getTitle()
                            + " | To: "
                            + assignment.getAssignedTo().getEmail()
            );
        }
    }
}