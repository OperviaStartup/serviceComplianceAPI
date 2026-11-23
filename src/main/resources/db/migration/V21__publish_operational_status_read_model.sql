-- Versioned read contract for Reporting. Source tables and writes remain with their owners.
CREATE VIEW operational_status_facts_v1 AS
SELECT o.id, o.company_id, o.site_id, o.site_name, o.title, o.assigned_operator_id,
       o.starts_at, o.due_at, COALESCE(o.timezone, 'America/Lima') AS timezone,
       o.source_plan_id, o.source_plan_version,
       COALESCE(o.starts_at, o.due_at) AS sort_at,
       p.service_id, x.state AS execution_state,
       COALESCE(x.exception_count, 0) AS exception_count
FROM obligations o
LEFT JOIN service_plans p
    ON p.id = o.source_plan_id AND p.business_version = o.source_plan_version
    AND p.company_id = o.company_id AND p.site_id = o.site_id
LEFT JOIN executions x ON x.obligation_id = o.id AND x.state <> 'LEGACY'
WHERE o.assigned_operator_id IS NOT NULL;

REVOKE ALL ON operational_status_facts_v1 FROM PUBLIC;

CREATE INDEX idx_obligations_operational_status_scope_period
    ON obligations(company_id, site_id, (COALESCE(starts_at, due_at)), id)
    WHERE assigned_operator_id IS NOT NULL;
