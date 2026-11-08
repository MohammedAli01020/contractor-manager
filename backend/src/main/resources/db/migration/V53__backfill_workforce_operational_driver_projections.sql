-- Backfill the legacy operational Driver projection for canonical Workforce members.
-- WorkforceMember remains the source of identity and active state. The projection is
-- retained only because the current shift aggregate, legacy shift API and worker ledger
-- still reference drivers.id. New assignments create/reconcile this projection in
-- WorkforceAssignmentService; this migration covers assignments created before that path.
--
-- Never overwrite an existing projection: it may contain availability or historical rates
-- that must be preserved. Zero rates are compatibility placeholders; approved compensation
-- agreements remain authoritative in ShiftCompensationResolver.

INSERT INTO drivers (
    tenant_id,
    workforce_member_id,
    name,
    phone,
    national_id,
    worker_type,
    availability,
    unavailable_reason,
    morning_rate,
    evening_rate,
    night_rate,
    notes,
    active
)
SELECT
    m.tenant_id,
    m.id,
    m.full_name,
    m.phone,
    m.national_id,
    CASE
        WHEN EXISTS (
            SELECT 1
            FROM workforce_assignments driver_assignment
            JOIN workforce_roles driver_role
              ON driver_role.id = driver_assignment.workforce_role_id
             AND driver_role.tenant_id = driver_assignment.tenant_id
            WHERE driver_assignment.tenant_id = m.tenant_id
              AND driver_assignment.workforce_member_id = m.id
              AND driver_assignment.status IN ('ACTIVE', 'ENDED')
              AND driver_role.code IN ('DRIVER', 'EQUIPMENT_OPERATOR')
        ) THEN 'DRIVER'
        ELSE 'WORKER'
    END,
    'AVAILABLE',
    NULL,
    0.00,
    0.00,
    0.00,
    m.notes,
    m.active
FROM workforce_members m
WHERE EXISTS (
    SELECT 1
    FROM workforce_assignments assignment
    JOIN workforce_roles role
      ON role.id = assignment.workforce_role_id
     AND role.tenant_id = assignment.tenant_id
    WHERE assignment.tenant_id = m.tenant_id
      AND assignment.workforce_member_id = m.id
      AND assignment.status IN ('ACTIVE', 'ENDED')
      AND role.code IN ('DRIVER', 'EQUIPMENT_OPERATOR', 'WORKER')
)
AND NOT EXISTS (
    SELECT 1
    FROM drivers existing_driver
    WHERE existing_driver.tenant_id = m.tenant_id
      AND existing_driver.workforce_member_id = m.id
);
