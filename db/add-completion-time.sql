-- Migration: add time-to-complete tracking to Tasks
-- Run against the InternalTools database before deploying the completion-time feature.
-- Safe to run once; all columns are additive and nullable (except the flag, which defaults to 0).

ALTER TABLE dbo.Tasks ADD
    CalculatedMinutes    INT NULL,
    CompletionMinutes    INT NULL,
    CompletionTimeEdited BIT NOT NULL
        CONSTRAINT DF_Tasks_CompletionTimeEdited DEFAULT 0;
