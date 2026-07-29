package com.treppides.taskmanager.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class SupportTicketRequest {

    @NotBlank(message = "Category is required")
    @Size(max = 100)
    private String category;

    @NotBlank(message = "Message is required")
    @Size(max = 5000)
    private String message;

    @NotBlank(message = "Type is required")
    private String type; // "tech" or "it"

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
}
