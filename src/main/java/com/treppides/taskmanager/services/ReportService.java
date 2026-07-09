package com.treppides.taskmanager.services;

import com.treppides.taskmanager.dto.EmployeeCompletionStat;
import com.treppides.taskmanager.dto.EmployeeStatsResponse;
import com.treppides.taskmanager.dto.EmployeeWorkloadStat;
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
}
