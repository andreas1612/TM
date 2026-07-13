package com.treppides.taskmanager.services;

import com.treppides.taskmanager.dto.EmployeeCompletionStat;
import com.treppides.taskmanager.dto.EmployeeReportDetail;
import com.treppides.taskmanager.dto.EmployeeStatsResponse;
import com.treppides.taskmanager.dto.EmployeeWorkloadStat;
import com.treppides.taskmanager.dto.GroupCompletionStat;
import com.treppides.taskmanager.dto.GroupStatsResponse;
import com.treppides.taskmanager.dto.GroupWorkloadStat;
import com.treppides.taskmanager.dto.TaskDetailRow;
import com.treppides.taskmanager.entities.Employee;
import com.treppides.taskmanager.entities.Task;
import com.treppides.taskmanager.entities.TaskHistory;
import com.treppides.taskmanager.repositories.DepartmentRepository;
import com.treppides.taskmanager.repositories.EmployeeRepository;
import com.treppides.taskmanager.repositories.ReportRepository;
import com.treppides.taskmanager.repositories.TaskHistoryRepository;
import com.treppides.taskmanager.repositories.TeamRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ReportService {

    private final ReportRepository reportRepository;
    private final EmployeeRepository employeeRepository;
    private final TaskHistoryRepository taskHistoryRepository;
    private final TeamRepository teamRepository;
    private final DepartmentRepository departmentRepository;
    private final TaskService taskService;

    public ReportService(ReportRepository reportRepository,
                         EmployeeRepository employeeRepository,
                         TaskHistoryRepository taskHistoryRepository,
                         TeamRepository teamRepository,
                         DepartmentRepository departmentRepository,
                         TaskService taskService) {
        this.reportRepository = reportRepository;
        this.employeeRepository = employeeRepository;
        this.taskHistoryRepository = taskHistoryRepository;
        this.teamRepository = teamRepository;
        this.departmentRepository = departmentRepository;
        this.taskService = taskService;
    }

    /**
     * Combined per-employee stats for everyone under the viewer (their team + direct reports):
     * completedCount within [start, end], plus a current snapshot of assigned/open/overdue.
     * Merges the two source queries by email so an employee shows up if they have EITHER
     * completions in the range OR a live workload. Sorted by completed desc, then open desc.
     */
    public List<EmployeeStatsResponse> getEmployeeStats(String viewer, LocalDate start, LocalDate end) {
        List<Employee> scopeEmployees = resolveScopeEmployees(viewer);
        if (scopeEmployees.isEmpty()) {
            return List.of();
        }
        List<String> scope = scopeEmployees.stream().map(Employee::getEmail).toList();

        Map<String, EmployeeStatsResponse> byEmail = new LinkedHashMap<>();

        // Seed every person under the viewer so those with no tasks still show up (with zeros).
        for (Employee employee : scopeEmployees) {
            EmployeeStatsResponse row = new EmployeeStatsResponse();
            row.setEmail(employee.getEmail());
            row.setFullName(employee.getFullName());
            row.setDepartmentId(employee.getDepartment());
            row.setTeamId(employee.getTeamId());
            byEmail.put(employee.getEmail(), row);
        }

        for (EmployeeWorkloadStat workload : reportRepository.findWorkloadPerEmployee(LocalDate.now(), scope)) {
            EmployeeStatsResponse row = byEmail.computeIfAbsent(
                    workload.getEmail(), email -> new EmployeeStatsResponse());
            row.setEmail(workload.getEmail());
            row.setFullName(workload.getFullName());
            row.setDepartmentId(workload.getDepartmentId());
            row.setTeamId(workload.getTeamId());
            row.setAssignedCount(workload.getAssignedCount());
            row.setOpenCount(workload.getOpenCount());
            row.setOverdueCount(workload.getOverdueCount());
        }

        List<EmployeeCompletionStat> completedStats = reportRepository.findCompletedPerEmployee(
                start.atStartOfDay(), end.plusDays(1).atStartOfDay(), scope);
        for (EmployeeCompletionStat completed : completedStats) {
            EmployeeStatsResponse row = byEmail.computeIfAbsent(
                    completed.getEmail(), email -> new EmployeeStatsResponse());
            row.setEmail(completed.getEmail());
            if (row.getFullName() == null) {
                row.setFullName(completed.getFullName());
                row.setDepartmentId(completed.getDepartmentId());
                row.setTeamId(completed.getTeamId());
            }
            row.setCompletedCount(completed.getCompletedCount());
        }

        return byEmail.values()
                .stream()
                .sorted(Comparator.comparingLong(EmployeeStatsResponse::getCompletedCount).reversed()
                        .thenComparing(Comparator.comparingLong(EmployeeStatsResponse::getOpenCount).reversed()))
                .toList();
    }

    /**
     * Emails of everyone "under" the viewer — mirrors the Team Tasks page: the viewer's team
     * (or their no-team department peers) plus their direct reports. Active employees only.
     */
    private List<Employee> resolveScopeEmployees(String viewerEmail) {
        Employee viewer = employeeRepository.findById(viewerEmail).orElse(null);
        if (viewer == null) {
            return List.of();
        }

        Map<String, Employee> byEmail = new LinkedHashMap<>();

        // The viewer and their own team (or their no-team department peers).
        byEmail.put(viewer.getEmail(), viewer);
        addUnitMembers(viewer, byEmail);

        // Each direct report, PLUS the whole team/department that report belongs to — so a
        // manager who supervises someone on another team can see that entire team/department.
        for (Employee report : employeeRepository.findBySupervisorIdAndIsActiveTrue(viewerEmail)) {
            byEmail.putIfAbsent(report.getEmail(), report);
            addUnitMembers(report, byEmail);
        }

        return new ArrayList<>(byEmail.values());
    }

    private void addUnitMembers(Employee employee, Map<String, Employee> byEmail) {
        List<Employee> members = employee.getTeamId() != null
                ? employeeRepository.findByTeamIdAndIsActiveTrue(employee.getTeamId())
                : employeeRepository.findByDepartmentIdAndTeamIdIsNullAndIsActiveTrue(employee.getDepartment());
        members.forEach(member -> byEmail.putIfAbsent(member.getEmail(), member));
    }

    private List<String> resolveScopeEmails(String viewerEmail) {
        return resolveScopeEmployees(viewerEmail)
                .stream()
                .map(Employee::getEmail)
                .toList();
    }

    /**
     * Combined per-team roll-up. Each task is counted once per team (COUNT DISTINCT),
     * so co-assignment within a team does not inflate the totals.
     */
    public List<GroupStatsResponse> getTeamStats(String viewer, LocalDate start, LocalDate end) {
        List<Employee> scopeEmployees = resolveScopeEmployees(viewer);
        if (scopeEmployees.isEmpty()) {
            return List.of();
        }
        List<String> scope = scopeEmployees.stream().map(Employee::getEmail).toList();

        // Seed every team/department unit the scoped people belong to, so units with no tasks still show.
        Map<String, GroupStatsResponse> byGroup = new LinkedHashMap<>();
        for (Employee employee : scopeEmployees) {
            seedTeamUnit(byGroup, employee);
        }

        overlayGroupStats(byGroup,
                reportRepository.findWorkloadPerTeam(LocalDate.now(), scope),
                reportRepository.findCompletedPerTeam(start.atStartOfDay(), end.plusDays(1).atStartOfDay(), scope));
        return sortedGroups(byGroup);
    }

    /**
     * Combined per-department roll-up. Each task is counted once per department.
     */
    public List<GroupStatsResponse> getDepartmentStats(String viewer, LocalDate start, LocalDate end) {
        List<Employee> scopeEmployees = resolveScopeEmployees(viewer);
        if (scopeEmployees.isEmpty()) {
            return List.of();
        }
        List<String> scope = scopeEmployees.stream().map(Employee::getEmail).toList();

        // Seed every department the scoped people belong to, so departments with no tasks still show.
        Map<String, GroupStatsResponse> byGroup = new LinkedHashMap<>();
        for (Employee employee : scopeEmployees) {
            String key = "dept:" + employee.getDepartment();
            byGroup.computeIfAbsent(key, k ->
                    newGroup(key, departmentName(employee.getDepartment()), "DEPARTMENT"));
        }

        overlayGroupStats(byGroup,
                reportRepository.findWorkloadPerDepartment(LocalDate.now(), scope),
                reportRepository.findCompletedPerDepartment(start.atStartOfDay(), end.plusDays(1).atStartOfDay(), scope));
        return sortedGroups(byGroup);
    }

    private void seedTeamUnit(Map<String, GroupStatsResponse> byGroup, Employee employee) {
        String key;
        String name;
        String type;
        if (employee.getTeamId() != null) {
            key = "team:" + employee.getTeamId();
            name = teamName(employee.getTeamId());
            type = "TEAM";
        } else {
            key = "dept:" + employee.getDepartment();
            name = departmentName(employee.getDepartment()) + " (no team)";
            type = "DEPARTMENT";
        }
        byGroup.computeIfAbsent(key, k -> newGroup(key, name, type));
    }

    private GroupStatsResponse newGroup(String key, String name, String type) {
        GroupStatsResponse row = new GroupStatsResponse();
        row.setGroupKey(key);
        row.setGroupName(name);
        row.setGroupType(type);
        return row;
    }

    private String teamName(Integer teamId) {
        return teamRepository.findById(teamId).map(team -> team.getName()).orElse("Team " + teamId);
    }

    private String departmentName(Integer departmentId) {
        return departmentRepository.findById(departmentId)
                .map(department -> department.getName())
                .orElse("Department " + departmentId);
    }

    private List<GroupStatsResponse> sortedGroups(Map<String, GroupStatsResponse> byGroup) {
        return byGroup.values()
                .stream()
                .sorted(Comparator.comparingLong(GroupStatsResponse::getCompletedCount).reversed()
                        .thenComparing(Comparator.comparingLong(GroupStatsResponse::getOpenCount).reversed()))
                .toList();
    }

    private void overlayGroupStats(
            Map<String, GroupStatsResponse> byGroup,
            List<GroupWorkloadStat> workload,
            List<GroupCompletionStat> completed
    ) {
        for (GroupWorkloadStat stat : workload) {
            GroupStatsResponse row = byGroup.computeIfAbsent(
                    stat.getGroupKey(), key -> new GroupStatsResponse());
            row.setGroupKey(stat.getGroupKey());
            row.setGroupName(stat.getGroupName());
            row.setGroupType(stat.getGroupType());
            row.setAssignedCount(stat.getAssignedCount());
            row.setOpenCount(stat.getOpenCount());
            row.setOverdueCount(stat.getOverdueCount());
        }

        for (GroupCompletionStat stat : completed) {
            GroupStatsResponse row = byGroup.computeIfAbsent(
                    stat.getGroupKey(), key -> new GroupStatsResponse());
            row.setGroupKey(stat.getGroupKey());
            if (row.getGroupName() == null) {
                row.setGroupName(stat.getGroupName());
                row.setGroupType(stat.getGroupType());
            }
            row.setCompletedCount(stat.getCompletedCount());
        }
    }

    /**
     * Task-level detail for one employee: every non-archived task assigned to them that is
     * either currently open OR was completed within [start, end]. Each row carries how long a
     * completed task took, how long an open task has been open, and whether it is overdue.
     */
    public EmployeeReportDetail getEmployeeReportDetail(String email, LocalDate start, LocalDate end) {
        Employee employee = employeeRepository.findById(email)
                .orElseThrow(() -> new RuntimeException("Employee not found: " + email));

        LocalDateTime startDateTime = start.atStartOfDay();
        LocalDateTime endDateTime = end.plusDays(1).atStartOfDay();
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();

        List<TaskDetailRow> rows = new ArrayList<>();
        for (Task task : taskService.getTasksForEmployee(email)) {
            TaskDetailRow row = buildTaskDetailRow(task, startDateTime, endDateTime, today, now);
            if (row != null) {
                rows.add(row);
            }
        }
        sortDetailRows(rows);

        EmployeeReportDetail detail = new EmployeeReportDetail();
        detail.setEmail(employee.getEmail());
        detail.setFullName(employee.getFullName());
        detail.setTasks(rows);
        return detail;
    }

    /**
     * Task-level detail for a whole team unit ("team:<id>" or "dept:<id>" for the no-team bucket).
     * Tasks assigned to several members of the unit are counted once (deduped by task id).
     */
    public EmployeeReportDetail getTeamReportDetail(String unitKey, String viewer, LocalDate start, LocalDate end) {
        return buildDetailForMembers(unitKey, resolveUnitMembers(unitKey), viewer, start, end);
    }

    /**
     * Task-level detail for an entire department (all its active members, any team),
     * limited to the people under the viewer.
     */
    public EmployeeReportDetail getDepartmentReportDetail(Integer departmentId, String viewer, LocalDate start, LocalDate end) {
        return buildDetailForMembers(
                "dept:" + departmentId,
                employeeRepository.findByDepartmentIdAndIsActiveTrue(departmentId),
                viewer, start, end);
    }

    private EmployeeReportDetail buildDetailForMembers(
            String detailKey, List<Employee> members, String viewer, LocalDate start, LocalDate end) {
        LocalDateTime startDateTime = start.atStartOfDay();
        LocalDateTime endDateTime = end.plusDays(1).atStartOfDay();
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();

        // only the members that are also under the viewer
        Set<String> scope = new HashSet<>(resolveScopeEmails(viewer));

        Map<Integer, Task> distinctTasks = new LinkedHashMap<>();
        for (Employee member : members) {
            if (!scope.contains(member.getEmail())) {
                continue;
            }
            for (Task task : taskService.getTasksForEmployee(member.getEmail())) {
                distinctTasks.putIfAbsent(task.getTaskId(), task);
            }
        }

        List<TaskDetailRow> rows = new ArrayList<>();
        for (Task task : distinctTasks.values()) {
            TaskDetailRow row = buildTaskDetailRow(task, startDateTime, endDateTime, today, now);
            if (row != null) {
                rows.add(row);
            }
        }
        sortDetailRows(rows);

        EmployeeReportDetail detail = new EmployeeReportDetail();
        detail.setEmail(detailKey);
        detail.setTasks(rows);
        return detail;
    }

    private List<Employee> resolveUnitMembers(String unitKey) {
        if (unitKey == null || !unitKey.contains(":")) {
            return List.of();
        }

        String[] parts = unitKey.split(":", 2);
        Integer id;
        try {
            id = Integer.valueOf(parts[1]);
        } catch (NumberFormatException ex) {
            return List.of();
        }

        if ("team".equals(parts[0])) {
            return employeeRepository.findByTeamIdAndIsActiveTrue(id);
        }
        if ("dept".equals(parts[0])) {
            return employeeRepository.findByDepartmentIdAndTeamIdIsNullAndIsActiveTrue(id);
        }
        return List.of();
    }

    /**
     * Builds one detail row, or returns null if the task is not relevant to the report
     * (i.e. neither currently open, nor completed within the range, nor cancelled).
     */
    private TaskDetailRow buildTaskDetailRow(
            Task task,
            LocalDateTime startDateTime,
            LocalDateTime endDateTime,
            LocalDate today,
            LocalDateTime now
    ) {
        List<TaskHistory> history =
                taskHistoryRepository.findByTask_TaskIdOrderByChangedAtAsc(task.getTaskId());

        LocalDateTime firstInProgress = null;
        LocalDateTime lastCompleted = null;
        for (TaskHistory entry : history) {
            if (!"Status".equals(entry.getFieldChanged())) {
                continue;
            }
            if (firstInProgress == null && "IN_PROGRESS".equals(entry.getNewValue())) {
                firstInProgress = entry.getChangedAt();
            }
            if (isCompletedStatus(entry.getNewValue())) {
                lastCompleted = entry.getChangedAt();
            }
        }

        boolean completed = isCompletedStatus(task.getStatus());
        boolean open = !isTerminalStatus(task.getStatus());
        boolean cancelled = "CANCELLED".equals(task.getStatus());
        LocalDateTime durationStart = firstInProgress != null ? firstInProgress : task.getCreatedAt();

        boolean completedInRange = completed
                && lastCompleted != null
                && !lastCompleted.isBefore(startDateTime)
                && lastCompleted.isBefore(endDateTime);

        // include currently-open tasks, tasks completed within the range, and cancelled tasks
        if (!open && !completedInRange && !cancelled) {
            return null;
        }

        Integer minutesToComplete = null;
        if (completed) {
            if (task.getCompletionMinutes() != null) {
                minutesToComplete = task.getCompletionMinutes();
            } else if (lastCompleted != null && durationStart != null
                    && !lastCompleted.isBefore(durationStart)) {
                minutesToComplete = (int) Duration.between(durationStart, lastCompleted).toMinutes();
            }
        }

        Integer minutesOpen = null;
        if (open && durationStart != null && !now.isBefore(durationStart)) {
            minutesOpen = (int) Duration.between(durationStart, now).toMinutes();
        }

        boolean overdue = false;
        if (task.getDueDate() != null && task.getDueDate().isBefore(today)) {
            if (open) {
                overdue = true;
            } else if (completed && lastCompleted != null
                    && lastCompleted.toLocalDate().isAfter(task.getDueDate())) {
                overdue = true;
            }
        }

        List<String> assignees = taskService.getAssignmentsForTask(task.getTaskId())
                .stream()
                .map(assignment -> assignment.getAssignedTo().getFullName())
                .toList();

        TaskDetailRow row = new TaskDetailRow();
        row.setTaskId(task.getTaskId());
        row.setTitle(task.getTitle());
        row.setStatus(task.getStatus());
        row.setPriority(task.getPriority());
        row.setClient(task.getClient());
        row.setDueDate(task.getDueDate());
        row.setAssignedTo(assignees);
        row.setCompletedAt(lastCompleted != null ? lastCompleted.toLocalDate() : null);
        row.setCompleted(completedInRange);
        row.setOpen(open);
        row.setOverdue(overdue);
        row.setMinutesToComplete(minutesToComplete);
        row.setMinutesOpen(minutesOpen);
        return row;
    }

    private void sortDetailRows(List<TaskDetailRow> rows) {
        // Overdue first, then open, then completed; longest-open / longest-to-complete near the top.
        rows.sort(Comparator
                .comparing(TaskDetailRow::isOverdue).reversed()
                .thenComparing(Comparator.comparing(TaskDetailRow::isOpen).reversed())
                .thenComparing(this::effectiveMinutes, Comparator.reverseOrder()));
    }

    private long effectiveMinutes(TaskDetailRow row) {
        if (row.getMinutesOpen() != null) {
            return row.getMinutesOpen();
        }
        if (row.getMinutesToComplete() != null) {
            return row.getMinutesToComplete();
        }
        return 0L;
    }

    private boolean isCompletedStatus(String status) {
        return "COMPLETED".equals(status) || "DONE".equals(status);
    }

    private boolean isTerminalStatus(String status) {
        return isCompletedStatus(status) || "CANCELLED".equals(status);
    }
}
