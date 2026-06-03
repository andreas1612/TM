package com.treppides.taskmanager.dto;

import com.treppides.taskmanager.entities.Employee;

public class EmployeeOptionResponse {

    private String email;
    private String fullName;

    public EmployeeOptionResponse(Employee employee) {
        this.email = employee.getEmail();
        this.fullName = employee.getFullName();
    }

    public String getEmail() {
        return email;
    }

    public String getFullName() {
        return fullName;
    }
}