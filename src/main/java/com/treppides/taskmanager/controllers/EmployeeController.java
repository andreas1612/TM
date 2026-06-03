package com.treppides.taskmanager.controllers;

import com.treppides.taskmanager.dto.EmployeeOptionResponse;
import com.treppides.taskmanager.services.EmployeeService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/employees")
@CrossOrigin
public class EmployeeController {

    private final EmployeeService employeeService;

    public EmployeeController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    @GetMapping("/direct-reports/{email}")
    public List<EmployeeOptionResponse> getDirectReports(@PathVariable String email) {
        return employeeService.getDirectReports(email);
    }
}