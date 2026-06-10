package com.treppides.taskmanager.dto;

import com.treppides.taskmanager.entities.Employee;

public class EmployeeOptionResponse {

    private String email;
    private String fullName;
    private Integer teamId;

    public EmployeeOptionResponse(Employee employee) {
        this.email = employee.getEmail();
        this.fullName = employee.getFullName();
        this.teamId = employee.getTeamId();
    }

    public String getEmail() {
        return email;
    }

    public String getFullName() {
        return fullName;
    }

    public Integer getTeamId() {
        return teamId;
    }
}
