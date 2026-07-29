package com.treppides.taskmanager.controllers;

import com.treppides.taskmanager.auth.AccessScope;
import com.treppides.taskmanager.auth.AccessScopeResolver;
import com.treppides.taskmanager.services.FinancialsService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Map;

/**
 * Financials reports (board / admin only for v1) — reproduces the KT Financials v7
 * Power BI model from the datamart. Every endpoint is gated by {@link AccessScopeResolver}:
 * the caller must resolve to an unrestricted scope (board member or admin). Scoped
 * (EL / department) access is wired in the access layer but stays closed until granted.
 */
@RestController
@RequestMapping("/api/reports/financials")
public class FinancialsController {

    private final FinancialsService service;
    private final AccessScopeResolver scopeResolver;

    public FinancialsController(FinancialsService service, AccessScopeResolver scopeResolver) {
        this.service = service;
        this.scopeResolver = scopeResolver;
    }

    @GetMapping("/revenue")
    public Map<String, Object> revenue(
            Authentication auth,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String company,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String el,
            @RequestParam(required = false, defaultValue = "10") int top) {
        requireBoardOrAdmin(auth);
        int yr = year != null ? year : LocalDate.now().getYear();
        return service.revenue(yr, company, department, el, top);
    }

    @GetMapping("/invoiced-receipts")
    public Map<String, Object> invoicedReceipts(
            Authentication auth,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String company) {
        requireBoardOrAdmin(auth);
        int yr = year != null ? year : LocalDate.now().getYear();
        return service.monthlyInvoicedReceipts(yr, company);
    }

    @GetMapping("/budget-vs-actual")
    public Map<String, Object> budgetVsActual(
            Authentication auth,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String company) {
        requireBoardOrAdmin(auth);
        int yr = year != null ? year : LocalDate.now().getYear();
        return service.budgetVsActual(yr, company);
    }

    @GetMapping("/recoverability")
    public Map<String, Object> recoverability(
            Authentication auth,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String company,
            @RequestParam(required = false, defaultValue = "10") int top) {
        requireBoardOrAdmin(auth);
        return service.recoverability(year, top, company);
    }

    @GetMapping("/invoice-list")
    public Map<String, Object> invoiceList(
            Authentication auth,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String company,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String el,
            @RequestParam(required = false, defaultValue = "100") int top) {
        requireBoardOrAdmin(auth);
        int yr = year != null ? year : LocalDate.now().getYear();
        return service.invoiceList(yr, company, department, el, top);
    }

    @GetMapping("/debtors")
    public Map<String, Object> debtors(
            Authentication auth,
            @RequestParam(required = false) String company,
            @RequestParam(required = false, defaultValue = "20") int top) {
        requireBoardOrAdmin(auth);
        return service.debtors(top, company);
    }

    // ---- access gate ----

    private void requireBoardOrAdmin(Authentication auth) {
        AccessScope scope = scopeResolver.resolve(resolveEmail(auth));
        if (!scope.unrestricted()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Financials access requires board or admin membership");
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
