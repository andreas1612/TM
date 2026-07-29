package com.treppides.taskmanager.repositories;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Financials datamart queries — reproduces the KT Financials v7 Power BI model
 * from the InternalTools eSoft mirror (esoft_invoices, esoft_budget, esoft_balances,
 * esoft_accounts, esoft_timesheets, esoft_jobcards). eSoft is touched only by the
 * sync job; this repo reads the mirror exclusively. See financials/KT_Financials_v7_Audit.md.
 *
 * Net revenue = SUM(docval - docvat) on posted invoices (status='P') — the PBI "Net amount".
 * Analysis decode (esoft_analysis_codes, comp-scoped): A1=Director(H2), A2=Department(H3), A3=EL(H4).
 */
@Repository
public class FinancialsRepository {

    private final JdbcTemplate jdbc;   // InternalTools datamart

    public FinancialsRepository(JdbcTemplate jdbcTemplate) {
        this.jdbc = jdbcTemplate;
    }

    private static final String NET = "SUM(i.docval - i.docvat)";

    // Department grouping: merge related eSoft department codes into business divisions.
    // L=Accounting CIFs + Z=Payroll → FCR | O=Internal Audit + J=Licensing + H=Compliance + E=Risk → ICAS
    // D=VAT + P=Tax → VAT and TAX | everything else keeps its own name.
    private static final String DEPT_GROUP_CODE =
        "CASE WHEN %s IN ('L','Z') THEN 'FCR'"
      + " WHEN %s IN ('O','J','H','E') THEN 'ICAS'"
      + " WHEN %s IN ('D','P') THEN 'VAT_TAX'"
      + " ELSE %s END";

    /** Maps a raw department column to the grouped code (FCR / ICAS / VAT_TAX / original). */
    private static String deptGroupCode(String col) {
        return String.format(DEPT_GROUP_CODE, col, col, col, col);
    }

    /** Posted-invoice WHERE clause; year/company/department(H3)/el(H4) all optional → enables cross-filtering. */
    private static String filter(Integer year, String company, String dept, String el, List<Object> args) {
        StringBuilder w = new StringBuilder(" WHERE i.status = 'P'");
        if (year != null)                          { w.append(" AND i.[year] = ?");        args.add(year); }
        if (company != null && !company.isBlank()) { w.append(" AND i.comp = ?");          args.add(company); }
        if (dept != null && !dept.isBlank())       { w.append(" AND i.h3_department = ?"); args.add(dept); }
        if (el != null && !el.isBlank())           { w.append(" AND i.h4_el = ?");         args.add(el); }
        return w.toString();
    }

    /** Net revenue per year (every year present), newest first. */
    public List<Map<String, Object>> revenueByYear(String company, String dept, String el) {
        List<Object> a = new ArrayList<>();
        String w = filter(null, company, dept, el, a);
        return jdbc.queryForList(
            "SELECT i.[year] AS year, " + NET + " AS net, COUNT(*) AS invoices"
          + " FROM dbo.esoft_invoices i" + w
          + " GROUP BY i.[year] ORDER BY i.[year] DESC", a.toArray());
    }

    /** Net revenue per department group (merged: FCR, ICAS, VAT and TAX) for a year.
     *  Excludes invoices with NULL h3_department (subsidiary companies with no analysis codes)
     *  so only classified revenue appears in the breakdown — totals still include everything. */
    public List<Map<String, Object>> revenueByDepartment(int year, String company, String dept, String el) {
        List<Object> a = new ArrayList<>();
        String w = filter(year, company, dept, el, a);
        String grpCode = deptGroupCode("i.h3_department");
        // Subquery: map raw dept code → group + keep analysis name; outer groups by mapped code.
        return jdbc.queryForList(
            "SELECT code,"
          + " CASE WHEN code = 'FCR' THEN 'FCR'"
          + "      WHEN code = 'ICAS' THEN 'ICAS'"
          + "      WHEN code = 'VAT_TAX' THEN 'VAT and TAX'"
          + "      ELSE MAX(raw_name) END AS name,"
          + " SUM(net) AS net"
          + " FROM (SELECT " + grpCode + " AS code, d.description AS raw_name,"
          + "   (i.docval - i.docvat) AS net"
          + "   FROM dbo.esoft_invoices i"
          + "   LEFT JOIN dbo.esoft_analysis_codes d ON d.head = 'A2' AND d.comp = i.comp AND d.code = i.h3_department"
          + w + " AND i.h3_department IS NOT NULL) sub"
          + " GROUP BY code ORDER BY net DESC", a.toArray());
    }

    /** Net revenue per Engagement Leader (H2 → analysis head A1 = Director/Partner).
     *  Excludes invoices with NULL h2_director (subsidiary companies with no analysis codes)
     *  so only classified revenue appears in the breakdown — totals still include everything. */
    public List<Map<String, Object>> revenueByEl(int year, String company, String dept, String el) {
        List<Object> a = new ArrayList<>();
        String w = filter(year, company, dept, el, a);
        return jdbc.queryForList(
            "SELECT i.h2_director AS code,"
          + " MAX(d.description) AS name,"
          + " " + NET + " AS net"
          + " FROM dbo.esoft_invoices i"
          + " LEFT JOIN dbo.esoft_analysis_codes d ON d.head = 'A1' AND d.comp = i.comp AND d.code = i.h2_director"
          + w + " AND i.h2_director IS NOT NULL"
          + " GROUP BY i.h2_director ORDER BY net DESC", a.toArray());
    }

    /** Net revenue per month (period 1-12) for a year. */
    public List<Map<String, Object>> revenueByMonth(int year, String company, String dept, String el) {
        List<Object> a = new ArrayList<>();
        String w = filter(year, company, dept, el, a);
        return jdbc.queryForList(
            "SELECT i.period AS month, " + NET + " AS net"
          + " FROM dbo.esoft_invoices i" + w
          + " GROUP BY i.period ORDER BY i.period", a.toArray());
    }

    /** Top N clients by net revenue for a year. */
    public List<Map<String, Object>> topClients(int year, String company, String dept, String el, int top) {
        List<Object> a = new ArrayList<>();
        a.add(top);
        String w = filter(year, company, dept, el, a);
        return jdbc.queryForList(
            "SELECT TOP (?) i.account_name AS client, " + NET + " AS net, COUNT(*) AS invoices"
          + " FROM dbo.esoft_invoices i" + w
          + " GROUP BY i.account_name ORDER BY net DESC", a.toArray());
    }

    /** Invoiced (net) vs receipts per month (1-12) for a year — matches the PBI "Revenue by month". */
    public List<Map<String, Object>> invoicedVsReceiptsByMonth(int year, String company) {
        List<Object> a = new ArrayList<>();
        String invComp = "", recComp = "";
        if (company != null && !company.isBlank()) { invComp = " AND comp = ?"; recComp = " AND comp = ?"; }
        a.add(year); if (!invComp.isEmpty()) a.add(company);
        a.add(year); if (!recComp.isEmpty()) a.add(company);
        return jdbc.queryForList(
            "SELECT mn.m AS month, ISNULL(inv.invoiced, 0) AS invoiced, ISNULL(rec.receipts, 0) AS receipts"
          + " FROM (VALUES (1),(2),(3),(4),(5),(6),(7),(8),(9),(10),(11),(12)) mn(m)"
          + " LEFT JOIN (SELECT period m, SUM(docval - docvat) invoiced FROM dbo.esoft_invoices"
          + "   WHERE status = 'P' AND [year] = ?" + invComp + " GROUP BY period) inv ON inv.m = mn.m"
          + " LEFT JOIN (SELECT MONTH(rec_date) m, SUM(base_amount) receipts FROM dbo.esoft_receipts"
          + "   WHERE post_year = ?" + recComp + " GROUP BY MONTH(rec_date)) rec ON rec.m = mn.m"
          + " ORDER BY mn.m", a.toArray());
    }

    /** Distinct companies present in posted invoices (for the company slicer). */
    public List<String> companies() {
        return jdbc.queryForList(
            "SELECT DISTINCT comp FROM dbo.esoft_invoices WHERE status = 'P' AND comp IS NOT NULL ORDER BY comp",
            String.class);
    }

    // ---- Budget vs Actual (per Department / service) ----------------------------
    // eSoft stores budgets per GL account with a SIGNED amount (revenue = credit/negative,
    // costs = positive). The PBI "Budget per EL/service" = the revenue budget only, sign-flipped:
    //   budget = -SUM(amount) over the credit (revenue) lines.   [reproduces PBI to ~0.01%]
    // The budget's a2 code is the DEPARTMENT letter (B=Audit, P=Tax, A=FRA, K=Admin, ...),
    // which matches the invoice H3 department code; decode via analysis head A2.

    public List<Map<String, Object>> budgetVsActualByDirector(int year, String company) {
        List<Object> a = new ArrayList<>();
        String actualComp = "";
        if (company != null && !company.isBlank()) { actualComp = " AND comp = ?"; }
        // Budget table has no comp column (firm-level); only actuals (invoices) filter by company.
        // Both sides group by the department mapping so FCR/ICAS/VAT_TAX aggregate correctly.
        String budGrp = deptGroupCode("a2_director");
        String actGrp = deptGroupCode("h3_department");
        // Name: merged groups get a fixed label; un-merged fall back to analysis_codes.
        String nameExpr = "CASE WHEN COALESCE(b.code, a.code) = 'FCR' THEN 'FCR'"
                        + " WHEN COALESCE(b.code, a.code) = 'ICAS' THEN 'ICAS'"
                        + " WHEN COALESCE(b.code, a.code) = 'VAT_TAX' THEN 'VAT and TAX'"
                        + " ELSE d.description END";
        a.add(year);
        a.add(year); if (!actualComp.isEmpty()) a.add(company);
        return jdbc.queryForList(
            "SELECT COALESCE(b.code, a.code) AS code,"
          + "       " + nameExpr + " AS name,"
          + "       ISNULL(b.budget, 0) AS budget,"
          + "       ISNULL(a.actual, 0) AS actual"
          + " FROM (SELECT " + budGrp + " AS code, -SUM(CASE WHEN amount < 0 THEN amount ELSE 0 END) AS budget"
          + "         FROM dbo.esoft_budget WHERE [year] = ? GROUP BY " + budGrp + ") b"
          + " FULL OUTER JOIN (SELECT " + actGrp + " AS code, SUM(docval - docvat) AS actual"
          + "         FROM dbo.esoft_invoices WHERE status = 'P' AND [year] = ?" + actualComp + " GROUP BY " + actGrp + ") a"
          + "   ON a.code = b.code"
          + " LEFT JOIN dbo.esoft_analysis_codes d ON d.head = 'A2' AND d.comp = 'TRE' AND d.code = COALESCE(b.code, a.code)"
          + " WHERE ISNULL(b.budget, 0) <> 0 OR ISNULL(a.actual, 0) <> 0"
          + " ORDER BY budget DESC",
            a.toArray());
    }

    // ---- Invoice listing (drill-down data) ----------------------------------------
    // Returns individual posted invoice rows for the UI detail table.
    // Department group mapping applied so rows show the merged group name.

    public List<Map<String, Object>> invoiceList(int year, String company, String dept, String el, int top) {
        List<Object> a = new ArrayList<>();
        a.add(top);
        String w = filter(year, company, dept, el, a);
        String grpCode = deptGroupCode("i.h3_department");
        String grpName = "CASE WHEN i.h3_department IN ('L','Z') THEN 'FCR'"
                       + " WHEN i.h3_department IN ('O','J','H','E') THEN 'ICAS'"
                       + " WHEN i.h3_department IN ('D','P') THEN 'VAT and TAX'"
                       + " ELSE d.description END";
        return jdbc.queryForList(
            "SELECT TOP (?) i.docno, i.account_name AS client, i.comp AS company,"
          + " i.docdate, i.period AS month, i.[year],"
          + " i.docval AS gross, i.docvat AS vat, (i.docval - i.docvat) AS net,"
          + " i.doctype, " + grpCode + " AS deptCode, " + grpName + " AS department,"
          + " i.h4_el AS elCode, el.description AS elName, i.[user]"
          + " FROM dbo.esoft_invoices i"
          + " LEFT JOIN dbo.esoft_analysis_codes d ON d.head = 'A2' AND d.comp = i.comp AND d.code = i.h3_department"
          + " LEFT JOIN dbo.esoft_analysis_codes el ON el.head = 'A3' AND el.comp = i.comp AND el.code = i.h4_el"
          + w
          + " ORDER BY i.docdate DESC", a.toArray());
    }

    // ---- Recoverability (per job card) ------------------------------------------
    // charged = SUM(timesheet total_amount); cost = SUM(employee_cost);
    // recoverability = charged / cost. Job budget + client from esoft_jobcards.

    public List<Map<String, Object>> recoverability(Integer year, int top, boolean bottom, String company) {
        List<Object> a = new ArrayList<>();
        a.add(top);
        String yearClause = "";
        String compClause = "";
        if (year != null) { yearClause = " AND YEAR(t.ts_date) = ?"; }
        if (company != null && !company.isBlank()) { compClause = " AND j.comp = ?"; }
        // args: TOP first, then optional year, then optional company
        StringBuilder sql = new StringBuilder()
            .append("SELECT TOP (?) t.jobcard AS jobcard,")
            .append("       MAX(j.account_name) AS client,")
            .append("       MAX(j.budget_money1) AS budget,")
            .append("       SUM(t.total_amount) AS charged,")
            .append("       SUM(t.employee_cost) AS cost,")
            .append("       CASE WHEN SUM(t.employee_cost) = 0 THEN NULL")
            .append("            ELSE SUM(t.total_amount) / SUM(t.employee_cost) END AS recoverability")
            .append(" FROM dbo.esoft_timesheets t")
            .append(" LEFT JOIN dbo.esoft_jobcards j ON j.jobcard = t.jobcard")
            // Real client jobs only: 'JC%' excludes NC*/NP* (non-chargeable / non-productive
            // internal buckets); ABS(...) < 1,000,000 drops garbage eSoft rows (e.g. the ±€50bn
            // fat-finger lines) that no single timesheet line could legitimately hold.
            .append(" WHERE t.jobcard LIKE 'JC%' AND ABS(t.total_amount) < 1000000").append(yearClause).append(compClause)
            .append(" GROUP BY t.jobcard")
            .append(" HAVING SUM(t.employee_cost) > 0")
            .append(" ORDER BY recoverability ").append(bottom ? "ASC" : "DESC");
        if (year != null) a.add(year);
        if (company != null && !company.isBlank()) a.add(company);
        return jdbc.queryForList(sql.toString(), a.toArray());
    }

    // ---- Debtors (outstanding balances) -----------------------------------------
    // PBI method: per client, net invoiced (posted) minus receipts allocated.
    // Invoices key on account_seq; receipts key on payer_acc (same client account id).
    // Positive balance = the client owes us. Matches the .pbix "Balance" column.

    // ---- Debtors (GL-balance approach) -------------------------------------------
    // The old receipt-based approach was broken: esoft_receipts only had ~1,700 rows
    // covering 2023-2024, so 2025-2026 invoices showed 100% outstanding (EUR 29M fake).
    //
    // Correct approach: use the GL account balance from esoft_balances for each client
    // that has posted invoices. Balance = opening + debits - credits for the latest
    // period. Inter-company Treppides group entities are excluded.
    //
    // The balance per client account_seq already reflects payments, credit notes, and
    // write-offs — it IS the debtor figure.

    private static final String INTERCO_EXCLUSION =
        " AND UPPER(a.account_name) NOT LIKE '%TREPPIDES%'"
      + " AND UPPER(a.account_name) NOT LIKE '%FINANZ%AUDIT%'";

    public List<Map<String, Object>> topDebtors(int top, String company) {
        List<Object> a = new ArrayList<>();
        a.add(top);
        String compClause = "";
        if (company != null && !company.isBlank()) { compClause = " AND a.comp = ?"; a.add(company); }
        return jdbc.queryForList(
            "SELECT TOP (?) client, balance FROM ("
          + "  SELECT a.account_name AS client,"
          + "         SUM(b.opening_balance + b.debits - b.credits) AS balance"
          + "  FROM dbo.esoft_balances b"
          + "  JOIN dbo.esoft_accounts a ON a.account_seq = b.account_seq"
          + "  WHERE b.account_seq IN (SELECT DISTINCT account_seq FROM dbo.esoft_invoices WHERE status = 'P')"
          + "    AND b.[year] = YEAR(GETDATE()) AND b.period = MONTH(GETDATE())"
          + compClause
          + INTERCO_EXCLUSION
          + "  GROUP BY a.account_name"
          + "  HAVING SUM(b.opening_balance + b.debits - b.credits) > 0"
          + ") t ORDER BY balance DESC",
            a.toArray());
    }

    /** Firm-wide total outstanding debtors (sum of positive client GL balances). */
    public Double totalDebtors(String company) {
        List<Object> a = new ArrayList<>();
        String compClause = "";
        if (company != null && !company.isBlank()) { compClause = " AND a.comp = ?"; a.add(company); }
        return jdbc.queryForObject(
            "SELECT ISNULL(SUM(balance), 0) FROM ("
          + "  SELECT SUM(b.opening_balance + b.debits - b.credits) AS balance"
          + "  FROM dbo.esoft_balances b"
          + "  JOIN dbo.esoft_accounts a ON a.account_seq = b.account_seq"
          + "  WHERE b.account_seq IN (SELECT DISTINCT account_seq FROM dbo.esoft_invoices WHERE status = 'P')"
          + "    AND b.[year] = YEAR(GETDATE()) AND b.period = MONTH(GETDATE())"
          + compClause
          + INTERCO_EXCLUSION
          + "  GROUP BY a.account_name"
          + "  HAVING SUM(b.opening_balance + b.debits - b.credits) > 0"
          + ") t",
            Double.class, a.toArray());
    }
}
