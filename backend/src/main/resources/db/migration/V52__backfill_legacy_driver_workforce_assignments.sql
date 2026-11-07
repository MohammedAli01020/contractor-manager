-- Backfill operational Workforce roles for existing active legacy driver/worker rows.
-- The mapping is deterministic from the pre-existing worker_type value; no names or rates are inferred.
-- Members with any non-cancelled operational role assignment are left untouched.
INSERT INTO workforce_assignments (
    tenant_id,
    workforce_member_id,
    workforce_role_id,
    site_id,
    effective_from,
    effective_to,
    status,
    notes
)
SELECT
    d.tenant_id,
    d.workforce_member_id,
    wr.id,
    NULL,
    DATE(d.created_at),
    NULL,
    'ACTIVE',
    'Migrated from legacy drivers.worker_type during WF12A'
FROM drivers d
JOIN workforce_roles wr
  ON wr.tenant_id = d.tenant_id
 AND wr.code = CASE d.worker_type
                   WHEN 'WORKER' THEN 'WORKER'
                   ELSE 'DRIVER'
               END
WHERE d.active = TRUE
  AND NOT EXISTS (
      SELECT 1
      FROM workforce_assignments existing_assignment
      JOIN workforce_roles existing_role
        ON existing_role.id = existing_assignment.workforce_role_id
       AND existing_role.tenant_id = existing_assignment.tenant_id
      WHERE existing_assignment.tenant_id = d.tenant_id
        AND existing_assignment.workforce_member_id = d.workforce_member_id
        -- Any prior operational assignment, including an explicit cancellation, is authoritative.
        AND existing_role.code IN ('DRIVER', 'EQUIPMENT_OPERATOR', 'WORKER')
  );
