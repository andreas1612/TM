package com.treppides.taskmanager.repositories;

import com.treppides.taskmanager.services.InMemoryTargetsProvider;
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

@Repository
public class PerformanceRepository {

    private static final Logger log = LoggerFactory.getLogger(PerformanceRepository.class);

    private final JdbcTemplate internalToolsJdbc;
    private final JdbcTemplate esoftJdbc;
    private final NamedParameterJdbcTemplate esoftNamedJdbc;
    private final InMemoryTargetsProvider fallback;

    public PerformanceRepository(
            JdbcTemplate jdbcTemplate,
            @Qualifier("esoftJdbcTemplate") JdbcTemplate esoftJdbcTemplate,
            InMemoryTargetsProvider fallback) {
        this.internalToolsJdbc = jdbcTemplate;
        this.esoftJdbc = esoftJdbcTemplate;
        this.esoftNamedJdbc = new NamedParameterJdbcTemplate(esoftJdbcTemplate.getDataSource());
        this.fallback = fallback;
    }

    /**
     * Resolve an email address to an eSoft employee code.
     * Matches against invservemployee_email (the @treppides.com format
     * which is what Azure AD preferred_username returns).
     */
    public Optional<String> findCodeByEmail(String email) {
        List<Map<String, Object>> rows = esoftJdbc.queryForList("""
            SELECT invservemployee_code
            FROM   dbo.invservemployees
            WHERE  invservemployee_email = ?
              AND  invservemployee_inactive = 0
            """, email);
        return rows.isEmpty() ? Optional.empty()
            : Optional.of((String) rows.get(0).get("invservemployee_code"));
    }

    public Optional<Map<String, Object>> findTargetByCode(String esoftCode) {
        try {
            List<Map<String, Object>> rows = internalToolsJdbc.queryForList("""
                SELECT esoft_code, employee_name, level, target_hrs_month, target_hrs_week,
                       location, manager_name, azure_email
                FROM   dbo.performance_targets
                WHERE  esoft_code = ?
                """, esoftCode);
            if (!rows.isEmpty()) return Optional.of(rows.get(0));
        } catch (DataAccessException e) {
            log.debug("performance_targets table unavailable, using in-memory fallback: {}", e.getMessage());
        }
        return fallback.findByCode(esoftCode);
    }

    public Optional<Map<String, Object>> findActualHours(String esoftCode, LocalDate start, LocalDate end) {
        List<Map<String, Object>> rows = esoftJdbc.queryForList("""
            SELECT
                SUM(tl.invservtimesheetln_total_week_hours)   AS actual_hrs,
                e.invservemployee_wrk_units_total              AS available_hrs_week,
                c1.invservemployeecategory_description         AS job_title,
                c2.invservemployeecategory_description         AS engagement_leader,
                c3.invservemployeecategory_description         AS team_name
            FROM dbo.invservtimesheetlines tl
            JOIN dbo.invservemployees e
                ON e.invservemployee_code = tl.invservtimesheetln_employee_code
            JOIN dbo.invservwork w
                ON w.invservwork_code = tl.invservtimesheetln_work_code
            JOIN dbo.soporderheader jc
                ON jc.sophorder_order = tl.invservtimesheetln_jobcard
            LEFT JOIN dbo.invservemployeecategories c1
                ON c1.invservemployeecategory_code = e.invservemployee_category1
               AND c1.invservemployeecategory_head = 'C1'
            LEFT JOIN dbo.invservemployeecategories c2
                ON c2.invservemployeecategory_code = e.invservemployee_category2
               AND c2.invservemployeecategory_head = 'C2'
            LEFT JOIN dbo.invservemployeecategories c3
                ON c3.invservemployeecategory_code = e.invservemployee_category3
               AND c3.invservemployeecategory_head = 'C3'
            WHERE tl.invservtimesheetln_employee_code = ?
              AND tl.invservtimesheetln_date >= ?
              AND tl.invservtimesheetln_date <  ?
              AND e.invservemployee_inactive = 0
              AND w.invservwork_notChargeable = 0
              AND jc.sophorder_H3 != 'K'
            GROUP BY
                e.invservemployee_wrk_units_total,
                c1.invservemployeecategory_description,
                c2.invservemployeecategory_description,
                c3.invservemployeecategory_description
            """, esoftCode, start, end);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public List<Map<String, Object>> findTeamActualHours(List<String> codes, LocalDate start, LocalDate end) {
        if (codes == null || codes.isEmpty()) return Collections.emptyList();
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("codes", codes)
            .addValue("start", start)
            .addValue("end", end);
        return esoftNamedJdbc.queryForList("""
            SELECT
                tl.invservtimesheetln_employee_code            AS esoft_code,
                SUM(tl.invservtimesheetln_total_week_hours)    AS actual_hrs,
                e.invservemployee_wrk_units_total              AS available_hrs_week
            FROM dbo.invservtimesheetlines tl
            JOIN dbo.invservemployees e
                ON e.invservemployee_code = tl.invservtimesheetln_employee_code
            JOIN dbo.invservwork w
                ON w.invservwork_code = tl.invservtimesheetln_work_code
            JOIN dbo.soporderheader jc
                ON jc.sophorder_order = tl.invservtimesheetln_jobcard
            WHERE tl.invservtimesheetln_employee_code IN (:codes)
              AND tl.invservtimesheetln_date >= :start
              AND tl.invservtimesheetln_date <  :end
              AND e.invservemployee_inactive = 0
              AND w.invservwork_notChargeable = 0
              AND jc.sophorder_H3 != 'K'
            GROUP BY
                tl.invservtimesheetln_employee_code,
                e.invservemployee_wrk_units_total
            """, params);
    }

    public List<Map<String, Object>> findDirectReports(String managerName) {
        try {
            List<Map<String, Object>> rows = internalToolsJdbc.queryForList("""
                SELECT esoft_code, employee_name, level, target_hrs_month, target_hrs_week,
                       location, azure_email
                FROM   dbo.performance_targets
                WHERE  manager_name = ?
                ORDER  BY employee_name
                """, managerName);
            if (!rows.isEmpty()) return rows;
        } catch (DataAccessException e) {
            log.debug("performance_targets table unavailable, using in-memory fallback");
        }
        return fallback.findByManager(managerName);
    }

    public List<Map<String, Object>> findHoursByCompany(String esoftCode, LocalDate start, LocalDate end) {
        return esoftJdbc.queryForList("""
            SELECT
                jc.sophorder_account_name                      AS company,
                SUM(tl.invservtimesheetln_total_week_hours)     AS hours
            FROM dbo.invservtimesheetlines tl
            JOIN dbo.invservwork w
                ON w.invservwork_code = tl.invservtimesheetln_work_code
            JOIN dbo.soporderheader jc
                ON jc.sophorder_order = tl.invservtimesheetln_jobcard
            WHERE tl.invservtimesheetln_employee_code = ?
              AND tl.invservtimesheetln_date >= ?
              AND tl.invservtimesheetln_date <  ?
              AND w.invservwork_notChargeable = 0
              AND jc.sophorder_H3 != 'K'
            GROUP BY jc.sophorder_account_name
            ORDER BY SUM(tl.invservtimesheetln_total_week_hours) DESC
            """, esoftCode, start, end);
    }

    public List<Map<String, Object>> findAllEmployees() {
        try {
            List<Map<String, Object>> rows = internalToolsJdbc.queryForList("""
                SELECT esoft_code, employee_name
                FROM   dbo.performance_targets
                ORDER  BY employee_name
                """);
            if (!rows.isEmpty()) return rows;
        } catch (DataAccessException e) {
            log.debug("performance_targets table unavailable, using in-memory fallback");
        }
        return fallback.findAll();
    }
}
