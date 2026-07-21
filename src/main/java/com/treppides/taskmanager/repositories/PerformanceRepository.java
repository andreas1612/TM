package com.treppides.taskmanager.repositories;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Performance data access — repointed to the InternalTools DATAMART (2026-06-24).
 *
 * Previously this read live eSoft + a stale 103-row seed table (which didn't even
 * exist in the DB, so it fell back to an in-memory copy of the same seed). That
 * made new eSoft staff invisible. Now EVERYTHING reads from the datamart, which is
 * refreshed from eSoft by the sync job:
 *
 *   - roster / identity      -> dbo.esoft_employees  (live; new hires appear, leavers drop)
 *   - chargeable hours        -> dbo.esoft_timesheets + esoft_workcodes + esoft_jobcards
 *   - job title / EL / team   -> dbo.esoft_employee_categories (C1 / C2 / C3)
 *   - target hours by level   -> dbo.level_targets  (new/unmapped staff default to 'Trainee')
 *   - per-person level/loc/mgr-> dbo.employee_levels (snapshot; known staff keep their values)
 *
 * Parity verified: chargeable-hours-per-employee from the datamart matches eSoft-direct
 * to the cent (0 mismatches across all employees). Map keys are unchanged so
 * PerformanceService is untouched.
 *
 * eSoft is NEVER queried here anymore — the app reads only InternalTools.
 */
@Repository
public class PerformanceRepository {

    private static final Logger log = LoggerFactory.getLogger(PerformanceRepository.class);

    private final JdbcTemplate jdbc;                 // primary datasource = InternalTools (datamart)
    private final NamedParameterJdbcTemplate namedJdbc;
    private final JdbcTemplate esoftJdbc;            // eSoft LIVE (read-only) — supervisor relationship only

    public PerformanceRepository(JdbcTemplate jdbcTemplate,
                                 @Qualifier("esoftJdbcTemplate") JdbcTemplate esoftJdbc) {
        this.jdbc = jdbcTemplate;
        this.namedJdbc = new NamedParameterJdbcTemplate(jdbcTemplate);
        this.esoftJdbc = esoftJdbc;
    }

    /** Resolve an email to an eSoft employee code (active staff only). */
    public Optional<String> findCodeByEmail(String email) {
        List<String> rows = jdbc.queryForList("""
            SELECT employee_code
            FROM   dbo.esoft_employees
            WHERE  LOWER(email) = LOWER(?)
              AND  inactive = 0
            """, String.class, email);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /**
     * Target/identity row for an employee. Returns a row for ANY employee in the
     * roster — unmapped/new staff resolve to the 'Trainee' target by default
     * (instead of the old 404). Keys match the old performance_targets shape.
     */
    public Optional<Map<String, Object>> findTargetByCode(String esoftCode) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT e.employee_code                       AS esoft_code,
                   e.employee_name                       AS employee_name,
                   COALESCE(el.level, 'Trainee')         AS level,
                   COALESCE(el.target_hrs_month, lt.target_hrs_month) AS target_hrs_month,
                   COALESCE(el.target_hrs_week,  lt.target_hrs_week)  AS target_hrs_week,
                   el.location                           AS location,
                   el.manager_name                       AS manager_name,
                   e.email                               AS azure_email
            FROM   dbo.esoft_employees e
            LEFT JOIN dbo.employee_levels el ON el.esoft_code = e.employee_code
            LEFT JOIN dbo.level_targets   lt ON lt.level = COALESCE(el.level, 'Trainee')
            WHERE  e.employee_code = ?
            """, esoftCode);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public Optional<Map<String, Object>> findActualHours(String esoftCode, LocalDate start, LocalDate end) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT
                SUM(tl.total_week_hours)        AS actual_hrs,
                e.wrk_units_total               AS available_hrs_week,
                c1.description                  AS job_title,
                c2.description                  AS engagement_leader,
                c3.description                  AS team_name
            FROM dbo.esoft_timesheets tl
            JOIN dbo.esoft_employees e
                ON e.employee_code = tl.employee_code
            JOIN dbo.esoft_workcodes w
                ON w.work_code = tl.work_code
            JOIN dbo.esoft_jobcards jc
                ON jc.jobcard = tl.jobcard
            LEFT JOIN dbo.esoft_employee_categories c1
                ON c1.category_head = 'C1' AND c1.category_code = e.category1
            LEFT JOIN dbo.esoft_employee_categories c2
                ON c2.category_head = 'C2' AND c2.category_code = e.category2
            LEFT JOIN dbo.esoft_employee_categories c3
                ON c3.category_head = 'C3' AND c3.category_code = e.category3
            WHERE tl.employee_code = ?
              AND tl.ts_date >= ?
              AND tl.ts_date <  ?
              AND w.not_chargeable = 0
              AND jc.h3_department != 'K'
            GROUP BY
                e.wrk_units_total,
                c1.description,
                c2.description,
                c3.description
            """, esoftCode, start, end);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public List<Map<String, Object>> findTeamActualHours(List<String> codes, LocalDate start, LocalDate end) {
        if (codes == null || codes.isEmpty()) return Collections.emptyList();
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("codes", codes)
            .addValue("start", start)
            .addValue("end", end);
        return namedJdbc.queryForList("""
            SELECT
                tl.employee_code                AS esoft_code,
                SUM(tl.total_week_hours)        AS actual_hrs,
                e.wrk_units_total               AS available_hrs_week
            FROM dbo.esoft_timesheets tl
            JOIN dbo.esoft_employees e
                ON e.employee_code = tl.employee_code
            JOIN dbo.esoft_workcodes w
                ON w.work_code = tl.work_code
            JOIN dbo.esoft_jobcards jc
                ON jc.jobcard = tl.jobcard
            WHERE tl.employee_code IN (:codes)
              AND tl.ts_date >= :start
              AND tl.ts_date <  :end
              AND w.not_chargeable = 0
              AND jc.h3_department != 'K'
            GROUP BY
                tl.employee_code,
                e.wrk_units_total
            """, params);
    }

    /**
     * Direct reports resolved LIVE from the eSoft database (category4 = the supervisor field),
     * NOT the nightly datamart and NOT the Excel-seeded manager_name. This is the ONE place in
     * the performance report that queries eSoft directly, so supervisor changes made in eSoft
     * take effect immediately.
     *
     * Model: a team = everyone carrying the same C4 code; that code's description names the head.
     * So the reports of the manager with {@code managerCode} are the active employees carrying the
     * C4 code whose description matches the manager's own name (order/spacing/punctuation-insensitive).
     * Heads flagged inactive in eSoft still resolve, because we match on the C4 description rather
     * than the head's active flag. Returns rows shaped {esoft_code, employee_name}.
     */
    public List<Map<String, Object>> findDirectReportsByCode(String managerCode) {
        if (managerCode == null || managerCode.isBlank()) return Collections.emptyList();
        try {
            // 1) Manager's own name (include inactive — some heads are flagged inactive in eSoft).
            List<Map<String, Object>> meRows = esoftJdbc.queryForList(
                "SELECT invservemployee_name AS name FROM dbo.invservemployees WHERE invservemployee_code = ?",
                managerCode);
            if (meRows.isEmpty()) return Collections.emptyList();
            String myName = normName((String) meRows.get(0).get("name"));
            if (myName.isEmpty()) return Collections.emptyList();

            // 2) Find the C4 code whose description names me → I am that team's head.
            List<Map<String, Object>> cats = esoftJdbc.queryForList(
                "SELECT invservemployeecategory_code AS code, invservemployeecategory_description AS descr " +
                "FROM dbo.invservemployeecategories WHERE invservemployeecategory_head = 'C4'");
            String headCode = null;
            for (Map<String, Object> c : cats) {
                if (normName((String) c.get("descr")).equals(myName)) {
                    headCode = (String) c.get("code");
                    break;
                }
            }
            if (headCode == null) return Collections.emptyList();   // I head no team → not a manager.

            // 3) Active members carrying that C4 code, excluding myself.
            return esoftJdbc.queryForList(
                "SELECT invservemployee_code AS esoft_code, invservemployee_name AS employee_name " +
                "FROM dbo.invservemployees " +
                "WHERE invservemployee_category4 = ? AND invservemployee_inactive = 0 " +
                "  AND invservemployee_code <> ? " +
                "ORDER BY invservemployee_name",
                headCode, managerCode);
        } catch (DataAccessException e) {
            // eSoft unreachable at request time → degrade to "no team" rather than failing the page.
            log.warn("Live eSoft supervisor lookup failed for {}: {}", managerCode, e.getMessage());
            return Collections.emptyList();
        }
    }

    /** Normalise a name for order/spacing/punctuation-insensitive matching (tokens sorted, upper-cased). */
    private static String normName(String n) {
        if (n == null) return "";
        String[] toks = n.toUpperCase().replaceAll("[^A-Z ]", " ").trim().split("\\s+");
        if (toks.length == 1 && toks[0].isEmpty()) return "";
        java.util.Arrays.sort(toks);
        return String.join(" ", toks);
    }

    /** Direct reports of a manager (by manager name, from the level snapshot). */
    public List<Map<String, Object>> findDirectReports(String managerName) {
        return jdbc.queryForList("""
            SELECT el.esoft_code           AS esoft_code,
                   e.employee_name         AS employee_name,
                   el.level                AS level,
                   COALESCE(el.target_hrs_month, lt.target_hrs_month) AS target_hrs_month,
                   COALESCE(el.target_hrs_week,  lt.target_hrs_week)  AS target_hrs_week,
                   el.location             AS location,
                   e.email                 AS azure_email
            FROM   dbo.employee_levels el
            JOIN   dbo.esoft_employees e  ON e.employee_code = el.esoft_code
            LEFT JOIN dbo.level_targets lt ON lt.level = el.level
            WHERE  el.manager_name = ?
            ORDER  BY e.employee_name
            """, managerName);
    }

    public List<Map<String, Object>> findHoursByCompany(String esoftCode, LocalDate start, LocalDate end) {
        return jdbc.queryForList("""
            SELECT
                jc.account_name                 AS company,
                SUM(tl.total_week_hours)        AS hours
            FROM dbo.esoft_timesheets tl
            JOIN dbo.esoft_workcodes w
                ON w.work_code = tl.work_code
            JOIN dbo.esoft_jobcards jc
                ON jc.jobcard = tl.jobcard
            WHERE tl.employee_code = ?
              AND tl.ts_date >= ?
              AND tl.ts_date <  ?
              AND w.not_chargeable = 0
              AND jc.h3_department != 'K'
            GROUP BY jc.account_name
            ORDER BY SUM(tl.total_week_hours) DESC
            """, esoftCode, start, end);
    }

    /** All ACTIVE employees for the dropdown — now the full live roster (fixes the stale seed). */
    public List<Map<String, Object>> findAllEmployees() {
        return jdbc.queryForList("""
            SELECT employee_code AS esoft_code, employee_name
            FROM   dbo.esoft_employees
            WHERE  inactive = 0
            ORDER  BY employee_name
            """);
    }
}
