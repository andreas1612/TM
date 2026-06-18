package com.treppides.taskmanager.controllers;

import com.treppides.taskmanager.repositories.PerformanceRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reports/performance")
@Profile("dev")
public class DevPerformanceController {

    private final PerformanceRepository repo;

    public DevPerformanceController(PerformanceRepository repo) {
        this.repo = repo;
    }

    @GetMapping("/employees")
    public List<Map<String, Object>> allEmployees() {
        return repo.findAllEmployees();
    }
}
