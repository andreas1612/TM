package com.treppides.taskmanager.services;

import com.treppides.taskmanager.dto.CreateChecklistItemRequest;
import com.treppides.taskmanager.dto.CreateTaskRequest;
import com.treppides.taskmanager.dto.TaskResponse;
import com.treppides.taskmanager.dto.UpdateTaskRequest;
import com.treppides.taskmanager.entities.Employee;
import com.treppides.taskmanager.entities.Task;
import com.treppides.taskmanager.entities.TaskAssignment;
import com.treppides.taskmanager.entities.TaskChecklistItem;
import com.treppides.taskmanager.entities.TaskComment;
import com.treppides.taskmanager.entities.TaskHistory;
import com.treppides.taskmanager.repositories.EmployeeRepository;
import com.treppides.taskmanager.repositories.TaskAssignmentRepository;
import com.treppides.taskmanager.repositories.TaskChecklistItemRepository;
import com.treppides.taskmanager.repositories.TaskCommentRepository;
import com.treppides.taskmanager.repositories.TaskHistoryRepository;
import com.treppides.taskmanager.repositories.TaskRepository;
import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class TaskService {

    private final TaskRepository taskRepository;
    private final EmployeeRepository employeeRepository;
    private final TaskAssignmentRepository taskAssignmentRepository;
    private final TaskHistoryRepository taskHistoryRepository;
    private final TaskCommentRepository taskCommentRepository;
    private final NotificationService notificationService;
    private final TaskChecklistItemRepository taskChecklistItemRepository;

    public TaskService(TaskRepository taskRepository,
                   EmployeeRepository employeeRepository,
                   TaskAssignmentRepository taskAssignmentRepository,
                   TaskHistoryRepository taskHistoryRepository,
                   TaskCommentRepository taskCommentRepository,
                   NotificationService notificationService, TaskChecklistItemRepository taskChecklistItemRepository) {
    this.taskRepository = taskRepository;
    this.employeeRepository = employeeRepository;
    this.taskAssignmentRepository = taskAssignmentRepository;
    this.taskHistoryRepository = taskHistoryRepository;
    this.taskCommentRepository = taskCommentRepository;
    this.notificationService = notificationService;
    this.taskChecklistItemRepository = taskChecklistItemRepository;
}

    public List<Task> getTeamTasks(String email) {
        return taskRepository.findTasksForTeam(email);
    }

    public Task getTaskById(Integer taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found"));
    }

    @Transactional
    public Task createTask(CreateTaskRequest request) {

        Employee creator = employeeRepository.findById(request.getCreatedBy())
                .orElseThrow(() -> new RuntimeException("Creator not found"));

        Task task = new Task();
        task.setTitle(request.getTitle());
        task.setDescription(request.getDescription());
        task.setClient(request.getClient());
        task.setCreatedBy(creator);
        task.setStatus(request.getStatus() != null ? request.getStatus() : "TO_DO");
        task.setPriority(request.getPriority() != null ? request.getPriority() : "MEDIUM");
        task.setStartDate(request.getStartDate());
        task.setDueDate(request.getDueDate());

        if (request.getParentTaskId() != null) {
            Task parentTask = taskRepository.findById(request.getParentTaskId())
                    .orElseThrow(() -> new RuntimeException("Parent task not found"));
            task.setParentTask(parentTask);
        }

        Task savedTask = taskRepository.save(task);

        if (request.getAssignedTo() != null && !request.getAssignedTo().isEmpty()) {
            for (String email : request.getAssignedTo()) {
                Employee employee = employeeRepository.findById(email.trim())
                        .orElseThrow(() -> new RuntimeException("Employee not found: " + email));

                TaskAssignment assignment = new TaskAssignment();
                assignment.setTask(savedTask);
                assignment.setAssignedTo(employee);

                taskAssignmentRepository.save(assignment);
                notificationService.sendTaskAssignedEmail(employee, task);
            }
        }

        return savedTask;
    }

    @Transactional
    public Task updateTaskStatus(Integer taskId, String newStatus, String changedByEmail) {

        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found"));

        String oldStatus = task.getStatus();

        if (!Objects.equals(oldStatus, newStatus)) {
            Employee changedBy = employeeRepository.findById(changedByEmail)
                    .orElseThrow(() -> new RuntimeException("Employee not found"));

            addHistory(task, changedBy, "Status", oldStatus, newStatus);
            task.setStatus(newStatus);
        }

        return taskRepository.save(task);
    }

    @Transactional
    public void addComment(Integer taskId, String commentText, String createdByEmail) {

        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found"));

        Employee employee = employeeRepository.findById(createdByEmail)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        TaskComment comment = new TaskComment();
        comment.setTask(task);
        comment.setCreatedBy(employee);
        comment.setCommentText(commentText);

        taskCommentRepository.save(comment);
    }

    public List<Task> getTasksForEmployee(String email) {

        List<TaskAssignment> assignments =
                taskAssignmentRepository.findByAssignedTo_Email(email);

        List<Task> tasks = new ArrayList<>();

        for (TaskAssignment assignment : assignments) {
            tasks.add(assignment.getTask());
        }

        return tasks;
    }

    public List<TaskResponse> convertToTaskResponses(List<Task> tasks) {
        List<TaskResponse> responses = new ArrayList<>();

        for (Task task : tasks) {
            List<TaskAssignment> assignments =
                    taskAssignmentRepository.findByTask_TaskId(task.getTaskId());

            responses.add(new TaskResponse(task, assignments));
        }

        return responses;
    }

    @Transactional
    public Task updateTask(Integer taskId, UpdateTaskRequest request, String changedByEmail) {

        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found"));

        Employee changedBy = employeeRepository.findById(changedByEmail)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        if (!Objects.equals(task.getTitle(), request.getTitle())) {
            addHistory(task, changedBy, "Title", task.getTitle(), request.getTitle());
            task.setTitle(request.getTitle());
        }

        if (!Objects.equals(task.getClient(), request.getClient())) {
            addHistory(task, changedBy, "Client", task.getClient(), request.getClient() );
            task.setClient(request.getClient());
        }

        if (!Objects.equals(task.getDescription(), request.getDescription())) {
            addHistory(task, changedBy, "Description", task.getDescription(), request.getDescription());
            task.setDescription(request.getDescription());
        }

        if (!Objects.equals(task.getStatus(), request.getStatus())) {
            addHistory(task, changedBy, "Status", task.getStatus(), request.getStatus());
            task.setStatus(request.getStatus());
        }

        if (!Objects.equals(task.getPriority(), request.getPriority())) {
            addHistory(task, changedBy, "Priority", task.getPriority(), request.getPriority());
            task.setPriority(request.getPriority());
        }

        if (!Objects.equals(task.getDueDate(), request.getDueDate())) {
            String oldDueDate = task.getDueDate() == null
                    ? null
                    : task.getDueDate().toString();

            String newDueDate = request.getDueDate() == null
                    ? null
                    : request.getDueDate().toString();

            addHistory(task, changedBy, "DueDate", oldDueDate, newDueDate);
            task.setDueDate(request.getDueDate());
        }

        if (request.getAssignedTo() != null) {
            updateTaskAssignments(task, request.getAssignedTo(), changedBy);
        }

        return taskRepository.save(task);
    }

    private void updateTaskAssignments(Task task, List<String> newAssignedEmails, Employee changedBy) {

        List<TaskAssignment> oldAssignments =
                taskAssignmentRepository.findByTask_TaskId(task.getTaskId());

        String oldAssignedTo = oldAssignments.stream()
                .map(a -> a.getAssignedTo().getEmail())
                .sorted()
                .reduce((a, b) -> a + ", " + b)
                .orElse("");

        String newAssignedTo = newAssignedEmails.stream()
                .map(String::trim)
                .filter(email -> !email.isEmpty())
                .sorted()
                .reduce((a, b) -> a + ", " + b)
                .orElse("");

        if (Objects.equals(oldAssignedTo, newAssignedTo)) {
            return;
        }

        addHistory(
                task,
                changedBy,
                "AssignedTo",
                oldAssignedTo,
                newAssignedTo
        );

        taskAssignmentRepository.deleteAll(oldAssignments);
        taskAssignmentRepository.flush();

        for (String email : newAssignedEmails) {
            String cleanEmail = email.trim();

            if (cleanEmail.isEmpty()) {
                continue;
            }

            Employee employee = employeeRepository.findById(cleanEmail)
                    .orElseThrow(() -> new RuntimeException("Employee not found: " + cleanEmail));

            TaskAssignment assignment = new TaskAssignment();
            assignment.setTask(task);
            assignment.setAssignedTo(employee);

            taskAssignmentRepository.save(assignment);
            notificationService.sendTaskAssignedEmail(employee, task);
        }
    }

    public List<String> getTaskAssignmentEmails(Integer taskId) {
        return taskAssignmentRepository.findByTask_TaskId(taskId)
                .stream()
                .map(a -> a.getAssignedTo().getEmail())
                .toList();
    }

    private void addHistory(Task task, Employee changedBy, String field, String oldValue, String newValue) {
        TaskHistory history = new TaskHistory();
        history.setTask(task);
        history.setChangedBy(changedBy);
        history.setFieldChanged(field);
        history.setOldValue(oldValue);
        history.setNewValue(newValue);

        taskHistoryRepository.save(history);
    }

    public List<TaskComment> getTaskComments(Integer taskId) {
        return taskCommentRepository.findByTask_TaskIdOrderByCreatedAtAsc(taskId);
    }

    public List<TaskAssignment> getAssignmentsForTask(Integer taskId) {
        return taskAssignmentRepository.findByTask_TaskId(taskId);
    }

    public List<TaskHistory> getTaskHistory(Integer taskId) {
        return taskHistoryRepository
                .findByTask_TaskIdOrderByChangedAtAsc(taskId);
    }

    public List<TaskChecklistItem> getChecklistItems(Integer taskId) {
        return taskChecklistItemRepository
                .findByTask_TaskIdOrderBySortOrderAscChecklistItemIdAsc(taskId);
    }

    @Transactional
    public TaskChecklistItem addChecklistItem(Integer taskId, CreateChecklistItemRequest request) {

        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found"));

        TaskChecklistItem item = new TaskChecklistItem();

        item.setTask(task);
        item.setItemText(request.getItemText());
        item.setIsCompleted(false);

        List<TaskChecklistItem> existingItems =
                taskChecklistItemRepository
                        .findByTask_TaskIdOrderBySortOrderAscChecklistItemIdAsc(taskId);

        item.setSortOrder(existingItems.size() + 1);

        return taskChecklistItemRepository.save(item);
    }

    @Transactional
    public TaskChecklistItem toggleChecklistItem(Integer checklistItemId) {

        TaskChecklistItem item =
                taskChecklistItemRepository.findById(checklistItemId)
                        .orElseThrow(() -> new RuntimeException("Checklist item not found"));

        item.setIsCompleted(!item.getIsCompleted());

        return taskChecklistItemRepository.save(item);
    }

    @Transactional
    public void deleteChecklistItem(Integer checklistItemId) {

        taskChecklistItemRepository.deleteById(checklistItemId);
    }
}