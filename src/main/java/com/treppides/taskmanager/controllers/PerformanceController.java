package com.treppides.taskmanager.controllers;

import com.treppides.taskmanager.dto.PerformanceCardDTO;
import com.treppides.taskmanager.services.PerformanceService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/reports/performance")
public class PerformanceController {

    private final PerformanceService service;

    public PerformanceController(PerformanceService service) {
        this.service = service;
    }

    @GetMapping("/me")
    public PerformanceCardDTO me(
            Authentication auth,
            @RequestParam(defaultValue = "month") String period) {
        return service.buildCard(resolveEmail(auth), period);
    }

    @GetMapping("/team")
    public PerformanceCardDTO team(
            Authentication auth,
            @RequestParam(defaultValue = "month") String period) {
        PerformanceCardDTO card = service.buildTeamCard(resolveEmail(auth), period);
        if (!card.isManager()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not a manager");
        }
        return card;
    }

    private static String resolveEmail(Authentication auth) {
        if (auth == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        if (auth.getPrincipal() instanceof OidcUser oidc) {
            return oidc.getPreferredUsername();
        }
        // Dev mode: UsernamePasswordAuthenticationToken with azure_email as principal name
        return auth.getName();
    }
}
