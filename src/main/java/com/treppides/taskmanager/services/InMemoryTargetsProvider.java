package com.treppides.taskmanager.services;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * In-memory fallback for dbo.performance_targets.
 *
 * The DBA has not yet created the performance_targets table in InternalTools.
 * This component loads ~/performance_targets_seed.sql at startup (the same
 * approach the Flask tester used on port 9092) so that the performance
 * endpoints work without the DB table.
 */
@Component
public class InMemoryTargetsProvider {

    private static final Logger log = LoggerFactory.getLogger(InMemoryTargetsProvider.class);
    private static final Path SEED_PATH = Path.of(System.getProperty("user.home"), "performance_targets_seed.sql");

    private final List<Map<String, Object>> allTargets = new ArrayList<>();
    private final Map<String, Map<String, Object>> byCode = new HashMap<>();

    @PostConstruct
    void load() {
        if (!Files.exists(SEED_PATH)) {
            log.warn("Seed file not found at {}. In-memory targets will be empty.", SEED_PATH);
            return;
        }

        try {
            String sql = Files.readString(SEED_PATH);
            Pattern p = Pattern.compile(
                "\\('([^']*)',\\s*'([^']*)',\\s*'([^']*)',\\s*([\\d.]+),\\s*([\\d.]+),\\s*'([^']*)',\\s*'([^']*)',\\s*'([^']*)'\\)"
            );
            Matcher m = p.matcher(sql);
            while (m.find()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("esoft_code", m.group(1));
                row.put("employee_name", m.group(2));
                row.put("level", m.group(3));
                row.put("target_hrs_month", Double.parseDouble(m.group(4)));
                row.put("target_hrs_week", Double.parseDouble(m.group(5)));
                row.put("location", m.group(6));
                row.put("manager_name", m.group(7));
                row.put("azure_email", m.group(8));
                allTargets.add(row);
                byCode.put(m.group(1), row);
            }
            log.info("Loaded {} performance targets from seed SQL (in-memory fallback)", allTargets.size());
        } catch (IOException e) {
            log.error("Failed to read seed file {}: {}", SEED_PATH, e.getMessage());
        }
    }

    public boolean hasData() {
        return !allTargets.isEmpty();
    }

    public Optional<Map<String, Object>> findByCode(String esoftCode) {
        return Optional.ofNullable(byCode.get(esoftCode));
    }

    public List<Map<String, Object>> findByManager(String managerName) {
        return allTargets.stream()
            .filter(r -> managerName.equals(r.get("manager_name")))
            .collect(Collectors.toList());
    }

    public List<Map<String, Object>> findAll() {
        return allTargets.stream()
            .map(r -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("esoft_code", r.get("esoft_code"));
                m.put("employee_name", r.get("employee_name"));
                return m;
            })
            .sorted(Comparator.comparing(r -> (String) r.get("employee_name")))
            .collect(Collectors.toList());
    }
}
