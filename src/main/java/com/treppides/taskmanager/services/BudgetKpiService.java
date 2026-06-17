package com.treppides.taskmanager.services;

import com.treppides.taskmanager.dto.BudgetKpiDTO;
import com.treppides.taskmanager.repositories.BudgetRepository;
import com.treppides.taskmanager.repositories.FeeAdjustmentRepository;
import com.treppides.taskmanager.repositories.PerformanceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class BudgetKpiService {

    private final BudgetRepository budgetRepo;
    private final FeeAdjustmentRepository feeRepo;
    private final PerformanceRepository perfRepo;

    public BudgetKpiService(BudgetRepository budgetRepo, FeeAdjustmentRepository feeRepo, PerformanceRepository perfRepo) {
        this.budgetRepo = budgetRepo;
        this.feeRepo = feeRepo;
        this.perfRepo = perfRepo;
    }

    public BudgetKpiDTO buildKpi(String email, Integer year) {
        int yr = year != null ? year : LocalDate.now().getYear();

        // Resolve email → eSoft code
        String esoftCode = perfRepo.findCodeByEmail(email)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Employee not found in eSoft"));

        return buildKpiForCode(esoftCode, yr);
    }

    /** Build KPI directly from eSoft code (admin use). */
    public BudgetKpiDTO buildKpiByCode(String esoftCode, Integer year) {
        int yr = year != null ? year : LocalDate.now().getYear();
        return buildKpiForCode(esoftCode, yr);
    }

    private BudgetKpiDTO buildKpiForCode(String esoftCode, int yr) {

        // Find budget entry
        Map<String, Object> budgetInfo = budgetRepo.findBudgetByEsoftCode(esoftCode, yr)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "No budget data for this employee"));

        String managerName = (String) budgetInfo.get("manager_name");
        String invoiceCode = (String) budgetInfo.get("invoice_code");
        String department  = nullSafe(budgetInfo.get("department"));
        String elName      = nullSafe(budgetInfo.get("el_name"));
        String team        = nullSafe(budgetInfo.get("team"));

        // Monthly budgets
        List<Map<String, Object>> budgetRows = budgetRepo.findMonthlyBudgets(invoiceCode, yr);
        Map<Integer, Double> budgetByMonth = new HashMap<>();
        for (Map<String, Object> row : budgetRows) {
            budgetByMonth.put(toInt(row.get("month_num")), toDouble(row.get("budget")));
        }

        // Monthly invoiced actuals from eSoft
        List<Map<String, Object>> invoicedRows = budgetRepo.findMonthlyInvoiced(invoiceCode, yr);
        Map<Integer, Double> invoicedByMonth = new HashMap<>();
        for (Map<String, Object> row : invoicedRows) {
            invoicedByMonth.put(toInt(row.get("month_num")), toDouble(row.get("invoiced")));
        }

        // Fee adjustments from InvoiceAllocation DB
        Map<Integer, Double> auditByMonth = new HashMap<>();
        Map<Integer, Double> taxByMonth = new HashMap<>();
        try {
            List<Map<String, Object>> feeSums = feeRepo.findMonthlySums(invoiceCode, yr);
            for (Map<String, Object> row : feeSums) {
                int mo = toInt(row.get("month_num"));
                auditByMonth.put(mo, toDouble(row.get("audit_fees")));
                taxByMonth.put(mo, toDouble(row.get("tax_fees")));
            }
        } catch (Exception e) {
            // InvoiceAllocation DB may be unavailable — continue without fee adjustments
        }

        // Build monthly KPI
        List<BudgetKpiDTO.MonthKpiDTO> months = new ArrayList<>();
        double cumBudget = 0, cumInvoiced = 0;

        for (int m = 1; m <= 12; m++) {
            double bgt = budgetByMonth.getOrDefault(m, 0.0);
            double esoftInv = invoicedByMonth.getOrDefault(m, 0.0);
            double audit = auditByMonth.getOrDefault(m, 0.0);
            double tax = taxByMonth.getOrDefault(m, 0.0);
            double inv = esoftInv + audit + tax;
            double pct = bgt > 0 ? round2((inv / bgt) * 100) : 0.0;

            cumBudget += bgt;
            cumInvoiced += inv;

            months.add(BudgetKpiDTO.MonthKpiDTO.builder()
                .month(m)
                .monthName(Month.of(m).getDisplayName(TextStyle.SHORT, Locale.ENGLISH))
                .budget(round2(bgt))
                .invoiced(round2(inv))
                .esoftInvoiced(round2(esoftInv))
                .auditFees(round2(audit))
                .taxFees(round2(tax))
                .completionPct(pct)
                .badge(badge(pct))
                .build());
        }

        double cumPct = cumBudget > 0 ? round2((cumInvoiced / cumBudget) * 100) : 0.0;

        // EL average
        double elAvg = computeGroupAvg(elName, yr, "el");
        // Department average
        double deptAvg = computeGroupAvg(department, yr, "dept");

        return BudgetKpiDTO.builder()
            .managerName(managerName)
            .esoftCode(esoftCode)
            .invoiceCode(invoiceCode)
            .department(department)
            .elName(elName)
            .team(team)
            .year(yr)
            .months(months)
            .cumulativeBudget(round2(cumBudget))
            .cumulativeInvoiced(round2(cumInvoiced))
            .cumulativeCompletionPct(cumPct)
            .cumulativeBadge(badge(cumPct))
            .elAvgCompletionPct(elAvg)
            .deptAvgCompletionPct(deptAvg)
            .build();
    }

    private double computeGroupAvg(String groupName, int year, String type) {
        if (groupName == null || groupName.isEmpty()) return 0.0;

        List<Map<String, Object>> managers = "el".equals(type)
            ? budgetRepo.findManagersByEl(groupName, year)
            : budgetRepo.findManagersByDepartment(groupName, year);

        if (managers.isEmpty()) return 0.0;

        List<String> codes = managers.stream()
            .map(r -> (String) r.get("invoice_code"))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

        if (codes.isEmpty()) return 0.0;

        // Get all budgets for these managers
        Map<String, Double> totalBudgets = new HashMap<>();
        for (String code : codes) {
            List<Map<String, Object>> budgets = budgetRepo.findMonthlyBudgets(code, year);
            double total = budgets.stream().mapToDouble(r -> toDouble(r.get("budget"))).sum();
            totalBudgets.put(code, total);
        }

        // Get all invoiced for these managers (bulk query)
        List<Map<String, Object>> invoicedRows = budgetRepo.findBulkInvoiced(codes, year);
        Map<String, Double> totalInvoiced = new HashMap<>();
        for (Map<String, Object> row : invoicedRows) {
            String code = (String) row.get("invoice_code");
            totalInvoiced.merge(code, toDouble(row.get("invoiced")), Double::sum);
        }

        // Add fee adjustments to invoiced totals
        try {
            List<Map<String, Object>> bulkFees = feeRepo.findBulkSums(codes, year);
            for (Map<String, Object> row : bulkFees) {
                String code = (String) row.get("invoice_code");
                totalInvoiced.merge(code, toDouble(row.get("total_fees")), Double::sum);
            }
        } catch (Exception e) {
            // InvoiceAllocation DB may be unavailable
        }

        // Compute each manager's completion % and average them
        double pctSum = 0;
        int count = 0;
        for (String code : codes) {
            double bgt = totalBudgets.getOrDefault(code, 0.0);
            double inv = totalInvoiced.getOrDefault(code, 0.0);
            if (bgt > 0) {
                pctSum += (inv / bgt) * 100;
                count++;
            }
        }

        return count > 0 ? round2(pctSum / count) : 0.0;
    }

    // ---- helpers ----

    private static String badge(double completionPct) {
        if (completionPct >= 100.0) return "GREEN";
        if (completionPct >= 80.0)  return "AMBER";
        return "RED";
    }

    private static double toDouble(Object v) {
        if (v == null) return 0.0;
        if (v instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(v.toString()); } catch (NumberFormatException e) { return 0.0; }
    }

    private static int toInt(Object v) {
        if (v == null) return 0;
        if (v instanceof Number n) return n.intValue();
        try { return Integer.parseInt(v.toString()); } catch (NumberFormatException e) { return 0; }
    }

    private static String nullSafe(Object v) {
        return v == null ? "" : v.toString();
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
