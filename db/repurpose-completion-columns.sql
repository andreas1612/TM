-- Migration: repurpose the completion-time columns on dbo.Tasks.
--
--   CompletionMinutes     = system-calculated time-to-complete (minutes).
--   CompletionTimeEdited  = time the EMPLOYEE reported (minutes); NULL when none.
--                           Widened from BIT to INT. Reports show this when not null,
--                           otherwise CompletionMinutes. Also now holds the running
--                           total accumulated by the daily time-log page.
--   CalculatedMinutes     = dropped (redundant; the calculated value lives in
--                           CompletionMinutes).
--
-- Run once against InternalTools. Supersedes the never-applied add-logged-minutes.sql
-- (no separate LoggedMinutes column is added).

-- 1. Drop the old bit default, then widen the flag column to hold minutes.
ALTER TABLE dbo.Tasks DROP CONSTRAINT DF_Tasks_CompletionTimeEdited;
GO
ALTER TABLE dbo.Tasks ALTER COLUMN CompletionTimeEdited INT NULL;
GO

-- 2. Backfill the employee-reported time. Where the value had previously been
--    overridden (old flag = 1), the reported minutes were stored in CompletionMinutes;
--    otherwise no time was reported.
UPDATE dbo.Tasks
SET CompletionTimeEdited = CASE WHEN CompletionTimeEdited = 1 THEN CompletionMinutes END;
GO

-- 3. CompletionMinutes now holds the system-calculated value.
UPDATE dbo.Tasks
SET CompletionMinutes = CalculatedMinutes
WHERE CalculatedMinutes IS NOT NULL;
GO

-- 4. Drop the redundant column.
ALTER TABLE dbo.Tasks DROP COLUMN CalculatedMinutes;
GO
