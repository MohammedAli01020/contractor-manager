-- Optional link from agreement components to reusable tenant catalog definitions.
-- Existing compensation components remain valid and retain their historical labels/rates.
ALTER TABLE compensation_components
    ADD COLUMN financial_item_definition_id BIGINT NULL AFTER compensation_agreement_id,
    ADD KEY ix_compensation_components_financial_item
        (financial_item_definition_id, tenant_id),
    ADD CONSTRAINT fk_compensation_components_financial_item
        FOREIGN KEY (financial_item_definition_id, tenant_id)
        REFERENCES workforce_financial_item_definitions (id, tenant_id);
