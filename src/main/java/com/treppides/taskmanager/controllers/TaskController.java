package com.treppides.taskmanager.controllers;

import com.treppides.taskmanager.dto.AddCommentRequest;
import com.treppides.taskmanager.dto.CreateTaskRequest;
import com.treppides.taskmanager.dto.TaskResponse;
import com.treppides.taskmanager.dto.TeamTaskGroupResponse;
import com.treppides.taskmanager.dto.UpdateStatusRequest;
import com.treppides.taskmanager.dto.UpdateTaskRequest;
import com.treppides.taskmanager.dto.CreateChecklistItemRequest;
import com.treppides.taskmanager.dto.CreateTaskDependencyRequest;
import com.treppides.taskmanager.dto.TaskDependencyResponse;
import com.treppides.taskmanager.entities.TaskDependency;
import com.treppides.taskmanager.entities.TaskChecklistItem;
import com.treppides.taskmanager.entities.Task;
import com.treppides.taskmanager.entities.TaskComment;
import com.treppides.taskmanager.entities.TaskHistory;
import com.treppides.taskmanager.services.TaskService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tasks")
@CrossOrigin
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping
    public Task createTask(@RequestBody CreateTaskRequest request) {
        return taskService.createTask(request);
    }

    @GetMapping("/employee/{email}")
    public List<Task> getTasksForEmployee(@PathVariable String email) {
        return taskService.getTasksForEmployee(email);
    }

    @GetMapping("/team/{email}")
    public List<TaskResponse> getTeamTasks(@PathVariable String email) {
        return taskService.convertToTaskResponses(
                taskService.getTeamTasks(email)
        );
    }

    @GetMapping("/team/{email}/groups")
    public List<TeamTaskGroupResponse> getTeamTaskGroups(@PathVariable String email) {
        return taskService.getTeamTaskGroups(email);
    }

    @GetMapping("/{taskId}")
    public TaskResponse getTaskById(@PathVariable Integer taskId) {

        Task task = taskService.getTaskById(taskId);

        return new TaskResponse(
                task,
                taskService.getAssignmentsForTask(task.getTaskId())
        );
    }

    @PutMapping("/{taskId}")
    public Task updateTask(
            @PathVariable Integer taskId,
            @RequestBody UpdateTaskRequest request,
            @RequestParam String changedBy
    ) {
        return taskService.updateTask(taskId, request, changedBy);
    }

    @PutMapping("/{taskId}/status")
    public Task updateStatus(
            @PathVariable Integer taskId,
            @RequestBody UpdateStatusRequest request
    ) {
        return taskService.updateTaskStatus(
                taskId,
                request.getStatus(),
                request.getChangedBy()
        );
    }

    @GetMapping("/{taskId}/comments")
    public List<TaskComment> getTaskComments(@PathVariable Integer taskId) {
        return taskService.getTaskComments(taskId);
    }

    @PostMapping("/{taskId}/comments")
    public void addComment(
            @PathVariable Integer taskId,
            @RequestBody AddCommentRequest request
    ) {
        taskService.addComment(
                taskId,
                request.getCommentText(),
                request.getCreatedBy()
        );
    }

    @GetMapping("/{taskId}/history")
    public List<TaskHistory> getTaskHistory(@PathVariable Integer taskId) {
        return taskService.getTaskHistory(taskId);
    }

    @GetMapping("/{taskId}/checklist")
    public List<TaskChecklistItem> getChecklistItems(
            @PathVariable Integer taskId
    ) {
        return taskService.getChecklistItems(taskId);
    }

    @PostMapping("/{taskId}/checklist")
    public TaskChecklistItem addChecklistItem(
            @PathVariable Integer taskId,
            @RequestBody CreateChecklistItemRequest request,
            @RequestParam String changedBy
    ) {
        return taskService.addChecklistItem(taskId, request, changedBy);
    }

    @PutMapping("/checklist/{checklistItemId}/toggle")
    public TaskChecklistItem toggleChecklistItem(
            @PathVariable Integer checklistItemId,
            @RequestParam String changedBy
    ) {
        return taskService.toggleChecklistItem(checklistItemId, changedBy);
    }

    @DeleteMapping("/checklist/{checklistItemId}")
    public void deleteChecklistItem(
            @PathVariable Integer checklistItemId,
            @RequestParam String changedBy
    ) {
        taskService.deleteChecklistItem(checklistItemId, changedBy);
    }

    @PostMapping("/{taskId}/dependencies")
    public TaskDependency addTaskDependency(
            @PathVariable Integer taskId,
            @RequestBody CreateTaskDependencyRequest request
    ) {
        return taskService.addTaskDependency(taskId, request);
    }

    @GetMapping("/{taskId}/dependencies")
    public List<TaskDependencyResponse> getTaskDependencies(@PathVariable Integer taskId) {
        return taskService.getTaskDependencies(taskId);
    }

    @GetMapping("/{taskId}/dependency-candidates")
    public List<TaskResponse> getDependencyCandidates(@PathVariable Integer taskId) {
        return taskService.getDependencyCandidates(taskId);
    }

    @DeleteMapping("/dependencies/{dependencyId}")
    public void deleteTaskDependency(@PathVariable Integer dependencyId) {
        taskService.deleteTaskDependency(dependencyId);
    }
}
