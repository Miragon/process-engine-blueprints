-- VanillaBP loads the workflow aggregate by the id it reads back from the engine as a JSON string,
-- so the leasing application is keyed by text. The added columns hold the state of the workflow
-- that used to live in Zeebe only: the open clarification user task and the two gateway decisions.

ALTER TABLE leasing_application
    ALTER COLUMN application_id TYPE VARCHAR(36) USING application_id::text;

ALTER TABLE leasing_application
    ADD COLUMN clarify_alternative_task_id VARCHAR(255),
    ADD COLUMN clarification_requested_at  TIMESTAMP(6),
    ADD COLUMN alternative_found           BOOLEAN,
    ADD COLUMN cancellation_possible       BOOLEAN;
