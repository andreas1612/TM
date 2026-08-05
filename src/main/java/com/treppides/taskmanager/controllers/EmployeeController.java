package com.treppides.taskmanager.controllers;

import com.treppides.taskmanager.auth.RoleService;
import com.treppides.taskmanager.dto.EmployeeOptionResponse;
import com.treppides.taskmanager.services.EmployeeService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/employees")
public class EmployeeController {

    private final EmployeeService employeeService;
    private final RoleService roleService;

    public EmployeeController(EmployeeService employeeService, RoleService roleService) {
        this.employeeService = employeeService;
        this.roleService = roleService;
    }

    @GetMapping("/direct-reports/{email}")
    public List<EmployeeOptionResponse> getDirectReports(Authentication auth,
                                                         @PathVariable String email) {
        requireSelfOrFull(auth, email);
        return employeeService.getDirectReports(email);
    }

    @GetMapping("/assignable/{email}")
    public List<EmployeeOptionResponse> getAssignableEmployees(Authentication auth,
                                                               @PathVariable String email) {
        requireSelfOrFull(auth, email);
        return employeeService.getAssignableEmployees(email);
    }

    private void requireSelfOrFull(Authentication auth, String target) {
        String caller = extractEmail(auth);
        if (caller.equalsIgnoreCase(target)) return;
        if (roleService.isFull(caller)) return;
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "You can only query your own employee data");
    }

    private String extractEmail(Authentication auth) {
        if (auth.getPrincipal() instanceof OidcUser oidc) {
            return oidc.getPreferredUsername().toLowerCase();
        }
        return auth.getName().toLowerCase();
    }
}
