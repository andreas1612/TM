package com.treppides.taskmanager.services;

import com.treppides.taskmanager.dto.EmployeeOptionResponse;
import com.treppides.taskmanager.entities.Employee;
import com.treppides.taskmanager.repositories.EmployeeRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class EmployeeService {

    private final EmployeeRepository employeeRepository;

    public EmployeeService(EmployeeRepository employeeRepository) {
        this.employeeRepository = employeeRepository;
    }

    public List<EmployeeOptionResponse> getDirectReports(String supervisorEmail) {
        Employee supervisor = employeeRepository.findById(supervisorEmail)
                .orElseThrow(() -> new RuntimeException("Employee not found: " + supervisorEmail));

        List<Employee> employees = supervisor.getTeamId() == null
                ? employeeRepository.findBySupervisorIdAndIsActiveTrue(supervisorEmail)
                : employeeRepository.findByTeamIdAndIsActiveTrue(supervisor.getTeamId())
                        .stream()
                        .filter(employee -> !Objects.equals(employee.getEmail(), supervisorEmail))
                        .toList();

        return employees.stream()
                .map(EmployeeOptionResponse::new)
                .toList();
    }
}
