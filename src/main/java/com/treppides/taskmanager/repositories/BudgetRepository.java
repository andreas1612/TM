package com.treppides.taskmanager.repositories;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class BudgetRepository {

    private final JdbcTemplate internalToolsJdbc;
    private final JdbcTemplate esoftJdbc;
    private final NamedParameterJdbcTemplate esoftNamedJdbc;

    public BudgetRepository(
            JdbcTemplate jdbcTemplate,
            @Qualifier("esoftJdbcTemplate") JdbcTemplate esoftJdbcTemplate) {
        this.internalToolsJdbc = jdbcTemplate;
        this.esoftJdbc = esoftJdbcTemplate;
        this.esoftNamedJdbc = new NamedParameterJdbcTemplate(esoftJdbcTemplate.getDataSource());
    }

    /** Find budget entry for a manager by eSoft code (resolves invoice_code). */
    public Optional<Map<String, Object>> findBudgetByEsoftCode(String esoftCode, int year) {
        List<Map<String, Object>> rows = internalToolsJdbc.queryForList("""
            SELECT TOP 1 esoft_code, manager_name, invoice_code, department,
                   department_code, el_name, team
            FROM   dbo.budget_per_manager
            WHERE  esoft_code = ? AND year = ?
            """, esoftCode, year);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /** Monthly budget targets for a manager in a given year. */
    public List<Map<String, Object>> findMonthlyBudgets(String invoiceCode, int year) {
        if (invoiceCode == null) return Collections.emptyList();
        return internalToolsJdbc.queryForList("""
            SELECT month_num, budget
            FROM   dbo.budget_per_manager
            WHERE  invoice_code = ? AND year = ?
            ORDER  BY month_num
            """, invoiceCode, year);
    }

    /** Actual invoiced amounts from eSoft, grouped by month. */
    public List<Map<String, Object>> findMonthlyInvoiced(String invoiceCode, int year) {
        if (invoiceCode == null) return Collections.emptyList();
        return esoftJdbc.queryForList("""
            SELECT invsavehd_period  AS month_num,
                   SUM(invsavehd_docval - invsavehd_docvat) AS invoiced
            FROM   dbo.invsaveheaders
            WHERE  invsavehd_H4 = ?
              AND  invsavehd_year = ?
            GROUP  BY invsavehd_period
            ORDER  BY invsavehd_period
            """, invoiceCode, year);
    }

    /** All managers in the same EL group for a given year. */
    public List<Map<String, Object>> findManagersByEl(String elName, int year) {
        return internalToolsJdbc.queryForList("""
            SELECT DISTINCT invoice_code, manager_name
            FROM   dbo.budget_per_manager
            WHERE  el_name = ? AND year = ? AND invoice_code IS NOT NULL
            """, elName, year);
    }

    /** All managers in the same department for a given year. */
    public List<Map<String, Object>> findManagersByDepartment(String department, int year) {
        return internalToolsJdbc.queryForList("""
            SELECT DISTINCT invoice_code, manager_name
            FROM   dbo.budget_per_manager
            WHERE  department = ? AND year = ? AND invoice_code IS NOT NULL
            """, department, year);
    }

    /** Bulk invoiced amounts for multiple managers. */
    public List<Map<String, Object>> findBulkInvoiced(List<String> invoiceCodes, int year) {
        if (invoiceCodes == null || invoiceCodes.isEmpty()) return Collections.emptyList();
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("codes", invoiceCodes)
            .addValue("year", year);
        return esoftNamedJdbc.queryForList("""
            SELECT invsavehd_H4        AS invoice_code,
                   invsavehd_period     AS month_num,
                   SUM(invsavehd_docval - invsavehd_docvat) AS invoiced
            FROM   dbo.invsaveheaders
            WHERE  invsavehd_H4 IN (:codes)
              AND  invsavehd_year = :year
            GROUP  BY invsavehd_H4, invsavehd_period
            """, params);
    }

    /** Find budget entry for a manager by invoice code (admin use — always populated). */
    public Optional<Map<String, Object>> findBudgetByInvoiceCode(String invoiceCode, int year) {
        List<Map<String, Object>> rows = internalToolsJdbc.queryForList("""
            SELECT TOP 1 esoft_code, manager_name, invoice_code, department,
                   department_code, el_name, team
            FROM   dbo.budget_per_manager
            WHERE  invoice_code = ? AND year = ?
            """, invoiceCode, year);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /** All budget entries (for listing available managers). */
    public List<Map<String, Object>> findAllBudgetManagers(int year) {
        return internalToolsJdbc.queryForList("""
            SELECT DISTINCT esoft_code, manager_name, invoice_code, department, el_name, team
            FROM   dbo.budget_per_manager
            WHERE  year = ?
            ORDER  BY manager_name
            """, year);
    }
}
