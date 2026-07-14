package com.treppides.taskmanager.controllers;

import com.treppides.taskmanager.auth.AdminService;
import com.treppides.taskmanager.dto.AddCommentRequest;
import com.treppides.taskmanager.dto.CompletionEstimateResponse;
import com.treppides.taskmanager.dto.CreateTaskRequest;
import com.treppides.taskmanager.dto.TaskResponse;
import com.treppides.taskmanager.dto.TeamTaskGroupResponse;
import com.treppides.taskmanager.dto.UpdateStatusRequest;
import com.treppides.taskmanager.dto.UpdateTaskRequest;
import com.treppides.taskmanager.dto.CreateChecklistItemRequest;
import com.treppides.taskmanager.dto.CreateTaskDependencyRequest;
import com.treppides.taskmanager.dto.TaskDependencyResponse;
import com.treppides.taskmanager.entities.Task;
import com.treppides.taskmanager.entities.TaskChecklistItem;
import com.treppides.taskmanager.entities.TaskComment;
import com.treppides.taskmanager.entities.TaskHistory;
import com.treppides.taskmanager.services.TaskService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService taskService;
    private final AdminService adminService;

    public TaskController(TaskService taskService, AdminService adminService) {
        this.taskService = taskService;
        this.adminService = adminService;
    }

    @PostMapping
    public Task createTask(Authentication auth,
                           @Valid @RequestBody CreateTaskRequest request) {
        String currentUser = resolveEmail(auth);
        // Override client-supplied createdBy with session identity (C2)
        request.setCreatedBy(currentUser);
        return taskService.createTask(request);
    }

    @GetMapping("/employee/{email}")
    public List<Task> getTasksForEmployee(Authentication auth,
                                          @PathVariable String email) {
        requireSelfOrAdmin(auth, email);
        return taskService.getTasksForEmployee(email);
    }

    @GetMapping("/team/{email}")
    public List<TaskResponse> getTeamTasks(Authentication auth,
                                           @PathVariable String email) {
        requireSelfOrAdmin(auth, email);
        return taskService.convertToTaskResponses(
                taskService.getTeamTasks(email)
        );
    }

    @GetMapping("/team/{email}/groups")
    public List<TeamTaskGroupResponse> getTeamTaskGroups(Authentication auth,
                                                         @PathVariable String email) {
        requireSelfOrAdmin(auth, email);
        return taskService.getTeamTaskGroups(email);
    }

    @GetMapping("/{taskId}")
    public TaskResponse getTaskById(Authentication auth,
                                    @PathVariable Integer taskId) {
        Task task = taskService.getTaskById(taskId);
        requireTaskAccess(auth, task);
        return new TaskResponse(
                task,
                taskService.getAssignmentsForTask(task.getTaskId())
        );
    }

    @PutMapping("/{taskId}")
    public Task updateTask(Authentication auth,
                           @PathVariable Integer taskId,
                           @Valid @RequestBody UpdateTaskRequest request) {
        String currentUser = resolveEmail(auth);
        Task task = taskService.getTaskById(taskId);
        requireTaskAccess(auth, task);
        // Derive changedBy from session (C2)
        return taskService.updateTask(taskId, request, currentUser);
    }

    @PutMapping("/{taskId}/status")
    public Task updateStatus(Authentication auth,
                             @PathVariable Integer taskId,
                             @Valid @RequestBody UpdateStatusRequest request) {
        String currentUser = resolveEmail(auth);
        Task task = taskService.getTaskById(taskId);
        requireTaskAccess(auth, task);
        // Derive changedBy from session (C2)
        return taskService.updateTaskStatus(taskId, request.getStatus(), currentUser, request.getTimeSpentMinutes());
    }

    @GetMapping("/{taskId}/completion-estimate")
    public CompletionEstimateResponse getCompletionEstimate(Authentication auth,
                                                            @PathVariable Integer taskId) {
        Task task = taskService.getTaskById(taskId);
        requireTaskAccess(auth, task);
        return new CompletionEstimateResponse(
                taskService.getCompletionEstimateMinutes(taskId)
        );
    }

    @GetMapping("/{taskId}/comments")
    public List<TaskComment> getTaskComments(Authentication auth,
                                             @PathVariable Integer taskId) {
        Task task = taskService.getTaskById(taskId);
        requireTaskAccess(auth, task);
        return taskService.getTaskComments(taskId);
    }

    @PostMapping("/{taskId}/comments")
    public void addComment(Authentication auth,
                           @PathVariable Integer taskId,
                           @Valid @RequestBody AddCommentRequest request) {
        String currentUser = resolveEmail(auth);
        Task task = taskService.getTaskById(taskId);
        requireTaskAccess(auth, task);
        // Derive createdBy from session (C2)
        taskService.addComment(taskId, request.getCommentText(), currentUser);
    }

    @GetMapping("/{taskId}/history")
    public List<TaskHistory> getTaskHistory(Authentication auth,
                                            @PathVariable Integer taskId) {
        Task task = taskService.getTaskById(taskId);
        requireTaskAccess(auth, task);
        return taskService.getTaskHistory(taskId);
    }

    @GetMapping("/{taskId}/checklist")
    public List<TaskChecklistItem> getChecklistItems(Authentication auth,
                                                     @PathVariable Integer taskId) {
        Task task = taskService.getTaskById(taskId);
        requireTaskAccess(auth, task);
        return taskService.getChecklistItems(taskId);
    }

    @PostMapping("/{taskId}/checklist")
    public TaskChecklistItem addChecklistItem(Authentication auth,
                                              @PathVariable Integer taskId,
                                              @Valid @RequestBody CreateChecklistItemRequest request) {
        String currentUser = resolveEmail(auth);
        Task task = taskService.getTaskById(taskId);
        requireTaskAccess(auth, task);
        // Derive changedBy from session (C2)
        return taskService.addChecklistItem(taskId, request, currentUser);
    }

    @PutMapping("/checklist/{checklistItemId}/toggle")
    public TaskChecklistItem toggleChecklistItem(Authentication auth,
                                                 @PathVariable Integer checklistItemId) {
        String currentUser = resolveEmail(auth);
        // Access checked via the checklist item's parent task
        return taskService.toggleChecklistItem(checklistItemId, currentUser);
    }

    @DeleteMapping("/checklist/{checklistItemId}")
    public void deleteChecklistItem(Authentication auth,
                                    @PathVariable Integer checklistItemId) {
        String currentUser = resolveEmail(auth);
        // Derive changedBy from session (C2)
        taskService.deleteChecklistItem(checklistItemId, currentUser);
    }

    @PostMapping("/{taskId}/dependencies")
    public TaskDependencyResponse addTaskDependency(Authentication auth,
                                                     @PathVariable Integer taskId,
                                                     @Valid @RequestBody CreateTaskDependencyRequest request) {
        Task task = taskService.getTaskById(taskId);
        requireTaskAccess(auth, task);
        return new TaskDependencyResponse(
                taskService.addTaskDependency(taskId, request),
                List.of()
        );
    }

    @PutMapping("/{taskId}/archive")
    public Task archiveTask(Authentication auth,
                            @PathVariable Integer taskId) {
        String currentUser = resolveEmail(auth);
        Task task = taskService.getTaskById(taskId);
        requireTaskAccess(auth, task);
        return taskService.archiveTask(taskId, currentUser);
    }

    @GetMapping("/{taskId}/dependencies")
    public List<TaskDependencyResponse> getTaskDependencies(Authentication auth,
                                                             @PathVariable Integer taskId) {
        Task task = taskService.getTaskById(taskId);
        requireTaskAccess(auth, task);
        return taskService.getTaskDependencies(taskId);
    }

    @GetMapping("/{taskId}/dependency-candidates")
    public List<TaskResponse> getDependencyCandidates(Authentication auth,
                                                      @PathVariable Integer taskId) {
        Task task = taskService.getTaskById(taskId);
        requireTaskAccess(auth, task);
        return taskService.getDependencyCandidates(taskId);
    }

    @DeleteMapping("/dependencies/{dependencyId}")
    public void deleteTaskDependency(Authentication auth,
                                     @PathVariable Integer dependencyId) {
        resolveEmail(auth); // ensure authenticated
        taskService.deleteTaskDependency(dependencyId);
    }

    // --- Authorization helpers ---

    private String resolveEmail(Authentication auth) {
        if (auth == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        if (auth.getPrincipal() instanceof OidcUser oidc) {
            return oidc.getPreferredUsername().toLowerCase();
        }
        return auth.getName().toLowerCase();
    }

    private void requireSelfOrAdmin(Authentication auth, String email) {
        String currentUser = resolveEmail(auth);
        if (!currentUser.equalsIgnoreCase(email) && !adminService.isAdmin(currentUser)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }

    private void requireTaskAccess(Authentication auth, Task task) {
        String currentUser = resolveEmail(auth);
        if (adminService.isAdmin(currentUser)) {
            return;
        }
        // Creator
        if (task.getCreatedBy() != null
                && currentUser.equalsIgnoreCase(task.getCreatedBy().getEmail())) {
            return;
        }
        // Assigned
        List<String> assigned = taskService.getTaskAssignmentEmails(task.getTaskId());
        for (String email : assigned) {
            if (currentUser.equalsIgnoreCase(email)) {
                return;
            }
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }
}
