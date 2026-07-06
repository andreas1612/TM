package com.treppides.taskmanager.controllers;

import com.treppides.taskmanager.auth.RoleService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * View-as simulator — TEST ENV ONLY (@ConditionalOnProperty app.simulator.enabled=true).
 *
 * Identity is sourced from the InternalTools EMPLOYEES directory keyed by EMAIL — the SAME
 * source prod authenticates against (Azure returns the email → EMPLOYEES). eSoft is NOT used
 * for identity (it is only the KPI/performance datamart). Picking an identity stores the email
 * in the session (SIM_USER_EMAIL); DevAuthFilter honours it, so the whole hub renders through
 * that person's real tier + scope. Absent in production.
 */
@RestController
@ConditionalOnProperty(name = "app.simulator.enabled", havingValue = "true")
@RequestMapping("/api/sim")
public class SimulatorController {

    private final JdbcTemplate jdbc;
    private final RoleService roleService;

    public SimulatorController(JdbcTemplate jdbc, RoleService roleService) {
        this.jdbc = jdbc;
        this.roleService = roleService;
    }

    /**
     * Identity picker from EMPLOYEES: all ACTIVE staff PLUS every admin (even if inactive),
     * each carrying its resolved tier; admins (FULL then STANDARD) sorted to the top.
     */
    @GetMapping("/employees")
    public List<Map<String, Object>> employees() {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT EMAIL AS email, FULLNAME AS name, ISACTIVE AS active FROM dbo.EMPLOYEES WHERE EMAIL IS NOT NULL");
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            String email = (String) r.get("email");
            RoleService.Tier tier = roleService.tierOf(email);
            boolean active = Boolean.TRUE.equals(r.get("active"));
            if (!active && tier == RoleService.Tier.NONE) continue; // drop inactive non-admins
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("email", email);
            e.put("name", r.get("name"));
            e.put("tier", tier.name());
            e.put("active", active);
            out.add(e);
        }
        out.sort((a, b) -> {
            int ra = rank((String) a.get("tier")), rb = rank((String) b.get("tier"));
            if (ra != rb) return Integer.compare(ra, rb);
            return String.valueOf(a.get("name")).compareToIgnoreCase(String.valueOf(b.get("name")));
        });
        return out;
    }

    /** Become an identity (by email) for this session. */
    @PostMapping("/login")
    public Map<String, Object> login(@RequestParam String email, HttpServletRequest req) {
        req.getSession(true).setAttribute("SIM_USER_EMAIL", email);
        return Map.of("ok", true, "email", email, "tier", roleService.tierOf(email).name());
    }

    /** Drop the simulated identity (back to the default dev identity). */
    @PostMapping("/logout")
    public Map<String, Object> logout(HttpServletRequest req) {
        var s = req.getSession(false);
        if (s != null) s.removeAttribute("SIM_USER_EMAIL");
        return Map.of("ok", true);
    }

    private static int rank(String tier) {
        return switch (tier) { case "FULL" -> 0; case "STANDARD" -> 1; default -> 2; };
    }
}
