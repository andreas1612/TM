package com.treppides.taskmanager.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Monthly chargeability check — runs on the 1st of each month at 9am.
 * Emails employees whose previous-month chargeability was below 50%,
 * with a week-by-week breakdown showing which weeks were low.
 */
@Service
public class ChargeabilityReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(ChargeabilityReminderScheduler.class);
    private static final double THRESHOLD = 50.0;

    private final JdbcTemplate jdbc;
    private final NotificationService notificationService;

    @Value("${app.chargeability.hr-email:hrdepartment@treppides.com}")
    private String hrEmail;

    public ChargeabilityReminderScheduler(JdbcTemplate jdbcTemplate,
                                          NotificationService notificationService) {
        this.jdbc = jdbcTemplate;
        this.notificationService = notificationService;
    }

    @Scheduled(cron = "0 0 9 1-7 * MON", zone = "Europe/Nicosia")
    public void checkMonthlyChargeability() {

        YearMonth lastMonth = YearMonth.now().minusMonths(1);
        LocalDate monthStart = lastMonth.atDay(1);
        LocalDate monthEnd = lastMonth.atEndOfMonth().plusDays(1); // exclusive upper bound

        log.info("Chargeability check starting for {}", lastMonth);

        // Get all active employees with their available hours per week
        List<Map<String, Object>> employees = jdbc.queryForList("""
            SELECT e.employee_code,
                   e.employee_name,
                   e.email,
                   e.wrk_units_total AS hrs_per_week
            FROM dbo.esoft_employees e
            WHERE e.inactive = 0
              AND e.email IS NOT NULL
              AND e.email != ''
            """);

        int notified = 0;

        for (Map<String, Object> emp : employees) {
            String code = (String) emp.get("employee_code");
            String name = (String) emp.get("employee_name");
            String email = (String) emp.get("email");
            BigDecimal hrsPerWeekBd = (BigDecimal) emp.get("hrs_per_week");
            double hrsPerWeek = hrsPerWeekBd != null ? hrsPerWeekBd.doubleValue() : 38.5;

            if (hrsPerWeek <= 0) continue;

            // Get weekly chargeable hours for this employee in the month
            List<Map<String, Object>> weeks = jdbc.queryForList("""
                SELECT t.ts_date AS week_start,
                       SUM(CASE WHEN w.not_chargeable = 0 AND jc.h3_department != 'K'
                                THEN t.total_week_hours ELSE 0 END) AS chargeable_hrs,
                       SUM(t.total_week_hours) AS total_hrs
                FROM dbo.esoft_timesheets t
                JOIN dbo.esoft_workcodes w ON w.work_code = t.work_code
                JOIN dbo.esoft_jobcards jc ON jc.jobcard = t.jobcard
                WHERE t.employee_code = ?
                  AND t.ts_date >= ? AND t.ts_date < ?
                GROUP BY t.ts_date
                ORDER BY t.ts_date
                """, code, monthStart, monthEnd);

            if (weeks.isEmpty()) continue;

            double totalChargeable = 0;
            int weekCount = weeks.size();

            for (Map<String, Object> w : weeks) {
                BigDecimal chg = (BigDecimal) w.get("chargeable_hrs");
                totalChargeable += chg != null ? chg.doubleValue() : 0;
            }

            double target = hrsPerWeek * weekCount;
            double pct = (totalChargeable / target) * 100.0;

            if (pct >= THRESHOLD) continue;

            // Build weekly breakdown
            StringBuilder weeklyBreakdown = new StringBuilder();
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM");

            for (Map<String, Object> w : weeks) {
                LocalDate weekStart = ((java.sql.Date) w.get("week_start")).toLocalDate();
                BigDecimal chg = (BigDecimal) w.get("chargeable_hrs");
                double weekChg = chg != null ? chg.doubleValue() : 0;
                double weekPct = (weekChg / hrsPerWeek) * 100.0;

                String flag = weekPct < THRESHOLD ? "  <-- low" : "";
                weeklyBreakdown.append(String.format(
                        "  - Week of %s:  %.1fh chargeable out of %.1fh available (%.0f%%)%s\n",
                        weekStart.format(fmt), weekChg, hrsPerWeek, weekPct, flag));
            }

            String ccEmail = (hrEmail != null && !hrEmail.isBlank()) ? hrEmail : null;

            notificationService.sendChargeabilityReminderEmail(
                    email, name, lastMonth.toString(),
                    pct, totalChargeable, target,
                    weeklyBreakdown.toString(), ccEmail);

            notified++;
            log.info("Chargeability alert sent to {} ({}) — {}%", name, email, String.format("%.1f", pct));
        }

        log.info("Chargeability check complete for {} — {} employees notified", lastMonth, notified);
    }
}
