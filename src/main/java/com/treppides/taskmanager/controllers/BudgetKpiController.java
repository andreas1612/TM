package com.treppides.taskmanager.controllers;

import com.treppides.taskmanager.dto.BudgetKpiDTO;
import com.treppides.taskmanager.repositories.BudgetRepository;
import com.treppides.taskmanager.repositories.FeeAdjustmentRepository;
import com.treppides.taskmanager.auth.AdminService;
import com.treppides.taskmanager.services.BudgetKpiService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;
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
    private final FeeAdjustmentRepository feeRepo;

    public BudgetKpiController(BudgetKpiService service,
                                AdminService adminService,
                                BudgetRepository budgetRepo,
                                FeeAdjustmentRepository feeRepo) {
        this.service = service;
        this.adminService = adminService;
        this.budgetRepo = budgetRepo;
        this.feeRepo = feeRepo;
    }

    @GetMapping("/me")
    public BudgetKpiDTO me(
            Authentication auth,
            @RequestParam(required = false) Integer year) {
        return service.buildKpi(resolveEmail(auth), year);
    }

    /** Admin-only: get budget KPI for any manager by invoice code. */
    @GetMapping("/{invoiceCode}")
    public BudgetKpiDTO byInvoiceCode(
            Authentication auth,
            @PathVariable String invoiceCode,
            @RequestParam(required = false) Integer year) {
        requireAdmin(auth);
        return service.buildKpiByInvoiceCode(invoiceCode, year);
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

    /** Admin-only: individual invoice lines for a manager (debug drill-down). */
    @GetMapping("/invoice-details/{invoiceCode}")
    public List<Map<String, Object>> invoiceDetails(
            Authentication auth,
            @PathVariable String invoiceCode,
            @RequestParam(required = false) Integer year) {
        requireAdmin(auth);
        int yr = year != null ? year : LocalDate.now().getYear();
        return budgetRepo.findInvoiceDetails(invoiceCode, yr);
    }

    /** Admin-only: list fee adjustments for a manager. */
    @GetMapping("/fee-adjustments/{invoiceCode}")
    public List<Map<String, Object>> feeAdjustments(
            Authentication auth,
            @PathVariable String invoiceCode,
            @RequestParam(required = false) Integer year) {
        requireAdmin(auth);
        int yr = year != null ? year : LocalDate.now().getYear();
        return feeRepo.findByInvoiceCode(invoiceCode, yr);
    }

    /** Admin-only: add a fee adjustment entry. */
    @PostMapping("/fee-adjustments")
    public Map<String, Object> addFeeAdjustment(
            Authentication auth,
            @RequestBody Map<String, Object> body) {
        requireAdmin(auth);
        String feeType = (String) body.get("feeType");
        String invoiceCode = (String) body.get("invoiceCode");
        String managerName = (String) body.get("managerName");
        int monthNum = ((Number) body.get("monthNum")).intValue();
        int year = ((Number) body.get("year")).intValue();
        double amount = ((Number) body.get("amount")).doubleValue();
        String entityName = (String) body.getOrDefault("entityName", "");
        String country = (String) body.getOrDefault("country", "");
        String enteredBy = resolveEmail(auth);

        if (feeType == null || invoiceCode == null || managerName == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing required fields");
        }
        if (!"AUDIT".equals(feeType) && !"TAX".equals(feeType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "feeType must be AUDIT or TAX");
        }
        if (monthNum < 1 || monthNum > 12) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "monthNum must be 1-12");
        }

        return feeRepo.insert(feeType, invoiceCode, managerName, monthNum, year, amount, entityName, country, enteredBy);
    }

    /** Admin-only: delete a fee adjustment entry. */
    @DeleteMapping("/fee-adjustments/{id}")
    public Map<String, Object> deleteFeeAdjustment(
            Authentication auth,
            @PathVariable int id) {
        requireAdmin(auth);
        boolean deleted = feeRepo.deleteById(id);
        if (!deleted) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Fee adjustment not found");
        }
        return Map.of("deleted", true);
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
