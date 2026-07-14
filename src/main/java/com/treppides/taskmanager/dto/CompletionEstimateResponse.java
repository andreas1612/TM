package com.treppides.taskmanager.dto;

/**
 * The system-calculated time-to-complete for a task, shown as the editable default
 * when a user marks the task Completed.
 */
public class CompletionEstimateResponse {

    private final long calculatedMinutes;

    public CompletionEstimateResponse(long calculatedMinutes) {
        this.calculatedMinutes = calculatedMinutes;
    }

    public long getCalculatedMinutes() { return calculatedMinutes; }
}
