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
    private double chargeabilityPct;
    private double targetPct;
    private String badge;
    private boolean isManager;

    private List<PerformanceCardDTO> directReports;
    private TeamSummaryDTO teamSummary;

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
}
