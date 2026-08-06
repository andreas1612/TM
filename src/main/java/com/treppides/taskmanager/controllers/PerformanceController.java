package com.treppides.taskmanager.controllers;

import com.treppides.taskmanager.dto.PerformanceCardDTO;
import com.treppides.taskmanager.auth.RoleService;
import com.treppides.taskmanager.services.PerformanceService;
import com.treppides.taskmanager.repositories.PerformanceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reports/performance")
public class PerformanceController {

    private final PerformanceService service;
    private final RoleService roleService;
    private final PerformanceRepository repo;

    public PerformanceController(PerformanceService service,
                                  RoleService roleService,
                                  PerformanceRepository repo) {
        this.service = service;
        this.roleService = roleService;
        this.repo = repo;
    }

    @GetMapping("/me")
    public PerformanceCardDTO me(
            Authentication auth,
            @RequestParam(defaultValue = "month") String period,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {
        return service.buildCard(resolveEmail(auth), period, year, month);
    }

    @GetMapping("/team")
    public PerformanceCardDTO team(
            Authentication auth,
            @RequestParam(defaultValue = "month") String period,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {
        PerformanceCardDTO card = service.buildTeamCard(resolveEmail(auth), period, year, month);
        if (!card.isManager()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not a manager");
        }
        return card;
    }

    /**
     * Manager-scoped drill-down: full performance card for one of the caller's OWN direct
     * reports. Authorised live against eSoft (category4) — a manager can only open a person
     * who actually reports to them. Lets non-admin supervisors see report detail without the
     * admin-only endpoints below.
     */
    @GetMapping("/report/{code}")
    public PerformanceCardDTO reportDetail(
            Authentication auth,
            @PathVariable String code,
            @RequestParam(defaultValue = "month") String period,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {
        String myCode = repo.findCodeByEmail(resolveEmail(auth))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "No eSoft identity"));
        if (!service.isReportOf(myCode, code)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your direct report");
        }
        return service.buildCardByCode(code, period, year, month);
    }

    /** Admin-only: get performance card for any employee by eSoft code. */
    @GetMapping("/{code}")
    public PerformanceCardDTO byCode(
            Authentication auth,
            @PathVariable String code,
            @RequestParam(defaultValue = "month") String period,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {
        requireAdmin(auth);
        return service.buildCardByCode(code, period, year, month);
    }

    /** Admin-only: get team card for any manager by eSoft code. */
    @GetMapping("/{code}/team")
    public PerformanceCardDTO teamByCode(
            Authentication auth,
            @PathVariable String code,
            @RequestParam(defaultValue = "month") String period,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {
        requireAdmin(auth);
        return service.buildTeamCardByCode(code, period, year, month);
    }

    /** Admin-only: list all employees for dropdown. */
    @GetMapping("/employees")
    public List<Map<String, Object>> employees(Authentication auth) {
        requireAdmin(auth);
        return repo.findAllEmployees();
    }

    private void requireAdmin(Authentication auth) {
        String email = resolveEmail(auth);
        if (!roleService.canViewAllReports(email)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "FULL-tier or HR access required");
        }
    }

    private static String resolveEmail(Authentication auth) {
        if (auth == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        if (auth.getPrincipal() instanceof OidcUser oidc) {
            return oidc.getPreferredUsername().toLowerCase();
        }
        return auth.getName().toLowerCase();
    }
}
