CREATE INDEX idx_obligations_operational_reporting
    ON obligations(company_id, site_id, assigned_operator_id, starts_at, due_at, id);

CREATE INDEX idx_obligations_source_plan
    ON obligations(source_plan_id);

CREATE INDEX idx_operational_exceptions_execution
    ON operational_exceptions(execution_id);
