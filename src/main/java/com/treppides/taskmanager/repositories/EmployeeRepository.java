package com.treppides.taskmanager.repositories;

import com.treppides.taskmanager.entities.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface EmployeeRepository extends JpaRepository<Employee, String> {
    boolean existsByEmailIgnoreCase(String email);
    List<Employee> findBySupervisorIdAndIsActiveTrue(String supervisorId);
    List<Employee> findByTeamIdAndIsActiveTrue(Integer teamId);
    List<Employee> findByDepartmentIdAndIsActiveTrue(Integer departmentId);
    List<Employee> findByDepartmentIdAndTeamIdIsNullAndIsActiveTrue(Integer departmentId);
}
