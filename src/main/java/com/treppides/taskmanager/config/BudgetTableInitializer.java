package com.treppides.taskmanager.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import javax.sql.DataSource;

@Component
public class BudgetTableInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(BudgetTableInitializer.class);
    private final DataSourceProperties dsProps;

    public BudgetTableInitializer(DataSourceProperties dsProps) {
        this.dsProps = dsProps;
    }

    @Override
    public void run(String... args) {
        try {
            DataSource ds = DataSourceBuilder.create()
                    .url(dsProps.getUrl())
                    .username(dsProps.getUsername())
                    .password(dsProps.getPassword())
                    .driverClassName(dsProps.getDriverClassName())
                    .build();
            JdbcTemplate jdbc = new JdbcTemplate(ds);

            if (!tableExists(jdbc)) {
                log.info("budget_per_manager table not found — creating...");
                createTable(jdbc);
                log.info("budget_per_manager table created.");
            }

            int count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM dbo.budget_per_manager", Integer.class);
            if (count == 0) {
                log.info("budget_per_manager is empty — seeding data...");
                seedData(jdbc);
                int seeded = jdbc.queryForObject(
                        "SELECT COUNT(*) FROM dbo.budget_per_manager", Integer.class);
                log.info("budget_per_manager seeded with {} rows.", seeded);
            } else {
                log.info("budget_per_manager already has {} rows — skipping seed.", count);
            }
        } catch (Exception e) {
            log.warn("budget_per_manager init failed ({}). "
                    + "The budget-kpi endpoint will not work until the table is created manually.",
                    e.getMessage());
        }
    }

    private boolean tableExists(JdbcTemplate jdbc) {
        Integer found = jdbc.queryForObject("""
                SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES
                WHERE TABLE_NAME = 'budget_per_manager'
                """, Integer.class);
        return found != null && found > 0;
    }

    private void createTable(JdbcTemplate jdbc) {
        jdbc.execute("""
                CREATE TABLE dbo.budget_per_manager (
                    id              INT IDENTITY(1,1) PRIMARY KEY,
                    esoft_code      NVARCHAR(10)    NULL,
                    manager_name    NVARCHAR(200)   NOT NULL,
                    invoice_code    NVARCHAR(10)    NULL,
                    budget          DECIMAL(12,2)   NOT NULL,
                    department      NVARCHAR(50)    NULL,
                    department_code CHAR(5)         NULL,
                    el_name         NVARCHAR(200)   NULL,
                    team            NVARCHAR(100)   NULL,
                    year            INT             NOT NULL,
                    month_num       INT             NOT NULL,
                    updated_at      DATETIME        NOT NULL DEFAULT GETDATE()
                )
                """);
    }

    private void seedData(JdbcTemplate jdbc) throws Exception {
        String sql = new ClassPathResource("db/budget_per_manager_data.sql")
                .getContentAsString(StandardCharsets.UTF_8);
        jdbc.execute(sql);
    }
}
