package com.treppides.taskmanager.services;

import com.treppides.taskmanager.entities.Task;
import com.treppides.taskmanager.entities.TaskAssignment;
import com.treppides.taskmanager.repositories.TaskAssignmentRepository;
import com.treppides.taskmanager.repositories.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class TaskReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(TaskReminderScheduler.class);

    private final TaskRepository taskRepository;
    private final TaskAssignmentRepository taskAssignmentRepository;
    private final NotificationService notificationService;

    public TaskReminderScheduler(TaskRepository taskRepository,
                                 TaskAssignmentRepository taskAssignmentRepository,
                                 NotificationService notificationService) {
        this.taskRepository = taskRepository;
        this.taskAssignmentRepository = taskAssignmentRepository;
        this.notificationService = notificationService;
    }

    @Scheduled(cron = "0 0 8 * * *")
    public void checkUpComingDueTasks() {

        log.info("Scheduler running...");

        List<Task> tasks =
                taskRepository.findByDueDateIsNotNullAndStatusNotInAndIsArchivedFalse(
                        List.of("COMPLETED", "CANCELLED")
                );

        LocalDate today =
                LocalDate.now();

        for (Task task : tasks) {

            log.debug("Checking task: {} | Due: {} | Priority: {}",
                    task.getTitle(), task.getDueDate(), task.getPriority());

            String reminderType =
                    getReminderType(task, today);

            log.debug("Reminder type: {}", reminderType);

            if (reminderType != null) {
                notifyAssignees(task, reminderType);
            }
        }
    }

    private String getReminderType(Task task, LocalDate today) {

        if (task.getDueDate() == null) {
            return null;
        }

        LocalDate dueDate =
                task.getDueDate();

        String priority =
                task.getPriority();

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

        LocalDate result =
                date;

        int remaining =
                workingDays;

        while (remaining > 0) {

            result =
                    result.minusDays(1);

            if (result.getDayOfWeek().getValue() < 6) {
                remaining--;
            }
        }

        return result;
    }

    private void notifyAssignees(Task task, String reminderType) {

        List<TaskAssignment> assignments =
                taskAssignmentRepository.findByTask_TaskId(
                        task.getTaskId()
                );

        for (TaskAssignment assignment : assignments) {

            notificationService.sendTaskDueReminderEmail(
                    assignment.getAssignedTo(),
                    task,
                    reminderType
            );

            log.info("Reminder email sent: {} | Task: {} | To: {}",
                    reminderType, task.getTitle(), assignment.getAssignedTo().getEmail());
        }
    }
}
