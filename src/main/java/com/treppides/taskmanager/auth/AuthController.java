package com.treppides.taskmanager.auth;

import com.treppides.taskmanager.repositories.BudgetRepository;
import com.treppides.taskmanager.repositories.PerformanceRepository;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
public class AuthController {

    private final AdminService adminService;
    private final PerformanceRepository perfRepo;
    private final BudgetRepository budgetRepo;
    private final OAuth2AuthorizedClientService authorizedClientService;
    private final RestTemplate restTemplate = new RestTemplate();

    public AuthController(AdminService adminService,
                          PerformanceRepository perfRepo,
                          BudgetRepository budgetRepo,
                          OAuth2AuthorizedClientService authorizedClientService) {
        this.adminService = adminService;
        this.perfRepo = perfRepo;
        this.budgetRepo = budgetRepo;
        this.authorizedClientService = authorizedClientService;
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

    @GetMapping("/api/graph-test")
    public Map<String, Object> graphTest(@AuthenticationPrincipal OidcUser user,
                                         Authentication authentication) {
        Map<String, Object> result = new HashMap<>();
        String email = user.getPreferredUsername().toLowerCase();
        result.put("email", email);

        OAuth2AuthorizedClient client = null;
        if (authentication instanceof OAuth2AuthenticationToken oauthToken) {
            client = authorizedClientService.loadAuthorizedClient(
                oauthToken.getAuthorizedClientRegistrationId(),
                oauthToken.getName()
            );
        }

        if (client == null || client.getAccessToken() == null) {
            result.put("error", "No Graph access token available — make sure the Graph scope is in application.properties and re-login.");
            return result;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(client.getAccessToken().getTokenValue());
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        // Full profile
        try {
            ResponseEntity<Map> resp = restTemplate.exchange(
                "https://graph.microsoft.com/v1.0/users/" + email +
                "?$select=id,displayName,givenName,surname,mail,userPrincipalName," +
                "mobilePhone,businessPhones,jobTitle,department,officeLocation,companyName,employeeId",
                HttpMethod.GET, entity, Map.class
            );
            result.put("profile", resp.getBody());
        } catch (Exception e) {
            result.put("profileError", e.getMessage());
        }

        // Manager / supervisor
        try {
            ResponseEntity<Map> resp = restTemplate.exchange(
                "https://graph.microsoft.com/v1.0/users/" + email +
                "/manager?$select=displayName,mail,jobTitle,mobilePhone,businessPhones,department",
                HttpMethod.GET, entity, Map.class
            );
            result.put("manager", resp.getBody());
        } catch (Exception e) {
            result.put("managerError", e.getMessage());
        }

        // Direct reports
        try {
            ResponseEntity<Map> resp = restTemplate.exchange(
                "https://graph.microsoft.com/v1.0/users/" + email +
                "/directReports?$select=displayName,mail,jobTitle",
                HttpMethod.GET, entity, Map.class
            );
            result.put("directReports", resp.getBody());
        } catch (Exception e) {
            result.put("directReportsError", e.getMessage());
        }

        // Photo (as base64)
        try {
            ResponseEntity<byte[]> photoResp = restTemplate.exchange(
                "https://graph.microsoft.com/v1.0/users/" + email + "/photo/$value",
                HttpMethod.GET, entity, byte[].class
            );
            if (photoResp.getBody() != null) {
                result.put("photoBase64", "data:image/jpeg;base64," +
                    java.util.Base64.getEncoder().encodeToString(photoResp.getBody()));
            }
        } catch (Exception e) {
            result.put("photoError", e.getMessage());
        }

        return result;
    }
}
