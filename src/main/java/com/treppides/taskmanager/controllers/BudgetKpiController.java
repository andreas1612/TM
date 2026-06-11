package com.treppides.taskmanager.controllers;

import com.treppides.taskmanager.dto.BudgetKpiDTO;
import com.treppides.taskmanager.services.BudgetKpiService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/reports/budget-kpi")
public class BudgetKpiController {

    private final BudgetKpiService service;

    public BudgetKpiController(BudgetKpiService service) {
        this.service = service;
    }

    @GetMapping("/me")
    public BudgetKpiDTO me(
            Authentication auth,
            @RequestParam(required = false) Integer year) {
        return service.buildKpi(resolveEmail(auth), year);
    }

    private static String resolveEmail(Authentication auth) {
        if (auth == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        if (auth.getPrincipal() instanceof OidcUser oidc) {
            return oidc.getPreferredUsername();
        }
        return auth.getName();
    }
}
