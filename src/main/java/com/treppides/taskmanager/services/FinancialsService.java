package com.treppides.taskmanager.services;

import com.treppides.taskmanager.repositories.FinancialsRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Assembles the Financials report payloads from {@link FinancialsRepository}.
 * Access is enforced in the controller via AccessScopeResolver (board/admin only
 * for v1 — unrestricted view). eSoft is never touched here; all reads hit the datamart.
 */
@Service
public class FinancialsService {

    private final FinancialsRepository repo;

    public FinancialsService(FinancialsRepository repo) {
        this.repo = repo;
    }

    /** Revenue page: headline + per-year, per-department, per-EL, per-month, top clients.
     *  Optional dept (H3) / el (H4) filters enable Power-BI-style cross-filtering. */
    public Map<String, Object> revenue(int year, String company, String dept, String el, int topN) {
        List<Map<String, Object>> byMonth = repo.revenueByMonth(year, company, dept, el);
        double total = byMonth.stream()
            .map(m -> m.get("net"))
            .filter(v -> v instanceof Number)
            .mapToDouble(v -> ((Number) v).doubleValue())
            .sum();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("year", year);
        out.put("company", company);
        out.put("department", dept);
        out.put("el", el);
        out.put("totalNet", total);
        out.put("byYear", repo.revenueByYear(company, dept, el));
        out.put("byDepartment", repo.revenueByDepartment(year, company, dept, el));
        out.put("byEl", repo.revenueByEl(year, company, dept, el));
        out.put("byMonth", byMonth);
        out.put("topClients", repo.topClients(year, company, dept, el, topN));
        out.put("companies", repo.companies());
        return out;
    }

    /** Invoiced vs receipts per month for a year (cash-flow view). */
    public Map<String, Object> monthlyInvoicedReceipts(int year, String company) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("year", year);
        out.put("months", repo.invoicedVsReceiptsByMonth(year, company));
        return out;
    }

    /** Budget vs Actual per director/EL for a year, with totals. */
    public Map<String, Object> budgetVsActual(int year) {
        List<Map<String, Object>> rows = repo.budgetVsActualByDirector(year);
        double totBudget = sum(rows, "budget");
        double totActual = sum(rows, "actual");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("year", year);
        out.put("totalBudget", totBudget);
        out.put("totalActual", totActual);
        out.put("byDirector", rows);
        return out;
    }

    /** Recoverability: top and bottom N jobs by charged/cost ratio. */
    public Map<String, Object> recoverability(Integer year, int topN) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("year", year);
        out.put("top", repo.recoverability(year, topN, false));
        out.put("bottom", repo.recoverability(year, topN, true));
        return out;
    }

    /** Debtors: top N outstanding accounts + firm-wide total. */
    public Map<String, Object> debtors(int topN) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("totalOutstanding", repo.totalDebtors());
        out.put("topDebtors", repo.topDebtors(topN));
        return out;
    }

    private static double sum(List<Map<String, Object>> rows, String key) {
        return rows.stream()
            .map(r -> r.get(key))
            .filter(v -> v instanceof Number)
            .mapToDouble(v -> ((Number) v).doubleValue())
            .sum();
    }
}
