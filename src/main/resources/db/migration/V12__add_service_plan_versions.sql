ALTER TABLE service_plans DROP CONSTRAINT ck_service_plans_state;
ALTER TABLE service_plans ADD CONSTRAINT ck_service_plans_state
    CHECK (state IN ('DRAFT', 'ACTIVE', 'SUPERSEDED'));

ALTER TABLE service_plans ADD COLUMN series_id UUID;
ALTER TABLE service_plans ADD COLUMN business_version INTEGER NOT NULL DEFAULT 1;
ALTER TABLE service_plans ADD COLUMN supersedes_plan_id BIGINT REFERENCES service_plans(id);
ALTER TABLE service_plans ADD COLUMN activated_by BIGINT REFERENCES users(id);
ALTER TABLE service_plans ADD COLUMN activated_at TIMESTAMP WITH TIME ZONE;

UPDATE service_plans SET series_id = md5('service-plan-' || id::text)::uuid WHERE series_id IS NULL;
ALTER TABLE service_plans ALTER COLUMN series_id SET NOT NULL;
ALTER TABLE service_plans ADD CONSTRAINT uq_service_plan_series_version
    UNIQUE (series_id, business_version);

CREATE INDEX idx_service_plan_active_overlap
    ON service_plans(company_id, service_id, site_id, start_date, end_date)
    WHERE state = 'ACTIVE';
