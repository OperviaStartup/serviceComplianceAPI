-- Índices adicionales: no reescriben obligaciones, ejecuciones ni decisiones existentes.
CREATE INDEX idx_execution_history_owner_recorded
    ON executions(operator_id, (COALESCE(submitted_at, executed_at)) DESC, id DESC)
    WHERE state IN ('SUBMITTED', 'LEGACY') AND result IS NOT NULL;

CREATE INDEX idx_compliance_results_execution_version
    ON compliance_results(execution_id, decision_version DESC, decided_at DESC);
