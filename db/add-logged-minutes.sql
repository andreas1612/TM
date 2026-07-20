-- Daily time-logging feature: running total of user-reported minutes per task.
-- Accumulated from the 4pm daily time-log prompt; used as the default in the
-- completion popup when present.
IF NOT EXISTS (
    SELECT 1 FROM sys.columns
    WHERE object_id = OBJECT_ID('dbo.Tasks') AND name = 'LoggedMinutes'
)
BEGIN
    ALTER TABLE dbo.Tasks ADD LoggedMinutes INT NULL;
END
GO
