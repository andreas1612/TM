package com.treppides.taskmanager.repositories;

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

    // Migrated to the InternalTools datamart: invoiced amounts now read the
    // esoft_invoices mirror (loaded nightly by EsoftSyncService) instead of
    // querying eSoft live. eSoft is no longer touched at runtime by this repo.
    private final JdbcTemplate internalToolsJdbc;
    private final NamedParameterJdbcTemplate internalToolsNamedJdbc;

    public BudgetRepository(JdbcTemplate jdbcTemplate) {
        this.internalToolsJdbc = jdbcTemplate;
        this.internalToolsNamedJdbc = new NamedParameterJdbcTemplate(jdbcTemplate.getDataSource());
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

    /**
     * PBI's computed Value formula (Power Query DAX):
     *   sign = if(doctype='SRE', -1, 1)
     *   base = (docval - docvat) * currency_rate
     *   EK001 + 'Finanz-Audit Limited'      → base / 0.7
     *   EK001 + 'TREPPIDES ADVISERS LIMITED' → base / 0.3
     *   else                                 → base
     *   Value = sign * above
     */
    private static final String PBI_VALUE = """
            CASE WHEN doctype = 'SRE' THEN -1 ELSE 1 END
            * CASE
                WHEN h4_el = 'EK001' AND account_name = 'Finanz-Audit Limited'
                  THEN (docval - docvat) * currency_rate / 0.7
                WHEN h4_el = 'EK001' AND account_name = 'TREPPIDES ADVISERS LIMITED'
                  THEN (docval - docvat) * currency_rate / 0.3
                ELSE (docval - docvat) * currency_rate
              END""";

    /** Actual invoiced amounts from the datamart mirror, grouped by month — matches PBI Value formula. */
    public List<Map<String, Object>> findMonthlyInvoiced(String invoiceCode, int year) {
        if (invoiceCode == null) return Collections.emptyList();
        return internalToolsJdbc.queryForList(
            "SELECT period AS month_num, SUM(" + PBI_VALUE + ") AS invoiced"
            + " FROM dbo.esoft_invoices"
            + " WHERE h4_el = ? AND year = ? AND status != 'C'"
            + " GROUP BY period"
            + " ORDER BY period",
            invoiceCode, year);
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
        return internalToolsNamedJdbc.queryForList(
            "SELECT h4_el AS invoice_code, period AS month_num, SUM(" + PBI_VALUE + ") AS invoiced"
            + " FROM dbo.esoft_invoices"
            + " WHERE h4_el IN (:codes) AND year = :year AND status != 'C'"
            + " GROUP BY h4_el, period",
            params);
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

    /** Individual invoice lines for debugging — shows every document with all relevant fields. */
    public List<Map<String, Object>> findInvoiceDetails(String invoiceCode, int year) {
        if (invoiceCode == null) return Collections.emptyList();
        return internalToolsJdbc.queryForList(
            "SELECT docno AS invoice_no,"
            + " period AS month_num,"
            + " docdate AS doc_date,"
            + " account_name AS client,"
            + " docval AS gross,"
            + " docvat AS vat,"
            + " docval - docvat AS net,"
            + " " + PBI_VALUE + " AS pbi_value,"
            + " doctype AS doc_type,"
            + " sign AS sign,"
            + " currency_rate AS currency_rate,"
            + " details AS description"
            + " FROM dbo.esoft_invoices"
            + " WHERE h4_el = ? AND year = ? AND status != 'C'"
            + " ORDER BY period, docdate, docno",
            invoiceCode, year);
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
