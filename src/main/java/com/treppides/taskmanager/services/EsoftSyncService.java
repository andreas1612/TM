package com.treppides.taskmanager.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * eSoft -> InternalTools datamart sync.
 *
 * Reads eSoft with SELECT ONLY (esoftJdbcTemplate) and truncate-and-reloads each
 * mirror table in InternalTools (primary jdbcTemplate). This is the ONLY thing in
 * the app that touches eSoft, and it never writes there. See financials/MIGRATION_PLAN.md §0.
 *
 * Volumes are small (~122k timesheet rows), so a full reload per table is simple and
 * fast. Runs nightly (cron) and can be triggered manually via SyncController.
 *
 * Lifecycle handled for free: joiners appear, leavers carry eSoft's inactive flag,
 * so the roster stays exactly what eSoft says — no hand-maintained seed.
 */
@Service
public class EsoftSyncService {

    private static final Logger log = LoggerFactory.getLogger(EsoftSyncService.class);
    private static final int CHUNK = 2000;

    private final JdbcTemplate esoftJdbc;   // read-only source
    private final JdbcTemplate jdbc;        // InternalTools datamart (write)

    public EsoftSyncService(@Qualifier("esoftJdbcTemplate") JdbcTemplate esoftJdbc,
                            JdbcTemplate jdbcTemplate) {
        this.esoftJdbc = esoftJdbc;
        this.jdbc = jdbcTemplate;
    }

    /** One mirror table: destination, ordered columns, and the SELECT (aliased to those columns). */
    private record TableSpec(String target, String[] cols, String selectSql) {}

    private static final List<TableSpec> TABLES = List.of(
        new TableSpec("esoft_employees",
            new String[]{"employee_code","employee_name","email","inactive","wrk_units_total",
                "cost_hourly_rate","chargeable_hourly_rate","category1","category2","category3","start_date","end_date"},
            """
            SELECT invservemployee_code AS employee_code, invservemployee_name AS employee_name,
                   invservemployee_email AS email, CAST(invservemployee_inactive AS bit) AS inactive,
                   invservemployee_wrk_units_total AS wrk_units_total,
                   invservemployee_cost_hourly_rate AS cost_hourly_rate,
                   invservemployee_chargeable_hourly_rate AS chargeable_hourly_rate,
                   invservemployee_category1 AS category1, invservemployee_category2 AS category2,
                   invservemployee_category3 AS category3, invservemployee_start_date AS start_date,
                   invservemployee_end_date AS end_date
            FROM dbo.invservemployees"""),

        new TableSpec("esoft_employee_categories",
            new String[]{"category_head","category_code","description"},
            """
            SELECT invservemployeecategory_head AS category_head, invservemployeecategory_code AS category_code,
                   MAX(invservemployeecategory_description) AS description
            FROM dbo.invservemployeecategories
            GROUP BY invservemployeecategory_head, invservemployeecategory_code"""),

        new TableSpec("esoft_analysis_codes",
            new String[]{"comp","head","code","description","inactive"},
            """
            SELECT accanalysis_comp AS comp, accanalysis_head AS head, accanalysis_code AS code,
                   accanalysis_desc1 AS description, CAST(accanalysis_inactive AS bit) AS inactive
            FROM dbo.accanalysis"""),

        new TableSpec("esoft_workcodes",
            new String[]{"work_code","description","not_chargeable","internal_use"},
            """
            SELECT invservwork_code AS work_code, invservwork_description AS description,
                   CAST(invservwork_notChargeable AS bit) AS not_chargeable,
                   CAST(invservwork_internalUse AS bit) AS internal_use
            FROM dbo.invservwork"""),

        new TableSpec("esoft_jobcards",
            new String[]{"jobcard","account_name","account_seq","comp","status","entry_date",
                "h2_director","h3_department","h4_el","year","budget_money1","docval","docvat"},
            """
            SELECT sophorder_order AS jobcard, sophorder_account_name AS account_name,
                   sophorder_account_seq AS account_seq, sophorder_comp AS comp, sophorder_status AS status,
                   sophorder_entry_date AS entry_date, sophorder_H2 AS h2_director, sophorder_H3 AS h3_department,
                   sophorder_H4 AS h4_el, sophorder_year AS year, sophorder_money1 AS budget_money1,
                   sophorder_docval AS docval, sophorder_docvat AS docvat
            FROM dbo.soporderheader"""),

        new TableSpec("esoft_accounts",
            new String[]{"account_seq","account_code","account_name","comp","currency","payment_terms","credit_limit"},
            """
            SELECT account_seq, account_code, account_name1 AS account_name, account_comp AS comp,
                   account_currency AS currency, account_payment_terms AS payment_terms,
                   account_credit_limit AS credit_limit
            FROM dbo.accaccounts"""),

        new TableSpec("esoft_balances",
            new String[]{"account_seq","year","period","opening_balance","debits","credits"},
            """
            SELECT accbal_account_seq AS account_seq, accbal_year AS year, accbal_period AS period,
                   accbal_opening_balance AS opening_balance, accbal_debits AS debits, accbal_credits AS credits
            FROM dbo.accbalances"""),

        new TableSpec("esoft_budget",
            new String[]{"a2_director","a3","year","amount","p1","p2","p3","p4","p5","p6","p7","p8","p9","p10","p11","p12"},
            """
            SELECT accbudanalysis_a2 AS a2_director, accbudanalysis_a3 AS a3, accbudanalysis_year AS year,
                   accbudanalysis_amount AS amount, accbudanalysis_p1 AS p1, accbudanalysis_p2 AS p2,
                   accbudanalysis_p3 AS p3, accbudanalysis_p4 AS p4, accbudanalysis_p5 AS p5, accbudanalysis_p6 AS p6,
                   accbudanalysis_p7 AS p7, accbudanalysis_p8 AS p8, accbudanalysis_p9 AS p9, accbudanalysis_p10 AS p10,
                   accbudanalysis_p11 AS p11, accbudanalysis_p12 AS p12
            FROM dbo.accbudgetanalysis"""),

        new TableSpec("esoft_receipts",
            new String[]{"rec_date","payer_name","payer_acc","base_amount","currency","comp","post_year","payer_a1","payer_a2","details"},
            """
            SELECT tran_rec_date AS rec_date, tran_rec_payer_name AS payer_name, tran_rec_payer_acc AS payer_acc,
                   tran_rec_base_amount AS base_amount, tran_rec_currency AS currency, tran_rec_comp AS comp,
                   tran_rec_receipt_post_yr AS post_year, tran_rec_payer_a1 AS payer_a1, tran_rec_payer_a2 AS payer_a2,
                   tran_rec_details AS details
            FROM dbo.acctranreceipts"""),

        new TableSpec("esoft_invoices",
            new String[]{"docno","account_seq","account_name","comp","currency","currency_rate","docdate","period",
                "year","doctype","status","docval","docvat","h2_director","h3_department","h4_el","sign","details","user"},
            """
            SELECT invsavehd_docno AS docno, invsavehd_account_seq AS account_seq, invsavehd_account_name AS account_name,
                   invsavehd_comp AS comp, invsavehd_currency AS currency, invsavehd_currency_rate AS currency_rate,
                   invsavehd_docdate AS docdate, invsavehd_period AS period, invsavehd_year AS year,
                   invsavehd_doctype AS doctype, invsavehd_status AS status, invsavehd_docval AS docval,
                   invsavehd_docvat AS docvat, invsavehd_H2 AS h2_director, invsavehd_H3 AS h3_department,
                   invsavehd_H4 AS h4_el, invsavehd_sign AS [sign], invsavehd_details AS details, invsavehd_user AS [user]
            FROM dbo.invsaveheaders"""),

        new TableSpec("esoft_timesheets",
            new String[]{"line_seq","employee_code","ts_date","jobcard","work_code","hourly_rate",
                "total_week_hours","total_amount","employee_cost"},
            """
            SELECT invservtimesheetln_seq AS line_seq, invservtimesheetln_employee_code AS employee_code,
                   invservtimesheetln_date AS ts_date, invservtimesheetln_jobcard AS jobcard,
                   invservtimesheetln_work_code AS work_code, invservtimesheetln_hourly_rate AS hourly_rate,
                   invservtimesheetln_total_week_hours AS total_week_hours, invservtimesheetln_total_amount AS total_amount,
                   invservtimesheetln_employee_cost AS employee_cost
            FROM dbo.invservtimesheetlines""")
    );

    /** Nightly full refresh (02:00 by default; override with esoft.sync.cron). */
    @Scheduled(cron = "${esoft.sync.cron:0 0 2 * * *}")
    public void scheduledSync() {
        log.info("Scheduled eSoft datamart sync starting...");
        Map<String, Object> result = syncAll();
        log.info("Scheduled eSoft datamart sync finished: {}", result);
    }

    /** Reload every mirror table. Returns per-table row counts (or error). */
    public Map<String, Object> syncAll() {
        Map<String, Object> summary = new LinkedHashMap<>();
        for (TableSpec spec : TABLES) {
            try {
                int n = reload(spec);
                summary.put(spec.target(), n);
            } catch (Exception e) {
                log.error("Sync failed for {}: {}", spec.target(), e.getMessage(), e);
                summary.put(spec.target(), "ERROR: " + e.getMessage());
            }
        }
        return summary;
    }

    private int reload(TableSpec spec) {
        LocalDateTime started = LocalDateTime.now();
        // READ from eSoft (SELECT only).
        List<Map<String, Object>> rows = esoftJdbc.queryForList(spec.selectSql());

        // WRITE to InternalTools: clear, then chunked batch insert.
        jdbc.update("DELETE FROM dbo." + spec.target());

        String cols = String.join(",", java.util.Arrays.stream(spec.cols()).map(c -> "[" + c + "]").toList());
        String qs = String.join(",", java.util.Collections.nCopies(spec.cols().length, "?"));
        String insert = "INSERT INTO dbo." + spec.target() + " (" + cols + ") VALUES (" + qs + ")";

        List<Object[]> batch = new ArrayList<>(CHUNK);
        for (Map<String, Object> row : rows) {
            Object[] vals = new Object[spec.cols().length];
            for (int i = 0; i < spec.cols().length; i++) vals[i] = row.get(spec.cols()[i]);
            batch.add(vals);
            if (batch.size() >= CHUNK) { jdbc.batchUpdate(insert, batch); batch.clear(); }
        }
        if (!batch.isEmpty()) jdbc.batchUpdate(insert, batch);

        jdbc.update("""
            INSERT INTO dbo.sync_log (table_name, rows_loaded, started_at, finished_at, status)
            VALUES (?, ?, ?, GETDATE(), 'OK')
            """, spec.target(), rows.size(), started);
        log.info("Synced {}: {} rows", spec.target(), rows.size());
        return rows.size();
    }
}
