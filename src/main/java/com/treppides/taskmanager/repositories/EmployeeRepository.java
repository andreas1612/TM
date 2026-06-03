package com.treppides.taskmanager.repositories;

import com.treppides.taskmanager.entities.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface EmployeeRepository extends JpaRepository<Employee, String> {
    List<Employee> findBySupervisorIdAndIsActiveTrue(String supervisorId);
}