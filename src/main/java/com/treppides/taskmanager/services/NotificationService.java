package com.treppides.taskmanager.services;

import com.treppides.taskmanager.entities.Employee;
import com.treppides.taskmanager.entities.Task;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    private final JavaMailSender mailSender;

    public NotificationService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendTaskAssignedEmail(Employee employee, Task task) {
        SimpleMailMessage message =
                new SimpleMailMessage();

        message.setTo(employee.getEmail());
        message.setSubject(
                "Task Manager - New Task Assigned: " + task.getTitle()
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

        System.out.println("Trying to send notification to: " + employee.getEmail());
        try {
                message.setFrom("notifications@treppides.com");
                mailSender.send(message);
                System.out.println("Notification email sent to: " + employee.getEmail());
        } catch (Exception e) {
                System.out.println("Notification email failed for: " + employee.getEmail());
                e.printStackTrace();
        }
    }
}