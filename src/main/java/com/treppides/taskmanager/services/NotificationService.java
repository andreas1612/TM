package com.treppides.taskmanager.services;

import com.treppides.taskmanager.entities.Employee;
import com.treppides.taskmanager.entities.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.InternetAddress;
import java.util.List;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final String LOGIN_URL = "https://tasks.treppides.com/login.html";

    private final JavaMailSender mailSender;

    @Value("${app.support.tech.email:TECHNICAL_TEAM@treppides.com}")
    private String techSupportEmail;

    @Value("${app.support.it.email:lpampaka@treppides.com}")
    private String itSupportEmail;

    public NotificationService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /**
     * Sends a tech support ticket email.
     * Sent via notifications@treppides.com, with the user's email as reply-to.
     */
    /**
     * Sends a support ticket email.
     * @param type "tech" routes to techSupportEmail, "it" routes to itSupportEmail.
     */
    public void sendSupportTicketEmail(String userEmail, String userName, String category, String messageBody, String type) {
        String targetEmail = "it".equals(type) ? itSupportEmail : techSupportEmail;
        String subjectPrefix = "it".equals(type) ? "[Hub IT Support]" : "[Hub Tech Support]";

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, false, "UTF-8");

            helper.setFrom("notifications@treppides.com", userName + " via Hub");
            helper.setReplyTo(userEmail);
            helper.setTo(targetEmail);
            helper.setSubject(subjectPrefix + " " + category.replaceAll("[\\r\\n]", " ") + " — " + userName);
            helper.setText(
                "From: " + userName + " (" + userEmail + ")\n" +
                "Category: " + category + "\n\n" +
                messageBody
            );

            mailSender.send(mimeMessage);
            log.info("Support ticket ({}) sent from {} to {}", type, userEmail, targetEmail);
        } catch (Exception e) {
            log.error("Support ticket email failed from {}: {}", userEmail, e.getMessage(), e);
            throw new RuntimeException("Failed to send support email", e);
        }
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

                Title: %s
                Description: %s
                Priority: %s
                Due Date: %s

                Log in to view and manage this task:
                %s

                Task Manager
                K. Treppides & Co Ltd
                """
                .formatted(
                        employee.getFullName(),
                        task.getTitle(),
                        task.getDescription() != null ? task.getDescription() : "-",
                        task.getPriority(),
                        task.getDueDate(),
                        LOGIN_URL
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

                Title: %s
                Client: %s
                Priority: %s
                Due Date: %s

                Please log in and ensure any required actions are completed before the deadline:
                %s

                Task Manager
                K. Treppides & Co Ltd
                """
                .formatted(
                        employee.getFullName(),
                        task.getTitle(),
                        task.getClient() != null ? task.getClient() : "-",
                        task.getPriority(),
                        task.getDueDate(),
                        LOGIN_URL
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

        message.setText(
                """
                Dear %s,

                Before you finish for the day, please log the time you spent today on
                your in-progress tasks:

                %s
                Log in to record your hours:
                %s

                Task Manager
                K. Treppides & Co Ltd
                """
                .formatted(
                        employee.getFullName(),
                        taskLines.toString(),
                        LOGIN_URL
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
     * Monthly chargeability reminder for employees below the threshold.
     * CC's HR if a CC address is provided.
     */
    public void sendChargeabilityReminderEmail(String employeeEmail, String employeeName,
                                                String monthLabel, double pct,
                                                double actualHrs, double targetHrs,
                                                String weeklyBreakdown, String ccEmail) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, false, "UTF-8");

            helper.setFrom("notifications@treppides.com");
            helper.setTo(employeeEmail);
            if (ccEmail != null && !ccEmail.isBlank()) {
                helper.setCc(ccEmail);
            }
            helper.setSubject("Chargeability Review — " + monthLabel);

            String body = """
                Dear %s,

                Your chargeability for %s was %.1f%% (%.1fh chargeable out of %.1fh available).

                This is below the expected threshold. Please review your timesheets in eSoft
                and ensure all chargeable hours have been recorded correctly.

                Weekly breakdown:
                %s
                If you believe this is correct, no action is needed.

                Kind regards,
                Task Manager
                K. Treppides & Co Ltd
                """.formatted(
                    employeeName, monthLabel, pct, actualHrs, targetHrs,
                    weeklyBreakdown
                );

            helper.setText(body);
            mailSender.send(mimeMessage);
            log.info("Chargeability reminder sent to: {} (CC: {})", employeeEmail, ccEmail);
        } catch (Exception e) {
            log.error("Chargeability reminder email failed for: {}", employeeEmail, e);
        }
    }

    /**
     * Start-of-day digest: a quick nudge with how many of the user's tasks are due
     * within the next 48 hours and a direct link to their My Tasks page.
     */
    public void sendDailyTaskDigestEmail(Employee employee, int dueSoonCount, int overdueCount, int totalActive) {
        SimpleMailMessage message = new SimpleMailMessage();

        message.setTo(employee.getEmail());
        message.setFrom("notifications@treppides.com");
        message.setSubject("Task Manager - Your tasks for today");

        String overdueLine = overdueCount == 0
                ? ""
                : (overdueCount == 1
                        ? "You have 1 overdue task.\n"
                        : "You have " + overdueCount + " overdue tasks.\n");

        String dueSoonLine = dueSoonCount == 0
                ? ""
                : (dueSoonCount == 1
                        ? "You have 1 task due within the next 48 hours.\n"
                        : "You have " + dueSoonCount + " tasks due within the next 48 hours.\n");

        message.setText(
                """
                Good morning %s,

                %s%sYou have %d active task(s) in total.

                Log in to view and manage your tasks:
                %s

                Task Manager
                K. Treppides & Co Ltd
                """
                .formatted(
                        employee.getFullName(),
                        overdueLine,
                        dueSoonLine,
                        totalActive,
                        LOGIN_URL
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
