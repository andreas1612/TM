package com.treppides.taskmanager.auth;

import com.treppides.taskmanager.repositories.BudgetRepository;
import com.treppides.taskmanager.repositories.PerformanceRepository;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
public class AuthController {

    private final AdminService adminService;
    private final BoardService boardService;
    private final RoleService roleService;
    private final PerformanceRepository perfRepo;
    private final BudgetRepository budgetRepo;
    private final OAuth2AuthorizedClientService authorizedClientService;
    private final RestTemplate restTemplate = new RestTemplate();

    @org.springframework.beans.factory.annotation.Value("${app.simulator.enabled:false}")
    private boolean simulatorEnabled;

    public AuthController(AdminService adminService,
                          BoardService boardService,
                          RoleService roleService,
                          PerformanceRepository perfRepo,
                          BudgetRepository budgetRepo,
                          OAuth2AuthorizedClientService authorizedClientService) {
        this.adminService = adminService;
        this.boardService = boardService;
        this.roleService = roleService;
        this.perfRepo = perfRepo;
        this.budgetRepo = budgetRepo;
        this.authorizedClientService = authorizedClientService;
    }

    @GetMapping("/api/me")
    public Map<String, Object> me(Authentication auth,
                                  jakarta.servlet.http.HttpServletResponse response) {
        if (auth == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        // Works for both Azure (OidcUser) and the dev profile (X-Dev-User-Code login).
        String email;
        String name;
        if (auth.getPrincipal() instanceof OidcUser oidc) {
            email = oidc.getPreferredUsername().toLowerCase();
            name = oidc.getFullName();
        } else {
            email = auth.getName().toLowerCase();
            Object details = auth.getDetails();
            name = details != null ? details.toString() : "";
        }
        // Expose verified email as a response header so nginx auth_request_set
        // can forward it to backend services (replaces untrusted X-User-Email).
        response.setHeader("X-Auth-User", email);

        boolean isAdmin = adminService.isAdmin(email);

        Map<String, Object> result = new HashMap<>();
        result.put("email", email);
        result.put("name", name != null ? name : "");
        result.put("isAdmin", isAdmin);
        // Board-membership flag: SUPER-tier or configured board members (app.board.emails).
        // NOTE: this is NOT the Financials gate — Financials is gated by the "financials"
        // feature (FULL + SUPER) and AccessScopeResolver (unrestricted = FULL OR board).
        result.put("isBoardMember", roleService.isSuper(email) || boardService.isBoard(email));

        // Hub access tier + visible feature set (single source of truth: RoleService).
        // FULL = everything incl. hidden/WIP; STANDARD = base hub; NONE = restricted.
        RoleService.Tier tier = roleService.tierOf(email);
        result.put("tier", tier.name());
        result.put("features", roleService.features(tier));
        // Read-across capability for Performance / Budget KPI: FULL, SUPER, or HR.
        // Lets the HR team see everyone's cards without granting the full admin tier.
        result.put("canViewAllReports", roleService.canViewAllReports(email));
        // Test-env only: tells the hub to show the "View as" switcher. False/absent in prod.
        result.put("simulator", simulatorEnabled);

        // Resolve eSoft code
        Optional<String> codeOpt = perfRepo.findCodeByEmail(email);
        String esoftCode = codeOpt.orElse(null);
        result.put("esoftCode", esoftCode);

        // Check if manager — resolved LIVE from eSoft category4 (supervisor field).
        boolean isManager = false;
        if (esoftCode != null) {
            try {
                isManager = !perfRepo.findDirectReportsByCode(esoftCode).isEmpty();
            } catch (Exception ignored) {
                // eSoft may be briefly unreachable — treat as non-manager rather than failing /api/me
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
                                         Authentication authentication,
                                         @RequestParam(required = false) String email) {
        Map<String, Object> result = new HashMap<>();
        String callerEmail = user.getPreferredUsername().toLowerCase();
        if (email == null || email.isBlank()) {
            email = callerEmail;
        } else if (!email.equalsIgnoreCase(callerEmail) && !roleService.isFull(callerEmail)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You can only query your own Graph profile");
        }
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
