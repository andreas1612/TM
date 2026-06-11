package com.treppides.taskmanager.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BudgetKpiDTO {

    private String managerName;
    private String esoftCode;
    private String invoiceCode;
    private String department;
    private String elName;
    private String team;
    private int year;

    private List<MonthKpiDTO> months;

    private double cumulativeBudget;
    private double cumulativeInvoiced;
    private double cumulativeCompletionPct;
    private String cumulativeBadge;

    private double elAvgCompletionPct;
    private double deptAvgCompletionPct;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MonthKpiDTO {
        private int month;
        private String monthName;
        private double budget;
        private double invoiced;
        private double completionPct;
        private String badge;
    }
}
