package com.treppides.taskmanager.repositories;

import org.springframework.beans.factory.annotation.Qualifier;
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

    private final JdbcTemplate internalToolsJdbc;
    private final JdbcTemplate esoftJdbc;
    private final NamedParameterJdbcTemplate esoftNamedJdbc;

    public PerformanceRepository(
            JdbcTemplate jdbcTemplate,
            @Qualifier("esoftJdbcTemplate") JdbcTemplate esoftJdbcTemplate) {
        this.internalToolsJdbc = jdbcTemplate;
        this.esoftJdbc = esoftJdbcTemplate;
        this.esoftNamedJdbc = new NamedParameterJdbcTemplate(esoftJdbcTemplate.getDataSource());
    }

    public Optional<Map<String, Object>> findTargetByAzureEmail(String azureEmail) {
        List<Map<String, Object>> rows = internalToolsJdbc.queryForList("""
            SELECT esoft_code, employee_name, level, target_hrs_month, target_hrs_week,
                   location, manager_name, azure_email
            FROM   dbo.performance_targets
            WHERE  azure_email = ?
            """, azureEmail);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
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
            WHERE tl.invservtimesheetln_employee_code IN (:codes)
              AND tl.invservtimesheetln_date >= :start
              AND tl.invservtimesheetln_date <  :end
              AND e.invservemployee_inactive = 0
            GROUP BY
                tl.invservtimesheetln_employee_code,
                e.invservemployee_wrk_units_total
            """, params);
    }

    public List<Map<String, Object>> findDirectReports(String managerName) {
        return internalToolsJdbc.queryForList("""
            SELECT esoft_code, employee_name, level, target_hrs_month, target_hrs_week,
                   location, azure_email
            FROM   dbo.performance_targets
            WHERE  manager_name = ?
            ORDER  BY employee_name
            """, managerName);
    }

    public List<Map<String, Object>> findAllEmployees() {
        return internalToolsJdbc.queryForList("""
            SELECT esoft_code, employee_name
            FROM   dbo.performance_targets
            ORDER  BY employee_name
            """);
    }
}
