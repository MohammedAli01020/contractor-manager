-- Source documents must stay consistent with their immutable posted journals.
-- Permit the one-time NULL -> journal link written by the creating transaction,
-- but never permit edits to financial source fields or deletion afterward.

CREATE TRIGGER trg_expenses_prevent_financial_update
BEFORE UPDATE ON expenses
FOR EACH ROW
BEGIN
    IF NOT (NEW.tenant_id <=> OLD.tenant_id)
       OR NOT (NEW.expense_number <=> OLD.expense_number)
       OR NOT (NEW.client_request_key <=> OLD.client_request_key)
       OR NOT (NEW.expense_date <=> OLD.expense_date)
       OR NOT (NEW.category_code <=> OLD.category_code)
       OR NOT (NEW.description <=> OLD.description)
       OR NOT (NEW.supplier_name <=> OLD.supplier_name)
       OR NOT (NEW.amount <=> OLD.amount)
       OR NOT (NEW.expense_account_id <=> OLD.expense_account_id)
       OR NOT (NEW.equipment_id <=> OLD.equipment_id)
       OR NOT (NEW.shift_id <=> OLD.shift_id)
       OR NOT (NEW.created_by_user_id <=> OLD.created_by_user_id)
       OR (OLD.journal_entry_id IS NOT NULL
           AND NOT (NEW.journal_entry_id <=> OLD.journal_entry_id)) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'POSTED_EXPENSE_IS_IMMUTABLE';
    END IF;
END;

CREATE TRIGGER trg_expenses_prevent_delete
BEFORE DELETE ON expenses
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'POSTED_EXPENSE_CANNOT_BE_DELETED';
END;

CREATE TRIGGER trg_expense_payments_prevent_financial_update
BEFORE UPDATE ON expense_payments
FOR EACH ROW
BEGIN
    IF NOT (NEW.tenant_id <=> OLD.tenant_id)
       OR NOT (NEW.expense_id <=> OLD.expense_id)
       OR NOT (NEW.payment_key <=> OLD.payment_key)
       OR NOT (NEW.payment_date <=> OLD.payment_date)
       OR NOT (NEW.amount <=> OLD.amount)
       OR NOT (NEW.payment_account_id <=> OLD.payment_account_id)
       OR NOT (NEW.created_by_user_id <=> OLD.created_by_user_id)
       OR (OLD.journal_entry_id IS NOT NULL
           AND NOT (NEW.journal_entry_id <=> OLD.journal_entry_id)) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'POSTED_EXPENSE_PAYMENT_IS_IMMUTABLE';
    END IF;
END;

CREATE TRIGGER trg_expense_payments_prevent_delete
BEFORE DELETE ON expense_payments
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'POSTED_EXPENSE_PAYMENT_CANNOT_BE_DELETED';
END;
