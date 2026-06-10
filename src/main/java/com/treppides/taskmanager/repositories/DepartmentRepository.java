package com.treppides.taskmanager.repositories;

import com.treppides.taskmanager.entities.Department;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepartmentRepository extends JpaRepository<Department, Integer> {
}
