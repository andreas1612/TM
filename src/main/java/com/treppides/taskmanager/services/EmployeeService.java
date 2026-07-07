package com.treppides.taskmanager.services;

import com.treppides.taskmanager.dto.EmployeeOptionResponse;
import com.treppides.taskmanager.entities.Employee;
import com.treppides.taskmanager.repositories.EmployeeRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class EmployeeService {

    // Leadership positions that may assign tasks to one another regardless of
    // the team/department/supervisor hierarchy. Compared case-insensitively.
    private static final Set<String> LEADERSHIP_POSITIONS = Set.of(
            "senior leader",
            "principal",
            "director"
    );

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

    public List<EmployeeOptionResponse> getAssignableEmployees(String email) {
        Employee currentEmployee = employeeRepository.findById(email)
                .orElseThrow(() -> new RuntimeException("Employee not found: " + email));

        Map<String, Employee> employeesByEmail = new LinkedHashMap<>();

        if (currentEmployee.getTeamId() != null) {
            employeeRepository.findByTeamIdAndIsActiveTrue(currentEmployee.getTeamId())
                    .stream()
                    .filter(employee -> !Objects.equals(employee.getEmail(), email))
                    .forEach(employee -> employeesByEmail.put(employee.getEmail(), employee));
        } else {
            employeeRepository.findByDepartmentIdAndIsActiveTrue(currentEmployee.getDepartment())
                    .stream()
                    .filter(employee -> !Objects.equals(employee.getEmail(), email))
                    .forEach(employee -> employeesByEmail.put(employee.getEmail(), employee));
        }

        employeeRepository.findBySupervisorIdAndIsActiveTrue(email)
                .stream()
                .filter(employee -> !Objects.equals(employee.getEmail(), email))
                .forEach(employee -> employeesByEmail.putIfAbsent(employee.getEmail(), employee));

        // Leadership roles (Senior Leader, Principal, Director) may additionally
        // assign tasks to one another, regardless of the hierarchy above.
        if (isLeadershipPosition(currentEmployee.getPosition())) {
            employeeRepository.findByPositionInAndIsActiveTrue(List.copyOf(LEADERSHIP_POSITIONS))
                    .stream()
                    .filter(employee -> !Objects.equals(employee.getEmail(), email))
                    .filter(employee -> isLeadershipPosition(employee.getPosition()))
                    .forEach(employee -> employeesByEmail.putIfAbsent(employee.getEmail(), employee));
        }

        return employeesByEmail.values()
                .stream()
                .map(EmployeeOptionResponse::new)
                .toList();
    }

    private boolean isLeadershipPosition(String position) {
        return position != null
                && LEADERSHIP_POSITIONS.contains(position.trim().toLowerCase(Locale.ROOT));
    }
}
