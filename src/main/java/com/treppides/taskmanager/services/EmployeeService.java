package com.treppides.taskmanager.services;

import com.treppides.taskmanager.dto.EmployeeOptionResponse;
import com.treppides.taskmanager.entities.Employee;
import com.treppides.taskmanager.repositories.EmployeeRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EmployeeService {

    private final EmployeeRepository employeeRepository;

    public EmployeeService(EmployeeRepository employeeRepository) {
        this.employeeRepository = employeeRepository;
    }

    public List<EmployeeOptionResponse> getDirectReports(String supervisorEmail) {
        List<Employee> employees =
                employeeRepository.findBySupervisorIdAndIsActiveTrue(supervisorEmail);

        return employees.stream()
                .map(EmployeeOptionResponse::new)
                .toList();
    }
}