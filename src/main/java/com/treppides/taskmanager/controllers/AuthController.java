package com.treppides.taskmanager.controllers;

import com.treppides.taskmanager.repositories.BudgetRepository;
import com.treppides.taskmanager.repositories.PerformanceRepository;
import com.treppides.taskmanager.services.AdminService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
public class AuthController {

    private final AdminService adminService;
    private final PerformanceRepository perfRepo;
    private final BudgetRepository budgetRepo;

    public AuthController(AdminService adminService,
                          PerformanceRepository perfRepo,
                          BudgetRepository budgetRepo) {
        this.adminService = adminService;
        this.perfRepo = perfRepo;
        this.budgetRepo = budgetRepo;
    }

    @GetMapping("/api/me")
    public Map<String, Object> me(@AuthenticationPrincipal OidcUser user) {
        String email = user.getPreferredUsername().toLowerCase();
        String name = user.getFullName();
        boolean isAdmin = adminService.isAdmin(email);

        Map<String, Object> result = new HashMap<>();
        result.put("email", email);
        result.put("name", name != null ? name : "");
        result.put("isAdmin", isAdmin);

        // Resolve eSoft code
        Optional<String> codeOpt = perfRepo.findCodeByEmail(email);
        String esoftCode = codeOpt.orElse(null);
        result.put("esoftCode", esoftCode);

        // Check if manager (has direct reports in performance_targets)
        boolean isManager = false;
        if (esoftCode != null) {
            try {
                Map<String, Object> target = perfRepo.findTargetByCode(esoftCode).orElse(null);
                if (target != null) {
                    String empName = (String) target.get("employee_name");
                    isManager = empName != null && !perfRepo.findDirectReports(empName).isEmpty();
                }
            } catch (Exception ignored) {
                // performance_targets table may not exist yet
            }
        }
        result.put("isManager", isManager);

        // Check if has budget data
        boolean hasBudgetData = false;
        if (esoftCode != null) {
            try {
                hasBudgetData = budgetRepo.findBudgetByEsoftCode(esoftCode, LocalDate.now().getYear()).isPresent();
            } catch (Exception ignored) {
            }
        }
        result.put("hasBudgetData", hasBudgetData);

        return result;
    }
}
