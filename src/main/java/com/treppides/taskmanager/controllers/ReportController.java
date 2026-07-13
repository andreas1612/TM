package com.treppides.taskmanager.controllers;

import com.treppides.taskmanager.dto.EmployeeReportDetail;
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

    @GetMapping("/employee-stats")
    public List<EmployeeStatsResponse> getEmployeeStats(
            @RequestParam String viewer,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end
    ) {
        return reportService.getEmployeeStats(viewer, start, end);
    }

    @GetMapping("/team-stats")
    public List<GroupStatsResponse> getTeamStats(
            @RequestParam String viewer,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end
    ) {
        return reportService.getTeamStats(viewer, start, end);
    }

    @GetMapping("/department-stats")
    public List<GroupStatsResponse> getDepartmentStats(
            @RequestParam String viewer,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end
    ) {
        return reportService.getDepartmentStats(viewer, start, end);
    }

    @GetMapping("/employee-detail")
    public EmployeeReportDetail getEmployeeDetail(
            @RequestParam String email,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end
    ) {
        return reportService.getEmployeeReportDetail(email, start, end);
    }

    @GetMapping("/team-detail")
    public EmployeeReportDetail getTeamDetail(
            @RequestParam String unit,
            @RequestParam String viewer,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end
    ) {
        return reportService.getTeamReportDetail(unit, viewer, start, end);
    }
}
