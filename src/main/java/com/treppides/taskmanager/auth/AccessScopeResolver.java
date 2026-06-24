package com.treppides.taskmanager.auth;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolves the current user's {@link AccessScope} — the single, reusable access
 * decision used by every report (Performance, Financials, ...).
 *
 * Sources (all in InternalTools — the primary datasource / datamart):
 *   - esoft_employees  : email -> live eSoft code (roster is always current)
 *   - reporting_lines  : manager_code -> report_code (hand-maintained hierarchy)
 *   - report_grants    : principal -> grant (data-driven; DEPARTMENT/EL/ALL)
 * Plus the config allow-lists: {@link AdminService} and {@link BoardService}.
 *
 * Resolution:
 *   admin or board               -> ALL (unrestricted)
 *   explicit grant rows          -> add DEPARTMENT / EL keys, or ALL
 *   has rows in reporting_lines  -> TEAM (self + direct reports)
 *   otherwise                    -> SELF (own code only)
 */
@Service
public class AccessScopeResolver {

    private final JdbcTemplate jdbc;          // primary datasource = InternalTools (datamart)
    private final AdminService adminService;
    private final BoardService boardService;

    public AccessScopeResolver(JdbcTemplate jdbcTemplate,
                               AdminService adminService,
                               BoardService boardService) {
        this.jdbc = jdbcTemplate;
        this.adminService = adminService;
        this.boardService = boardService;
    }

    public AccessScope resolve(String email) {
        if (email == null) {
            return new AccessScope(AccessScope.Level.SELF, false, null,
                    Set.of(), Set.of(), Set.of());
        }
        String lower = email.toLowerCase();
        String ownCode = findCodeByEmail(lower);

        // Board / admin → unrestricted.
        if (adminService.isAdmin(lower) || boardService.isBoard(lower)) {
            return AccessScope.all(ownCode);
        }

        Set<String> employeeCodes = new HashSet<>();
        Set<String> departmentCodes = new HashSet<>();
        Set<String> elCodes = new HashSet<>();
        if (ownCode != null) employeeCodes.add(ownCode);   // SELF baseline

        // Data-driven grants (DEPARTMENT / EL / explicit ALL).
        for (Map<String, Object> g : findGrants(lower)) {
            String type = String.valueOf(g.get("grant_type"));
            String key  = (String) g.get("grant_key");
            switch (type) {
                case "ALL"        -> { return AccessScope.all(ownCode); }
                case "DEPARTMENT" -> { if (key != null) departmentCodes.add(key); }
                case "EL"         -> { if (key != null) elCodes.add(key); }
                default           -> { /* SELF / TEAM handled below */ }
            }
        }

        // TEAM: anyone with direct reports sees self + reports.
        AccessScope.Level level = AccessScope.Level.SELF;
        if (ownCode != null) {
            List<String> reports = findDirectReports(ownCode);
            if (!reports.isEmpty()) {
                employeeCodes.addAll(reports);
                level = AccessScope.Level.TEAM;
            }
        }
        if (!departmentCodes.isEmpty()) level = AccessScope.Level.DEPARTMENT;
        if (!elCodes.isEmpty())         level = AccessScope.Level.EL;

        return new AccessScope(level, false, ownCode, employeeCodes, departmentCodes, elCodes);
    }

    // ---- datamart lookups ----

    private String findCodeByEmail(String email) {
        List<String> rows = jdbc.queryForList("""
            SELECT employee_code FROM dbo.esoft_employees
            WHERE LOWER(email) = ? AND inactive = 0
            """, String.class, email);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private List<String> findDirectReports(String managerCode) {
        return jdbc.queryForList("""
            SELECT report_code FROM dbo.reporting_lines WHERE manager_code = ?
            """, String.class, managerCode);
    }

    private List<Map<String, Object>> findGrants(String principal) {
        return jdbc.queryForList("""
            SELECT grant_type, grant_key FROM dbo.report_grants WHERE LOWER(principal) = ?
            """, principal);
    }
}
