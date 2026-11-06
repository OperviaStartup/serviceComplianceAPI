ALTER TABLE obligations ADD COLUMN source_plan_version INTEGER;
ALTER TABLE obligations ADD COLUMN source_definition_version BIGINT;
ALTER TABLE obligations ADD CONSTRAINT ck_generated_obligation_source_versions
    CHECK ((source_plan_id IS NULL AND source_plan_version IS NULL
            AND source_definition_id IS NULL AND source_definition_version IS NULL)
        OR (source_plan_id IS NOT NULL AND source_plan_version IS NOT NULL
            AND source_definition_id IS NOT NULL AND source_definition_version IS NOT NULL));
