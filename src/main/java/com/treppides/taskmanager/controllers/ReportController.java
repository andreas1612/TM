package com.treppides.taskmanager.controllers;

import com.treppides.taskmanager.auth.RoleService;
import com.treppides.taskmanager.dto.EmployeeReportDetail;
import com.treppides.taskmanager.dto.EmployeeStatsResponse;
import com.treppides.taskmanager.dto.GroupStatsResponse;
import com.treppides.taskmanager.services.ReportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reportService;
    private final RoleService roleService;

    public ReportController(ReportService reportService, RoleService roleService) {
        this.reportService = reportService;
        this.roleService = roleService;
    }

    @GetMapping("/employee-stats")
    public List<EmployeeStatsResponse> getEmployeeStats(
            Authentication auth,
            @RequestParam String viewer,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end
    ) {
        requireSelfOrFull(auth, viewer);
        return reportService.getEmployeeStats(viewer, start, end);
    }

    @GetMapping("/team-stats")
    public List<GroupStatsResponse> getTeamStats(
            Authentication auth,
            @RequestParam String viewer,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end
    ) {
        requireSelfOrFull(auth, viewer);
        return reportService.getTeamStats(viewer, start, end);
    }

    @GetMapping("/department-stats")
    public List<GroupStatsResponse> getDepartmentStats(
            Authentication auth,
            @RequestParam String viewer,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end
    ) {
        requireSelfOrFull(auth, viewer);
        return reportService.getDepartmentStats(viewer, start, end);
    }

    @GetMapping("/employee-detail")
    public EmployeeReportDetail getEmployeeDetail(
            Authentication auth,
            @RequestParam String email,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end
    ) {
        requireSelfOrScopeOrFull(auth, email);
        return reportService.getEmployeeReportDetail(email, start, end);
    }

    @GetMapping("/team-detail")
    public EmployeeReportDetail getTeamDetail(
            Authentication auth,
            @RequestParam String unit,
            @RequestParam String viewer,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end
    ) {
        requireSelfOrFull(auth, viewer);
        return reportService.getTeamReportDetail(unit, viewer, start, end);
    }

    @GetMapping("/department-detail")
    public EmployeeReportDetail getDepartmentDetail(
            Authentication auth,
            @RequestParam Integer department,
            @RequestParam String viewer,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end
    ) {
        requireSelfOrFull(auth, viewer);
        return reportService.getDepartmentReportDetail(department, viewer, start, end);
    }

    private void requireSelfOrFull(Authentication auth, String target) {
        String caller = extractEmail(auth);
        if (caller.equalsIgnoreCase(target)) return;
        if (roleService.isFull(caller)) return;
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "You can only view your own reports");
    }

    /** Like requireSelfOrFull, but also allows viewing people within the caller's team scope. */
    private void requireSelfOrScopeOrFull(Authentication auth, String target) {
        String caller = extractEmail(auth);
        if (caller.equalsIgnoreCase(target)) return;
        if (roleService.isFull(caller)) return;
        if (reportService.isInScope(caller, target)) return;
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "You can only view details for people in your team");
    }

    private String extractEmail(Authentication auth) {
        if (auth.getPrincipal() instanceof OidcUser oidc) {
            return oidc.getPreferredUsername().toLowerCase();
        }
        return auth.getName().toLowerCase();
    }
}
