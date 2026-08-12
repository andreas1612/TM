package com.treppides.taskmanager.services;

import com.treppides.taskmanager.dto.PerformanceCardDTO;
import com.treppides.taskmanager.repositories.PerformanceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
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

    public PerformanceCardDTO buildCardByCode(String esoftCode, String period, Integer year, Integer month) {
        int[] ym = primaryMonth(period, year, month);
        Map<String, Object> target = repo.findEffectiveTarget(esoftCode, ym[0], ym[1])
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "NON_CHARGEABLE_ROLE"));
        return buildCardFromTarget(target, period, year, month);
    }

    public PerformanceCardDTO buildCard(String email, String period, Integer year, Integer month) {
        String resolvedCode = repo.findCodeByEmail(email)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "NON_CHARGEABLE_ROLE"));
        int[] ym = primaryMonth(period, year, month);
        Map<String, Object> target = repo.findEffectiveTarget(resolvedCode, ym[0], ym[1])
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "NON_CHARGEABLE_ROLE"));
        return buildCardFromTarget(target, period, year, month);
    }

    /** The month whose target drives the card: the viewed month, or the latest month for YTD. */
    private static int[] primaryMonth(String period, Integer year, Integer month) {
        LocalDate today = LocalDate.now();
        int yr = year != null ? year : today.getYear();
        int mo = "ytd".equalsIgnoreCase(period)
            ? (yr == today.getYear() ? today.getMonthValue() : 12)
            : (month != null ? month : today.getMonthValue());
        return new int[]{yr, mo};
    }

    private PerformanceCardDTO buildCardFromTarget(Map<String, Object> target, String period, Integer year, Integer month) {
        PeriodInfo pi = periodRange(period, year, month);

        String esoftCode    = (String) target.get("esoft_code");
        String employeeName = (String) target.get("employee_name");
        String level        = (String) target.get("level");
        double targetHrsWeek = toDouble(target.get("target_hrs_week"));
        double targetHrsMonth = toDouble(target.get("target_hrs_month"));

        PerformanceCardDTO.PerformanceCardDTOBuilder builder = PerformanceCardDTO.builder()
            .esoftCode(esoftCode)
            .employeeName(employeeName)
            .level(level)
            .location(nullSafe(target.get("location")))
            .period(pi.start + "/" + pi.end)
            .weeksInPeriod(pi.weeks)
            .targetHrsWeek(round2(targetHrsWeek))
            .targetHrsMonth(round2(targetHrsMonth))
            .targetPct(100.0);

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

        Optional<Map<String, Object>> timesheetOpt = repo.findActualHours(esoftCode, pi.start, pi.end);

        double availHrsWeek = timesheetOpt.map(r -> toDouble(r.get("available_hrs_week"))).orElse(38.5);
        double actualHrs    = timesheetOpt.map(r -> toDouble(r.get("actual_hrs"))).orElse(0.0);
        String jobTitle     = timesheetOpt.map(r -> nullSafe(r.get("job_title"))).orElse("");
        String team         = timesheetOpt.map(r -> nullSafe(r.get("team_name"))).orElse("");
        String el           = timesheetOpt.map(r -> nullSafe(r.get("engagement_leader"))).orElse("");

        double availHrsPeriod  = availHrsWeek * pi.weeks;
        double targetHrsPeriod = targetHrsWeek * pi.weeks;
        double chargeability   = targetHrsPeriod > 0 ? round2((actualHrs / targetHrsPeriod) * 100) : 0.0;
        double targetPct       = 100.0;

        boolean isManager = !repo.findDirectReportsByCode(esoftCode).isEmpty();

        List<PerformanceCardDTO.CompanyBreakdownDTO> breakdown = repo.findHoursByCompany(esoftCode, pi.start, pi.end)
            .stream()
            .map(r -> PerformanceCardDTO.CompanyBreakdownDTO.builder()
                .company(nullSafe(r.get("company")))
                .hours(round2(toDouble(r.get("hours"))))
                .build())
            .collect(Collectors.toList());

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
            .companyBreakdown(breakdown)
            .build();
    }

    public PerformanceCardDTO buildTeamCardByCode(String esoftCode, String period, Integer year, Integer month) {
        PerformanceCardDTO managerCard = buildCardByCode(esoftCode, period, year, month);
        return buildTeamFromManagerCard(managerCard, esoftCode, period, year, month);
    }

    public PerformanceCardDTO buildTeamCard(String azureEmail, String period, Integer year, Integer month) {
        PerformanceCardDTO managerCard = buildCard(azureEmail, period, year, month);
        String managerCode = repo.findCodeByEmail(azureEmail)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "NON_CHARGEABLE_ROLE"));
        return buildTeamFromManagerCard(managerCard, managerCode, period, year, month);
    }

    /** True if {@code reportCode} is one of {@code managerCode}'s live-eSoft direct reports. */
    public boolean isReportOf(String managerCode, String reportCode) {
        if (managerCode == null || reportCode == null) return false;
        return repo.findDirectReportsByCode(managerCode).stream()
            .anyMatch(r -> reportCode.equals(r.get("esoft_code")));
    }

    private PerformanceCardDTO buildTeamFromManagerCard(PerformanceCardDTO managerCard, String managerCode, String period, Integer year, Integer month) {
        if (!managerCard.isManager()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not a manager");
        }

        int[] ym = primaryMonth(period, year, month);
        repo.findEffectiveTarget(managerCode, ym[0], ym[1])
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "NON_CHARGEABLE_ROLE"));

        // Reports come LIVE from eSoft (category4 supervisor field). Each is enriched with its
        // effective target for the viewed month (level / target hours / location); skip any not
        // yet in the roster.
        List<Map<String, Object>> reports = new ArrayList<>();
        for (Map<String, Object> basic : repo.findDirectReportsByCode(managerCode)) {
            String rcode = (String) basic.get("esoft_code");
            Optional<Map<String, Object>> t = repo.findEffectiveTarget(rcode, ym[0], ym[1]);
            if (t.isEmpty()) continue;
            Map<String, Object> merged = new HashMap<>(t.get());
            merged.put("esoft_code", rcode);
            merged.put("employee_name", basic.get("employee_name"));   // prefer the live eSoft name
            reports.add(merged);
        }

        PeriodInfo pi = periodRange(period, year, month);

        List<String> codes = reports.stream()
            .map(r -> (String) r.get("esoft_code"))
            .collect(Collectors.toList());

        List<Map<String, Object>> timesheets = repo.findTeamActualHours(codes, pi.start, pi.end);
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

            double availHrsPeriod  = availHrsWeek * pi.weeks;
            double targetHrsPeriod = tgtHrsWeek * pi.weeks;
            double chargeability   = targetHrsPeriod > 0 ? round2((actualHrs / targetHrsPeriod) * 100) : 0.0;
            double targetPct       = 100.0;
            String b               = "Maternity".equalsIgnoreCase(lvl) ? "EXEMPT" : badge(chargeability, targetPct);

            directCards.add(PerformanceCardDTO.builder()
                .esoftCode(code)
                .employeeName(name)
                .level(lvl)
                .location(loc)
                .period(pi.start + "/" + pi.end)
                .weeksInPeriod(pi.weeks)
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

        double teamTargetPct = 100.0;

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

    private record PeriodInfo(LocalDate start, LocalDate end, int weeks) {}

    /**
     * PBI-style Monday-based period calculation.
     * Matches Power BI week counting — validated against PBI output.
     * For current month, only counts completed weeks (Friday has passed).
     */
    private static PeriodInfo periodRange(String period, Integer year, Integer month) {
        LocalDate today = LocalDate.now();
        int yr = year != null ? year : today.getYear();
        int mo = month != null ? month : today.getMonthValue();

        if ("ytd".equalsIgnoreCase(period)) {
            LocalDate firstMon = mondayOnOrAfter(LocalDate.of(yr, 1, 1));
            LocalDate lastMon  = mondayOnOrBefore(yr == today.getYear() ? today : LocalDate.of(yr, 12, 31));
            if (lastMon.isBefore(firstMon)) {
                return new PeriodInfo(firstMon, firstMon.plusDays(7), 1);
            }
            int weeks = (int) (ChronoUnit.DAYS.between(firstMon, lastMon) / 7) + 1;
            return new PeriodInfo(firstMon, lastMon.plusDays(7), weeks);
        }

        // Month period — PBI includes Mondays in [1st_of_month, 1st_of_next_month]
        LocalDate firstOfMonth = LocalDate.of(yr, mo, 1);
        LocalDate firstOfNext  = firstOfMonth.plusMonths(1);

        LocalDate firstMon = mondayOnOrAfter(firstOfMonth);
        LocalDate lastMon  = mondayOnOrBefore(firstOfNext); // PBI includes overlap Monday

        // For current/future month, only count completed weeks (Friday has passed)
        if (firstOfNext.isAfter(today)) {
            LocalDate cap = mondayOnOrBefore(today.minusDays(4));
            if (cap.isBefore(firstMon)) {
                return new PeriodInfo(firstMon, firstMon.plusDays(7), 0);
            }
            lastMon = cap;
        }

        int weeks = (int) (ChronoUnit.DAYS.between(firstMon, lastMon) / 7) + 1;
        return new PeriodInfo(firstMon, lastMon.plusDays(7), weeks);
    }

    private static LocalDate mondayOnOrAfter(LocalDate d) {
        return d.getDayOfWeek() == DayOfWeek.MONDAY ? d : d.with(TemporalAdjusters.next(DayOfWeek.MONDAY));
    }

    private static LocalDate mondayOnOrBefore(LocalDate d) {
        return d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
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
