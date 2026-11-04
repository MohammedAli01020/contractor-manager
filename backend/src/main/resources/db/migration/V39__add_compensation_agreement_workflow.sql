-- Workflow metadata for auditable, atomic agreement replacement.
ALTER TABLE compensation_agreements
    ADD COLUMN supersedes_agreement_id BIGINT NULL AFTER source,
    ADD COLUMN change_reason VARCHAR(500) NULL AFTER supersedes_agreement_id;

ALTER TABLE compensation_agreements
    ADD KEY ix_compensation_agreements_supersedes (supersedes_agreement_id, tenant_id),
    ADD CONSTRAINT fk_compensation_agreements_supersedes
        FOREIGN KEY (supersedes_agreement_id, tenant_id)
        REFERENCES compensation_agreements (id, tenant_id),
    DROP CHECK ck_compensation_agreements_status,
    ADD CONSTRAINT ck_compensation_agreements_status
        CHECK (status IN ('DRAFT', 'APPROVED', 'SUPERSEDED', 'EXPIRED', 'CANCELLED')),
    DROP CHECK ck_compensation_agreements_approval,
    ADD CONSTRAINT ck_compensation_agreements_approval
        CHECK (
            (status IN ('DRAFT', 'CANCELLED') AND approved_by_user_id IS NULL AND approved_at IS NULL)
            OR (status IN ('APPROVED', 'SUPERSEDED', 'EXPIRED')
                AND approved_by_user_id IS NOT NULL AND approved_at IS NOT NULL)
        ),
    ADD CONSTRAINT ck_compensation_agreements_replacement_reason
        CHECK (supersedes_agreement_id IS NULL OR
               (change_reason IS NOT NULL AND TRIM(change_reason) <> ''));
