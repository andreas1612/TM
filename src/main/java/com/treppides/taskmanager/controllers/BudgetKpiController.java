package com.treppides.taskmanager.controllers;

import com.treppides.taskmanager.dto.BudgetKpiDTO;
import com.treppides.taskmanager.repositories.BudgetRepository;
import com.treppides.taskmanager.services.AdminService;
import com.treppides.taskmanager.services.BudgetKpiService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reports/budget-kpi")
public class BudgetKpiController {

    private final BudgetKpiService service;
    private final AdminService adminService;
    private final BudgetRepository budgetRepo;

    public BudgetKpiController(BudgetKpiService service,
                                AdminService adminService,
                                BudgetRepository budgetRepo) {
        this.service = service;
        this.adminService = adminService;
        this.budgetRepo = budgetRepo;
    }

    @GetMapping("/me")
    public BudgetKpiDTO me(
            Authentication auth,
            @RequestParam(required = false) Integer year) {
        return service.buildKpi(resolveEmail(auth), year);
    }

    /** Admin-only: get budget KPI for any manager by eSoft code. */
    @GetMapping("/{code}")
    public BudgetKpiDTO byCode(
            Authentication auth,
            @PathVariable String code,
            @RequestParam(required = false) Integer year) {
        requireAdmin(auth);
        return service.buildKpiByCode(code, year);
    }

    /** Admin-only: list all managers with budget data for dropdown. */
    @GetMapping("/managers")
    public List<Map<String, Object>> managers(
            Authentication auth,
            @RequestParam(required = false) Integer year) {
        requireAdmin(auth);
        int yr = year != null ? year : LocalDate.now().getYear();
        return budgetRepo.findAllBudgetManagers(yr);
    }

    private void requireAdmin(Authentication auth) {
        String email = resolveEmail(auth);
        if (!adminService.isAdmin(email)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
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
