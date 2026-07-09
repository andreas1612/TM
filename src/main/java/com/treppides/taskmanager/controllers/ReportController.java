package com.treppides.taskmanager.controllers;

import com.treppides.taskmanager.dto.EmployeeCompletionStat;
import com.treppides.taskmanager.dto.EmployeeStatsResponse;
import com.treppides.taskmanager.dto.GroupStatsResponse;
import com.treppides.taskmanager.services.ReportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/reports")
@CrossOrigin
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/completed-per-employee")
    public List<EmployeeCompletionStat> getCompletedPerEmployee(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end
    ) {
        return reportService.getCompletedPerEmployee(start, end);
    }

    @GetMapping("/employee-stats")
    public List<EmployeeStatsResponse> getEmployeeStats(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end
    ) {
        return reportService.getEmployeeStats(start, end);
    }

    @GetMapping("/team-stats")
    public List<GroupStatsResponse> getTeamStats(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end
    ) {
        return reportService.getTeamStats(start, end);
    }

    @GetMapping("/department-stats")
    public List<GroupStatsResponse> getDepartmentStats(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end
    ) {
        return reportService.getDepartmentStats(start, end);
    }
}
