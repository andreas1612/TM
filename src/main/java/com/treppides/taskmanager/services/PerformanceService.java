package com.treppides.taskmanager.services;

import com.treppides.taskmanager.dto.PerformanceCardDTO;
import com.treppides.taskmanager.repositories.PerformanceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class PerformanceService {

    private final PerformanceRepository repo;

    public PerformanceService(PerformanceRepository repo) {
        this.repo = repo;
    }

    public PerformanceCardDTO buildCard(String azureEmail, String period) {
        Map<String, Object> target = repo.findTargetByAzureEmail(azureEmail)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "NON_CHARGEABLE_ROLE"));

        LocalDate[] range = periodRange(period);
        LocalDate start = range[0];
        LocalDate end   = range[1];
        int weeks = (int) (ChronoUnit.DAYS.between(start, end) / 7);

        String esoftCode    = (String) target.get("esoft_code");
        String employeeName = (String) target.get("employee_name");
        String level        = (String) target.get("level");
        double targetHrsWeek = toDouble(target.get("target_hrs_week"));

        PerformanceCardDTO.PerformanceCardDTOBuilder builder = PerformanceCardDTO.builder()
            .esoftCode(esoftCode)
            .employeeName(employeeName)
            .level(level)
            .location(nullSafe(target.get("location")))
            .period(start + "/" + end)
            .weeksInPeriod(weeks)
            .targetPct(targetHrsWeek > 0
                ? round2(targetHrsWeek / 38.5 * 100)  // denominator is resolved per-employee below
                : 0.0);

        if ("Maternity".equalsIgnoreCase(level)) {
            return builder
                .jobTitle("")
                .team("")
                .engagementLeader("")
                .actualHrs(0)
                .availableHrsPeriod(0)
                .targetHrsPeriod(0)
                .chargeabilityPct(0)
                .targetPct(0)
                .badge("EXEMPT")
                .isManager(false)
                .build();
        }

        Optional<Map<String, Object>> timesheetOpt = repo.findActualHours(esoftCode, start, end);

        double availHrsWeek = timesheetOpt.map(r -> toDouble(r.get("available_hrs_week"))).orElse(38.5);
        double actualHrs    = timesheetOpt.map(r -> toDouble(r.get("actual_hrs"))).orElse(0.0);
        String jobTitle     = timesheetOpt.map(r -> nullSafe(r.get("job_title"))).orElse("");
        String team         = timesheetOpt.map(r -> nullSafe(r.get("team_name"))).orElse("");
        String el           = timesheetOpt.map(r -> nullSafe(r.get("engagement_leader"))).orElse("");

        double availHrsPeriod  = availHrsWeek * weeks;
        double targetHrsPeriod = targetHrsWeek * weeks;
        double chargeability   = availHrsPeriod > 0 ? round2((actualHrs / availHrsPeriod) * 100) : 0.0;
        double targetPct       = availHrsWeek > 0   ? round2((targetHrsWeek / availHrsWeek) * 100) : 0.0;

        boolean isManager = !repo.findDirectReports(employeeName).isEmpty();

        return builder
            .jobTitle(jobTitle)
            .team(team)
            .engagementLeader(el)
            .actualHrs(round2(actualHrs))
            .availableHrsPeriod(round2(availHrsPeriod))
            .targetHrsPeriod(round2(targetHrsPeriod))
            .chargeabilityPct(chargeability)
            .targetPct(targetPct)
            .badge(badge(chargeability, targetPct))
            .isManager(isManager)
            .build();
    }

    public PerformanceCardDTO buildTeamCard(String azureEmail, String period) {
        PerformanceCardDTO managerCard = buildCard(azureEmail, period);

        if (!managerCard.isManager()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not a manager");
        }

        Map<String, Object> target = repo.findTargetByAzureEmail(azureEmail)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "NON_CHARGEABLE_ROLE"));
        String managerName = (String) target.get("employee_name");

        List<Map<String, Object>> reports = repo.findDirectReports(managerName);

        LocalDate[] range = periodRange(period);
        LocalDate start = range[0];
        LocalDate end   = range[1];
        int weeks = (int) (ChronoUnit.DAYS.between(start, end) / 7);

        List<String> codes = reports.stream()
            .map(r -> (String) r.get("esoft_code"))
            .collect(Collectors.toList());

        List<Map<String, Object>> timesheets = repo.findTeamActualHours(codes, start, end);
        Map<String, Map<String, Object>> tsMap = timesheets.stream()
            .collect(Collectors.toMap(r -> (String) r.get("esoft_code"), r -> r));

        List<PerformanceCardDTO> directCards = new ArrayList<>();
        for (Map<String, Object> report : reports) {
            String code          = (String) report.get("esoft_code");
            String name          = (String) report.get("employee_name");
            String lvl           = (String) report.get("level");
            double tgtHrsWeek    = toDouble(report.get("target_hrs_week"));
            String loc           = nullSafe(report.get("location"));

            Map<String, Object> ts = tsMap.get(code);
            double availHrsWeek   = ts != null ? toDouble(ts.get("available_hrs_week")) : 38.5;
            double actualHrs      = ts != null ? toDouble(ts.get("actual_hrs")) : 0.0;

            double availHrsPeriod  = availHrsWeek * weeks;
            double targetHrsPeriod = tgtHrsWeek * weeks;
            double chargeability   = availHrsPeriod > 0 ? round2((actualHrs / availHrsPeriod) * 100) : 0.0;
            double targetPct       = availHrsWeek > 0   ? round2((tgtHrsWeek / availHrsWeek) * 100) : 0.0;
            String b               = "Maternity".equalsIgnoreCase(lvl) ? "EXEMPT" : badge(chargeability, targetPct);

            directCards.add(PerformanceCardDTO.builder()
                .esoftCode(code)
                .employeeName(name)
                .level(lvl)
                .location(loc)
                .period(start + "/" + end)
                .weeksInPeriod(weeks)
                .actualHrs(round2(actualHrs))
                .availableHrsPeriod(round2(availHrsPeriod))
                .targetHrsPeriod(round2(targetHrsPeriod))
                .chargeabilityPct("EXEMPT".equals(b) ? 0.0 : chargeability)
                .targetPct("EXEMPT".equals(b) ? 0.0 : targetPct)
                .badge(b)
                .build());
        }

        List<PerformanceCardDTO> gradedCards = directCards.stream()
            .filter(c -> !"EXEMPT".equals(c.getBadge()))
            .toList();

        int greenCount = (int) gradedCards.stream().filter(c -> "GREEN".equals(c.getBadge())).count();
        int amberCount = (int) gradedCards.stream().filter(c -> "AMBER".equals(c.getBadge())).count();
        int redCount   = (int) gradedCards.stream().filter(c -> "RED".equals(c.getBadge())).count();
        double avgPct  = gradedCards.isEmpty() ? 0.0
            : round2(gradedCards.stream().mapToDouble(PerformanceCardDTO::getChargeabilityPct).average().orElse(0));

        double teamTargetPct = gradedCards.isEmpty() ? 0.0
            : round2(gradedCards.stream().mapToDouble(PerformanceCardDTO::getTargetPct).average().orElse(0));

        PerformanceCardDTO.TeamSummaryDTO summary = PerformanceCardDTO.TeamSummaryDTO.builder()
            .headCount(directCards.size())
            .teamAvgPct(avgPct)
            .badge(badge(avgPct, teamTargetPct))
            .greenCount(greenCount)
            .amberCount(amberCount)
            .redCount(redCount)
            .build();

        managerCard.setDirectReports(directCards);
        managerCard.setTeamSummary(summary);
        return managerCard;
    }

    // ---- helpers ----

    private static LocalDate[] periodRange(String period) {
        LocalDate today = LocalDate.now();
        if ("ytd".equalsIgnoreCase(period)) {
            return new LocalDate[]{LocalDate.of(today.getYear(), 1, 1), today.plusDays(1)};
        }
        LocalDate first = today.withDayOfMonth(1);
        return new LocalDate[]{first, first.plusMonths(1)};
    }

    private static String badge(double actual, double target) {
        if (actual >= target)              return "GREEN";
        if (actual >= target * 0.80)       return "AMBER";
        return "RED";
    }

    private static double toDouble(Object v) {
        if (v == null) return 0.0;
        if (v instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(v.toString()); } catch (NumberFormatException e) { return 0.0; }
    }

    private static String nullSafe(Object v) {
        return v == null ? "" : v.toString();
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
