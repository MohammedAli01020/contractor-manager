-- Journal rows are immutable once persisted. Corrections use a separate reversal journal.
-- Keep INSERT available to the posting transaction; UPDATE/DELETE are rejected at the DB boundary.
CREATE TRIGGER trg_journal_entries_prevent_update
BEFORE UPDATE ON journal_entries
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'POSTED_JOURNAL_IS_IMMUTABLE';
END;

CREATE TRIGGER trg_journal_entries_prevent_delete
BEFORE DELETE ON journal_entries
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'POSTED_JOURNAL_CANNOT_BE_DELETED';
END;

CREATE TRIGGER trg_journal_lines_prevent_update
BEFORE UPDATE ON journal_lines
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'POSTED_JOURNAL_LINES_ARE_IMMUTABLE';
END;

CREATE TRIGGER trg_journal_lines_prevent_delete
BEFORE DELETE ON journal_lines
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'POSTED_JOURNAL_LINES_CANNOT_BE_DELETED';
END;
