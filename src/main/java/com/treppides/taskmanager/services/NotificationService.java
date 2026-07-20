package com.treppides.taskmanager.services;

import com.treppides.taskmanager.entities.Employee;
import com.treppides.taskmanager.entities.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final JavaMailSender mailSender;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    public NotificationService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendTaskAssignedEmail(Employee employee, Task task) {
        SimpleMailMessage message =
                new SimpleMailMessage();

        message.setTo(employee.getEmail());
        String safeTitle = task.getTitle().replaceAll("[\\r\\n]", " ");
        message.setSubject(
                "Task Manager - New Task Assigned: " + safeTitle
        );

        message.setText(
                """
                Hello %s,

                You have been assigned a new task.

                Title:
                %s

                Description:
                %s

                Priority:
                %s

                Due Date:
                %s

                Please log into the Task Manager system for more details.
                """
                .formatted(
                        employee.getFullName(),
                        task.getTitle(),
                        task.getDescription(),
                        task.getPriority(),
                        task.getDueDate()
                )
        );

        log.info("Sending notification to: {}", employee.getEmail());
        try {
                message.setFrom("notifications@treppides.com");
                mailSender.send(message);
                log.info("Notification email sent to: {}", employee.getEmail());
        } catch (Exception e) {
                log.error("Notification email failed for: {}", employee.getEmail(), e);
        }
    }

   public void sendTaskDueReminderEmail(Employee employee, Task task, String reminderType) {
        SimpleMailMessage message = new SimpleMailMessage();

        message.setTo(employee.getEmail());
        message.setFrom("notifications@treppides.com");

       String safeTitle = task.getTitle().replaceAll("[\\r\\n]", " ");
       message.setSubject(
                "Task Manager - Upcoming Task Deadline - "
                        + safeTitle );

        message.setText(
                """
                Dear %s,

                This is a reminder that a task assigned to you is approaching its deadline.

                Task Details
                ----------------------------
                Title: %s
                Client: %s
                Priority: %s
                Due Date: %s

                Please review the task and ensure any required actions are completed before the deadline.

                Task Manager
                K. Treppides & Co Ltd
                """
                .formatted(
                        employee.getFullName(),
                        task.getTitle(),
                        task.getClient() != null ? task.getClient() : "-",
                        task.getPriority(),
                        task.getDueDate()
                )
        );

        log.info("Sending reminder to: {}", employee.getEmail());

        try {
                mailSender.send(message);
                log.info("Reminder email sent to: {}", employee.getEmail());
        } catch (Exception e) {
                log.error("Reminder email failed for: {}", employee.getEmail(), e);
        }
        }

    /**
     * End-of-day prompt asking the user to log the time they spent today on their
     * in-progress tasks. Links to the log-time page which reuses the normal login.
     */
    public void sendDailyTimeLogReminderEmail(Employee employee, List<Task> inProgressTasks) {
        SimpleMailMessage message = new SimpleMailMessage();

        message.setTo(employee.getEmail());
        message.setFrom("notifications@treppides.com");
        message.setSubject("Task Manager - Log your hours for today");

        StringBuilder taskLines = new StringBuilder();
        for (Task task : inProgressTasks) {
            String safeTitle = task.getTitle().replaceAll("[\\r\\n]", " ");
            taskLines.append("  - ").append(safeTitle).append("\n");
        }

        String logUrl = baseUrl + "/log-time.html";

        message.setText(
                """
                Dear %s,

                Before you finish for the day, please log the time you spent today on
                your in-progress tasks:

                %s
                Log your hours here:
                %s

                Task Manager
                K. Treppides & Co Ltd
                """
                .formatted(
                        employee.getFullName(),
                        taskLines.toString(),
                        logUrl
                )
        );

        try {
            mailSender.send(message);
            log.info("Daily time-log reminder sent to: {}", employee.getEmail());
        } catch (Exception e) {
            log.error("Daily time-log reminder failed for: {}", employee.getEmail(), e);
        }
    }

    /**
     * Start-of-day digest: a quick nudge with how many of the user's tasks are due
     * within the next 48 hours and a direct link to their My Tasks page.
     */
    public void sendDailyTaskDigestEmail(Employee employee, int dueSoonCount, int totalActive) {
        SimpleMailMessage message = new SimpleMailMessage();

        message.setTo(employee.getEmail());
        message.setFrom("notifications@treppides.com");
        message.setSubject("Task Manager - Your tasks for today");

        String dueSoonLine = dueSoonCount == 1
                ? "You have 1 task due within the next 48 hours."
                : "You have " + dueSoonCount + " tasks due within the next 48 hours.";

        String myTasksUrl = baseUrl + "/my-tasks.html";

        message.setText(
                """
                Good morning %s,

                %s
                You have %d active task(s) in total.

                View and manage them in My Tasks:
                %s

                Task Manager
                K. Treppides & Co Ltd
                """
                .formatted(
                        employee.getFullName(),
                        dueSoonLine,
                        totalActive,
                        myTasksUrl
                )
        );

        try {
            mailSender.send(message);
            log.info("Daily task digest sent to: {}", employee.getEmail());
        } catch (Exception e) {
            log.error("Daily task digest failed for: {}", employee.getEmail(), e);
        }
    }
}
