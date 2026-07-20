package com.treppides.taskmanager.services;

import com.treppides.taskmanager.entities.Task;
import com.treppides.taskmanager.entities.TaskAssignment;
import com.treppides.taskmanager.repositories.TaskAssignmentRepository;
import com.treppides.taskmanager.repositories.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.treppides.taskmanager.entities.Employee;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    /**
     * At 8am on weekdays, send each user a start-of-day digest: how many of their
     * tasks are due within the next 48 hours, with a link to their My Tasks page.
     */
    @Scheduled(cron = "0 0 8 * * MON-FRI")
    public void sendDailyTaskDigest() {

        log.info("Daily task digest scheduler running...");

        List<Task> activeTasks =
                taskRepository.findByStatusNotInAndIsArchivedFalse(
                        List.of("COMPLETED", "CANCELLED", "DONE")
                );

        LocalDate today = LocalDate.now();
        LocalDate horizon = today.plusDays(2);

        Map<String, Employee> employeesByEmail = new LinkedHashMap<>();
        Map<String, Integer> dueSoonByEmail = new LinkedHashMap<>();
        Map<String, Integer> totalByEmail = new LinkedHashMap<>();

        for (Task task : activeTasks) {
            boolean dueSoon = task.getDueDate() != null
                    && !task.getDueDate().isBefore(today)
                    && !task.getDueDate().isAfter(horizon);

            List<TaskAssignment> assignments =
                    taskAssignmentRepository.findByTask_TaskId(task.getTaskId());

            for (TaskAssignment assignment : assignments) {
                Employee employee = assignment.getAssignedTo();
                if (employee == null) {
                    continue;
                }

                String email = employee.getEmail();
                employeesByEmail.putIfAbsent(email, employee);
                totalByEmail.merge(email, 1, Integer::sum);
                if (dueSoon) {
                    dueSoonByEmail.merge(email, 1, Integer::sum);
                }
            }
        }

        for (String email : employeesByEmail.keySet()) {
            notificationService.sendDailyTaskDigestEmail(
                    employeesByEmail.get(email),
                    dueSoonByEmail.getOrDefault(email, 0),
                    totalByEmail.getOrDefault(email, 0)
            );
            log.info("Daily task digest queued for: {} ({} due within 48h)",
                    email, dueSoonByEmail.getOrDefault(email, 0));
        }
    }

    /**
     * At 4pm on weekdays, prompt each user with in-progress tasks to log the time
     * they spent today. One email per user, listing their in-progress tasks.
     */
    @Scheduled(cron = "0 0 16 * * MON-FRI")
    public void sendDailyTimeLogReminders() {

        log.info("Daily time-log reminder scheduler running...");

        List<Task> inProgressTasks =
                taskRepository.findByStatusAndIsArchivedFalse("IN_PROGRESS");

        // Group in-progress tasks by the employee they are assigned to.
        Map<String, Employee> employeesByEmail = new LinkedHashMap<>();
        Map<String, List<Task>> tasksByEmail = new LinkedHashMap<>();

        for (Task task : inProgressTasks) {
            List<TaskAssignment> assignments =
                    taskAssignmentRepository.findByTask_TaskId(task.getTaskId());

            for (TaskAssignment assignment : assignments) {
                Employee employee = assignment.getAssignedTo();
                if (employee == null) {
                    continue;
                }

                String email = employee.getEmail();
                employeesByEmail.putIfAbsent(email, employee);
                tasksByEmail.computeIfAbsent(email, e -> new ArrayList<>()).add(task);
            }
        }

        for (Map.Entry<String, List<Task>> entry : tasksByEmail.entrySet()) {
            Employee employee = employeesByEmail.get(entry.getKey());
            notificationService.sendDailyTimeLogReminderEmail(employee, entry.getValue());
            log.info("Daily time-log reminder queued for: {} ({} task(s))",
                    entry.getKey(), entry.getValue().size());
        }
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
