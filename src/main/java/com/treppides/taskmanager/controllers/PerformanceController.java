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

import java.time.LocalDate;
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

    /** HR/SUPER only: the raw level -> default-hours rows (kept for reference/back-compat). */
    @GetMapping("/levels")
    public List<Map<String, Object>> levels(Authentication auth) {
        requireEditor(auth);
        return repo.listLevelTargets();
    }

    /**
     * HR/SUPER only: the status options for THIS person, each with the seed contracted hours,
     * the status ratio, and the computed chargeable target (week + month). Drives the editor.
     */
    @GetMapping("/target-defaults/{code}")
    public List<Map<String, Object>> targetDefaults(Authentication auth, @PathVariable String code) {
        requireEditor(auth);
        return service.statusDefaults(code);
    }

    /**
     * HR/SUPER only: set an employee's status + contracted hours, effective from the viewed
     * month. The chargeable target is computed here (contracted x status ratio) and stored in
     * employee_level_history — editing a month never rewrites earlier months. Team membership
     * is NOT changed (that is driven by eSoft category4). Returns the recomputed card.
     */
    @PutMapping("/target/{code}")
    public PerformanceCardDTO updateTarget(
            Authentication auth,
            @PathVariable String code,
            @RequestBody Map<String, Object> body) {
        requireEditor(auth);

        String level = body.get("level") == null ? null : body.get("level").toString().trim();
        if (level == null || level.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "level is required");
        }
        Double contractedWeek = toDouble(body.get("contractedHrsWeek"));
        if (contractedWeek != null && contractedWeek < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "contracted hours cannot be negative");
        }
        double cw = contractedWeek == null ? 0.0 : contractedWeek;
        String location = body.get("location") == null ? null : body.get("location").toString().trim();

        // Chargeable target derived server-side from status + contracted (single source of truth).
        double targetWeek  = service.computeTargetWeek(level, cw);
        double targetMonth = Math.round(targetWeek * (52.0 / 12.0) * 10000.0) / 10000.0;

        // Effective from the month the editor was viewing (defaults to the current month).
        LocalDate now = LocalDate.now();
        int effYear  = body.get("year")  != null ? toInt(body.get("year"))  : now.getYear();
        int effMonth = body.get("month") != null ? toInt(body.get("month")) : now.getMonthValue();
        if (effMonth < 1 || effMonth > 12) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "month must be 1-12");
        }

        repo.upsertLevelHistory(code, effYear, effMonth, level, targetMonth, targetWeek, cw, location, resolveEmail(auth));
        return service.buildCardByCode(code, "month", effYear, effMonth);
    }

    private static int toInt(Object v) {
        try { return (int) Double.parseDouble(v.toString().trim()); }
        catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid number: " + v);
        }
    }

    private static Double toDouble(Object v) {
        if (v == null || v.toString().isBlank()) return null;
        try { return Double.parseDouble(v.toString()); }
        catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid number: " + v);
        }
    }

    /** SUPER admins + HR only — the gate for editing performance targets. */
    private void requireEditor(Authentication auth) {
        if (!roleService.canEditTargets(resolveEmail(auth))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "HR or super-admin access required");
        }
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
