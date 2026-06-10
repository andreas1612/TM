package com.treppides.taskmanager.services;

import com.treppides.taskmanager.dto.CreateChecklistItemRequest;
import com.treppides.taskmanager.dto.CreateTaskRequest;
import com.treppides.taskmanager.dto.TaskResponse;
import com.treppides.taskmanager.dto.TaskDependencyResponse;
import com.treppides.taskmanager.dto.TeamTaskGroupResponse;
import com.treppides.taskmanager.dto.UpdateTaskRequest;
import com.treppides.taskmanager.dto.CreateTaskDependencyRequest;
import com.treppides.taskmanager.entities.TaskDependency;
import com.treppides.taskmanager.entities.Employee;
import com.treppides.taskmanager.entities.Task;
import com.treppides.taskmanager.entities.TaskAssignment;
import com.treppides.taskmanager.entities.TaskChecklistItem;
import com.treppides.taskmanager.entities.TaskComment;
import com.treppides.taskmanager.entities.TaskHistory;
import com.treppides.taskmanager.repositories.EmployeeRepository;
import com.treppides.taskmanager.repositories.DepartmentRepository;
import com.treppides.taskmanager.repositories.TaskAssignmentRepository;
import com.treppides.taskmanager.repositories.TaskChecklistItemRepository;
import com.treppides.taskmanager.repositories.TaskCommentRepository;
import com.treppides.taskmanager.repositories.TaskHistoryRepository;
import com.treppides.taskmanager.repositories.TaskRepository;
import com.treppides.taskmanager.repositories.TaskDependencyRepository;
import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class TaskService {

    private final TaskRepository taskRepository;
    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final TaskAssignmentRepository taskAssignmentRepository;
    private final TaskHistoryRepository taskHistoryRepository;
    private final TaskCommentRepository taskCommentRepository;
    private final NotificationService notificationService;
    private final TaskChecklistItemRepository taskChecklistItemRepository;
    private final TaskDependencyRepository taskDependencyRepository;

    public TaskService(TaskRepository taskRepository,
                   EmployeeRepository employeeRepository,
                   DepartmentRepository departmentRepository,
                   TaskAssignmentRepository taskAssignmentRepository,
                   TaskHistoryRepository taskHistoryRepository,
                   TaskCommentRepository taskCommentRepository,
                   NotificationService notificationService, TaskChecklistItemRepository taskChecklistItemRepository,
                 TaskDependencyRepository taskDependencyRepository) {
    this.taskRepository = taskRepository;
    this.employeeRepository = employeeRepository;
    this.departmentRepository = departmentRepository;
    this.taskAssignmentRepository = taskAssignmentRepository;
    this.taskHistoryRepository = taskHistoryRepository;
    this.taskCommentRepository = taskCommentRepository;
    this.notificationService = notificationService;
    this.taskChecklistItemRepository = taskChecklistItemRepository;
    this.taskDependencyRepository = taskDependencyRepository;
}

    public List<Task> getTeamTasks(String email) {
        Map<Integer, Task> tasksById = new LinkedHashMap<>();

        for (TeamTaskGroupResponse group : getTeamTaskGroups(email)) {
            for (TaskResponse taskResponse : group.getTasks()) {
                Task task = taskRepository.findById(taskResponse.getTaskId())
                        .orElseThrow(() -> new RuntimeException("Task not found"));

                tasksById.putIfAbsent(task.getTaskId(), task);
            }
        }

        return new ArrayList<>(tasksById.values());
    }

    public List<TeamTaskGroupResponse> getTeamTaskGroups(String email) {
        Employee currentEmployee = employeeRepository.findById(email)
                .orElseThrow(() -> new RuntimeException("Employee not found: " + email));

        Map<String, EmployeeGroup> groups = new LinkedHashMap<>();

        addPrimaryGroup(groups, currentEmployee);
        addSupervisedGroups(groups, currentEmployee);

        return groups.values()
                .stream()
                .map(this::toTeamTaskGroupResponse)
                .toList();
    }

    private void addPrimaryGroup(Map<String, EmployeeGroup> groups, Employee currentEmployee) {
        if (currentEmployee.getTeamId() != null) {
            addGroup(
                    groups,
                    "team-" + currentEmployee.getTeamId(),
                    "Team " + currentEmployee.getTeamId(),
                    "TEAM",
                    currentEmployee.getTeamId(),
                    null,
                    employeeRepository.findByTeamIdAndIsActiveTrue(currentEmployee.getTeamId())
            );

            return;
        }

        addGroup(
                groups,
                "department-" + currentEmployee.getDepartment(),
                getDepartmentGroupName(currentEmployee.getDepartment()),
                "DEPARTMENT",
                null,
                currentEmployee.getDepartment(),
                employeeRepository.findByDepartmentIdAndTeamIdIsNullAndIsActiveTrue(
                        currentEmployee.getDepartment()
                )
        );
    }

    private void addSupervisedGroups(Map<String, EmployeeGroup> groups, Employee currentEmployee) {
        List<Employee> supervisedEmployees =
                employeeRepository.findBySupervisorIdAndIsActiveTrue(currentEmployee.getEmail());

        for (Employee employee : supervisedEmployees) {
            if (belongsToPrimaryGroup(currentEmployee, employee)) {
                continue;
            }

            if (employee.getTeamId() != null) {
                addGroup(
                        groups,
                        "supervised-team-" + employee.getTeamId(),
                        "Supervised Team " + employee.getTeamId(),
                        "SUPERVISED_TEAM",
                        employee.getTeamId(),
                        null,
                        List.of(employee)
                );
            } else {
                addGroup(
                        groups,
                        "supervised-department-" + employee.getDepartment(),
                        "Supervised " + getDepartmentGroupName(employee.getDepartment()),
                        "SUPERVISED_DEPARTMENT",
                        null,
                        employee.getDepartment(),
                        List.of(employee)
                );
            }
        }
    }

    private boolean belongsToPrimaryGroup(Employee currentEmployee, Employee employee) {
        if (currentEmployee.getTeamId() != null) {
            return Objects.equals(currentEmployee.getTeamId(), employee.getTeamId());
        }

        return employee.getTeamId() == null
                && Objects.equals(currentEmployee.getDepartment(), employee.getDepartment());
    }

    private String getDepartmentGroupName(Integer departmentId) {
        String departmentName = departmentRepository.findById(departmentId)
                .map(department -> department.getName())
                .orElse(String.valueOf(departmentId));

        return "Department: " + departmentName;
    }

    private void addGroup(
            Map<String, EmployeeGroup> groups,
            String groupKey,
            String groupName,
            String groupType,
            Integer teamId,
            Integer departmentId,
            List<Employee> employees
    ) {
        EmployeeGroup group = groups.computeIfAbsent(
                groupKey,
                key -> new EmployeeGroup(groupKey, groupName, groupType, teamId, departmentId)
        );

        for (Employee employee : employees) {
            group.employeeEmails.add(employee.getEmail());
        }
    }

    private TeamTaskGroupResponse toTeamTaskGroupResponse(EmployeeGroup group) {
        List<TaskResponse> taskResponses = new ArrayList<>();

        if (!group.employeeEmails.isEmpty()) {
            List<TaskAssignment> assignments =
                    taskAssignmentRepository.findByAssignedTo_EmailIn(
                            new ArrayList<>(group.employeeEmails)
                    );

            Map<Integer, Task> tasksById = new LinkedHashMap<>();

            for (TaskAssignment assignment : assignments) {
                Task task = assignment.getTask();
                tasksById.putIfAbsent(task.getTaskId(), task);
            }

            for (Task task : tasksById.values()) {
                taskResponses.add(new TaskResponse(
                        task,
                        taskAssignmentRepository.findByTask_TaskId(task.getTaskId())
                ));
            }
        }

        return new TeamTaskGroupResponse(
                group.groupKey,
                group.groupName,
                group.groupType,
                group.teamId,
                group.departmentId,
                taskResponses
        );
    }

    private static class EmployeeGroup {
        private final String groupKey;
        private final String groupName;
        private final String groupType;
        private final Integer teamId;
        private final Integer departmentId;
        private final Set<String> employeeEmails = new LinkedHashSet<>();

        private EmployeeGroup(
                String groupKey,
                String groupName,
                String groupType,
                Integer teamId,
                Integer departmentId
        ) {
            this.groupKey = groupKey;
            this.groupName = groupName;
            this.groupType = groupType;
            this.teamId = teamId;
            this.departmentId = departmentId;
        }
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
    public TaskChecklistItem addChecklistItem(
            Integer taskId,
            CreateChecklistItemRequest request,
            String changedByEmail
    ) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found"));

        Employee changedBy = employeeRepository.findById(changedByEmail)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        TaskChecklistItem item = new TaskChecklistItem();

        item.setTask(task);
        item.setItemText(request.getItemText());
        item.setIsCompleted(false);

        List<TaskChecklistItem> existingItems =
                taskChecklistItemRepository
                        .findByTask_TaskIdOrderBySortOrderAscChecklistItemIdAsc(taskId);

        item.setSortOrder(existingItems.size() + 1);

        TaskChecklistItem savedItem =
                taskChecklistItemRepository.save(item);

        addHistory(
                task,
                changedBy,
                "Checklist",
                "Created",
                savedItem.getItemText()
        );

        return savedItem;
    }

    @Transactional
    public TaskChecklistItem toggleChecklistItem(
            Integer checklistItemId,
            String changedByEmail
    ) {
        TaskChecklistItem item =
                taskChecklistItemRepository.findById(checklistItemId)
                        .orElseThrow(() -> new RuntimeException("Checklist item not found"));

        Employee changedBy = employeeRepository.findById(changedByEmail)
                .orElseThrow(() -> new RuntimeException("Employee not found"));
        Boolean oldValue = item.getIsCompleted();
        Boolean newValue = !oldValue;

        item.setIsCompleted(newValue);

        addHistory(
                item.getTask(),
                changedBy,
                "Checklist",
                oldValue ? "Completed" : "Open",
                newValue
                        ? "Completed: " + item.getItemText()
                        : "Open: " + item.getItemText()
        );

        return taskChecklistItemRepository.save(item);
    }

    @Transactional
    public void deleteChecklistItem(
            Integer checklistItemId,
            String changedByEmail
    ) {
        TaskChecklistItem item =
                taskChecklistItemRepository.findById(checklistItemId)
                        .orElseThrow(() -> new RuntimeException("Checklist item not found"));

        Employee changedBy = employeeRepository.findById(changedByEmail)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        addHistory(
                item.getTask(),
                changedBy,
                "Checklist",
                "Deleted",
                item.getItemText()
        );

        taskChecklistItemRepository.deleteById(checklistItemId);
    }

    public List<TaskDependencyResponse> getTaskDependencies(Integer taskId) {
        taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found"));

        return getTaskDependencyResponses(taskId, new LinkedHashSet<>());
    }

    public List<TaskResponse> getDependencyCandidates(Integer taskId) {
        taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found"));

        return taskRepository.findDependencyCandidatesForTask(taskId)
                .stream()
                .filter(task -> !taskDependencyRepository
                        .existsByTask_TaskIdAndDependsOnTask_TaskIdAndDependencyType(
                                taskId,
                                task.getTaskId(),
                                "BLOCKED_BY"
                        ))
                .filter(task -> !wouldCreateCycle(taskId, task.getTaskId()))
                .map(task -> new TaskResponse(
                        task,
                        taskAssignmentRepository.findByTask_TaskId(task.getTaskId())
                ))
                .toList();
    }

    @Transactional
    public TaskDependency addTaskDependency(
            Integer taskId,
            CreateTaskDependencyRequest request
    ) {
        String dependencyType = normalizeDependencyType(request.getDependencyType());

        if (taskId.equals(request.getDependsOnTaskId())) {
            throw new RuntimeException("Task cannot depend on itself");
        }

        boolean alreadyExists =
                taskDependencyRepository
                        .existsByTask_TaskIdAndDependsOnTask_TaskIdAndDependencyType(
                                taskId,
                                request.getDependsOnTaskId(),
                                dependencyType
                        );

        if (alreadyExists) {
            throw new RuntimeException("Dependency already exists");
        }

        if (wouldCreateCycle(taskId, request.getDependsOnTaskId())) {
            throw new RuntimeException("Dependency would create a cycle");
        }

        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found"));

        Task dependsOnTask = taskRepository.findById(request.getDependsOnTaskId())
                .orElseThrow(() -> new RuntimeException("Dependency task not found"));

        if (isInactiveStatus(dependsOnTask.getStatus())) {
            throw new RuntimeException("Dependency task must be active");
        }

        boolean sameTeamScope = taskRepository.findDependencyCandidatesForTask(taskId)
                .stream()
                .anyMatch(candidate -> Objects.equals(
                        candidate.getTaskId(),
                        dependsOnTask.getTaskId()
                ));

        if (!sameTeamScope) {
            throw new RuntimeException("Dependency task must be from the same team");
        }

        TaskDependency dependency = new TaskDependency();
        dependency.setTask(task);
        dependency.setDependsOnTask(dependsOnTask);
        dependency.setDependencyType(dependencyType);

        return taskDependencyRepository.save(dependency);
    }

    private String normalizeDependencyType(String dependencyType) {
        if (dependencyType == null || dependencyType.trim().isEmpty()) {
            return "BLOCKED_BY";
        }

        return dependencyType.trim().toUpperCase();
    }

    private boolean isInactiveStatus(String status) {
        return "COMPLETED".equals(status)
                || "CANCELLED".equals(status)
                || "DONE".equals(status);
    }

    private List<TaskDependencyResponse> getTaskDependencyResponses(
            Integer taskId,
            Set<Integer> visitedTaskIds
    ) {
        if (!visitedTaskIds.add(taskId)) {
            return List.of();
        }

        return taskDependencyRepository.findByTask_TaskId(taskId)
                .stream()
                .map(dependency -> new TaskDependencyResponse(
                        dependency,
                        getTaskDependencyResponses(
                                dependency.getDependsOnTask().getTaskId(),
                                new LinkedHashSet<>(visitedTaskIds)
                        )
                ))
                .toList();
    }

    private boolean wouldCreateCycle(Integer taskId, Integer dependsOnTaskId) {
        return hasDependencyPath(dependsOnTaskId, taskId, new LinkedHashSet<>());
    }

    private boolean hasDependencyPath(
            Integer currentTaskId,
            Integer targetTaskId,
            Set<Integer> visitedTaskIds
    ) {
        if (Objects.equals(currentTaskId, targetTaskId)) {
            return true;
        }

        if (!visitedTaskIds.add(currentTaskId)) {
            return false;
        }

        return taskDependencyRepository.findByTask_TaskId(currentTaskId)
                .stream()
                .anyMatch(dependency -> hasDependencyPath(
                        dependency.getDependsOnTask().getTaskId(),
                        targetTaskId,
                        visitedTaskIds
                ));
    }

    @Transactional
    public void deleteTaskDependency(Integer dependencyId) {
        taskDependencyRepository.deleteById(dependencyId);
    }
}
