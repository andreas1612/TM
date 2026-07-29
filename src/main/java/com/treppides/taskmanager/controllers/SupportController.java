package com.treppides.taskmanager.controllers;

import com.treppides.taskmanager.dto.SupportTicketRequest;
import com.treppides.taskmanager.services.NotificationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/support")
public class SupportController {

    private final NotificationService notificationService;

    public SupportController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PostMapping("/ticket")
    public Map<String, String> submitTicket(@Valid @RequestBody SupportTicketRequest request,
                                            Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof OidcUser)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        OidcUser user = (OidcUser) auth.getPrincipal();
        String email = user.getAttribute("preferred_username");
        String name  = user.getAttribute("name");

        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not determine user email");
        }
        if (name == null || name.isBlank()) {
            name = email.split("@")[0];
        }

        notificationService.sendSupportTicketEmail(email, name, request.getCategory(), request.getMessage(), request.getType());

        return Map.of("status", "sent");
    }
}
