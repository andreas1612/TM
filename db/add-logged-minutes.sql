-- Daily time-logging: running total of employee-reported minutes per task.
-- Accumulated (+=) from the daily time-log page; every change is also recorded in
-- TaskHistory (field "TimeLogged"). Used to prefill the completion popup.
-- Safe to run once; additive and nullable.
IF NOT EXISTS (
    SELECT 1 FROM sys.columns
    WHERE object_id = OBJECT_ID('dbo.Tasks') AND name = 'LoggedMinutes'
)
BEGIN
    ALTER TABLE dbo.Tasks ADD LoggedMinutes INT NULL;
END
GO
