package com.treppides.taskmanager.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PerformanceCardDTO {

    private String esoftCode;
    private String employeeName;
    private String jobTitle;
    private String team;
    private String engagementLeader;
    private String location;
    private String level;
    private String period;
    private int weeksInPeriod;
    private double actualHrs;
    private double availableHrsPeriod;
    private double targetHrsPeriod;
    // Raw per-person targets (not period-scaled) — used to pre-fill the HR/SUPER target editor.
    private double targetHrsWeek;
    private double targetHrsMonth;
    // Effective contracted hours/week (eSoft wrk_units_total, or an HR override) — the editor
    // input from which the chargeable target is derived.
    private double contractedHrsWeek;
    // HR-saved contracted override for this month (0 if none) — lets the editor show a saved
    // part-time figure but stay blank (prompting input) when nothing was ever set.
    private double contractedOverride;
    private double chargeabilityPct;
    private double targetPct;
    private String badge;
    // Frontend reads card.isManager to decide whether to load the team view;
    // without this, Lombok's isManager() getter serializes as "manager" and the
    // team view silently never renders.
    @JsonProperty("isManager")
    private boolean isManager;

    private List<PerformanceCardDTO> directReports;
    private TeamSummaryDTO teamSummary;
    private List<CompanyBreakdownDTO> companyBreakdown;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TeamSummaryDTO {
        private int headCount;
        private double teamAvgPct;
        private String badge;
        private int greenCount;
        private int amberCount;
        private int redCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompanyBreakdownDTO {
        private String company;
        private double hours;
    }
}
