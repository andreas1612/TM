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
                "New Task Assigned: " + task.getTitle()
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

        try{
                mailSender.send(message);
        } catch (Exception e) {
                System.out.println("Failed to send email to " + employee.getEmail() + ": " + e.getMessage());
        }
    }
}