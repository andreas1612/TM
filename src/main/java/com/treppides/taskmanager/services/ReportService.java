package com.treppides.taskmanager.services;

import com.treppides.taskmanager.dto.EmployeeCompletionStat;
import com.treppides.taskmanager.dto.EmployeeStatsResponse;
import com.treppides.taskmanager.dto.EmployeeWorkloadStat;
import com.treppides.taskmanager.dto.GroupCompletionStat;
import com.treppides.taskmanager.dto.GroupStatsResponse;
import com.treppides.taskmanager.dto.GroupWorkloadStat;
import com.treppides.taskmanager.repositories.ReportRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReportService {

    private final ReportRepository reportRepository;

    public ReportService(ReportRepository reportRepository) {
        this.reportRepository = reportRepository;
    }

    /**
     * Tasks completed per employee between start and end (both inclusive, by calendar day).
     */
    public List<EmployeeCompletionStat> getCompletedPerEmployee(LocalDate start, LocalDate end) {
        return reportRepository.findCompletedPerEmployee(
                start.atStartOfDay(),
                end.plusDays(1).atStartOfDay()
        );
    }

    /**
     * Combined per-employee stats: completedCount within [start, end], plus a current
     * snapshot of assigned/open/overdue. Merges the two source queries by email so an
     * employee shows up if they have EITHER completions in the range OR a live workload.
     * Sorted by completed desc, then open desc.
     */
    public List<EmployeeStatsResponse> getEmployeeStats(LocalDate start, LocalDate end) {
        Map<String, EmployeeStatsResponse> byEmail = new LinkedHashMap<>();

        for (EmployeeWorkloadStat workload : reportRepository.findWorkloadPerEmployee(LocalDate.now())) {
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

        for (EmployeeCompletionStat completed : getCompletedPerEmployee(start, end)) {
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
     * Combined per-team roll-up. Each task is counted once per team (COUNT DISTINCT),
     * so co-assignment within a team does not inflate the totals.
     */
    public List<GroupStatsResponse> getTeamStats(LocalDate start, LocalDate end) {
        return mergeGroupStats(
                reportRepository.findWorkloadPerTeam(LocalDate.now()),
                reportRepository.findCompletedPerTeam(start.atStartOfDay(), end.plusDays(1).atStartOfDay())
        );
    }

    /**
     * Combined per-department roll-up. Each task is counted once per department.
     */
    public List<GroupStatsResponse> getDepartmentStats(LocalDate start, LocalDate end) {
        return mergeGroupStats(
                reportRepository.findWorkloadPerDepartment(LocalDate.now()),
                reportRepository.findCompletedPerDepartment(start.atStartOfDay(), end.plusDays(1).atStartOfDay())
        );
    }

    private List<GroupStatsResponse> mergeGroupStats(
            List<GroupWorkloadStat> workload,
            List<GroupCompletionStat> completed
    ) {
        Map<Integer, GroupStatsResponse> byGroup = new LinkedHashMap<>();

        for (GroupWorkloadStat stat : workload) {
            GroupStatsResponse row = byGroup.computeIfAbsent(
                    stat.getGroupId(), id -> new GroupStatsResponse());
            row.setGroupId(stat.getGroupId());
            row.setGroupName(stat.getGroupName());
            row.setAssignedCount(stat.getAssignedCount());
            row.setOpenCount(stat.getOpenCount());
            row.setOverdueCount(stat.getOverdueCount());
        }

        for (GroupCompletionStat stat : completed) {
            GroupStatsResponse row = byGroup.computeIfAbsent(
                    stat.getGroupId(), id -> new GroupStatsResponse());
            row.setGroupId(stat.getGroupId());
            if (row.getGroupName() == null) {
                row.setGroupName(stat.getGroupName());
            }
            row.setCompletedCount(stat.getCompletedCount());
        }

        return byGroup.values()
                .stream()
                .sorted(Comparator.comparingLong(GroupStatsResponse::getCompletedCount).reversed()
                        .thenComparing(Comparator.comparingLong(GroupStatsResponse::getOpenCount).reversed()))
                .toList();
    }
}
