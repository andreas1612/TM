package com.treppides.taskmanager.controllers;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * View-as simulator — TEST ENV ONLY.
 *
 * Loads ONLY when {@code app.simulator.enabled=true} (set on the Docker test instance;
 * absent in production, so this controller does not exist there). Lets a tester pick any
 * employee identity; the choice is stored in the HTTP session (SIM_USER_CODE) and honoured
 * by DevAuthFilter, so the whole hub renders through that person's real tier + scope.
 *
 * Requires the dev profile (DevAuthFilter reads the session attribute). Never wired into
 * the production Azure security chain.
 */
@RestController
@ConditionalOnProperty(name = "app.simulator.enabled", havingValue = "true")
@RequestMapping("/api/sim")
public class SimulatorController {

    private final JdbcTemplate jdbc;

    public SimulatorController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** All active employees, for the identity picker. */
    @GetMapping("/employees")
    public List<Map<String, Object>> employees() {
        return jdbc.queryForList(
            "SELECT employee_code AS code, employee_name AS name, email"
          + " FROM dbo.esoft_employees WHERE inactive = 0 AND email IS NOT NULL"
          + " ORDER BY employee_name");
    }

    /** Become an identity (by eSoft code) for this session. */
    @PostMapping("/login")
    public Map<String, Object> login(@RequestParam String code, HttpServletRequest req) {
        req.getSession(true).setAttribute("SIM_USER_CODE", code);
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT employee_name AS name, email FROM dbo.esoft_employees WHERE employee_code = ?", code);
        Map<String, Object> who = rows.isEmpty() ? Map.of() : rows.get(0);
        return Map.of("ok", true, "code", code, "identity", who);
    }

    /** Drop the simulated identity (back to the default dev user). */
    @PostMapping("/logout")
    public Map<String, Object> logout(HttpServletRequest req) {
        var s = req.getSession(false);
        if (s != null) s.removeAttribute("SIM_USER_CODE");
        return Map.of("ok", true);
    }
}
