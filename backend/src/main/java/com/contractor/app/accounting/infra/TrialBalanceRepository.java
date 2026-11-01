package com.contractor.app.accounting.infra;

import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Read-only trial-balance aggregation. Tenant and date predicates are applied in SQL;
 * all account rows are retained even when no posted journal line exists in the range.
 */
@Repository
public class TrialBalanceRepository {

    private final JdbcTemplate jdbcTemplate;

    public TrialBalanceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<TrialBalanceAccountRow> findTrialBalance(Long tenantId,
                                                          LocalDate fromDate,
                                                          LocalDate toDate) {
        String sql =
                "SELECT a.id AS account_id, a.account_code AS account_code, a.name AS account_name, " +
                "a.account_type AS account_type, a.active AS active, " +
                "COALESCE(SUM(CASE WHEN je.accounting_date < ? THEN jl.debit ELSE 0 END), 0.00) AS opening_debit, " +
                "COALESCE(SUM(CASE WHEN je.accounting_date < ? THEN jl.credit ELSE 0 END), 0.00) AS opening_credit, " +
                "COALESCE(SUM(CASE WHEN je.accounting_date >= ? AND je.accounting_date <= ? THEN jl.debit ELSE 0 END), 0.00) AS period_debit, " +
                "COALESCE(SUM(CASE WHEN je.accounting_date >= ? AND je.accounting_date <= ? THEN jl.credit ELSE 0 END), 0.00) AS period_credit " +
                "FROM chart_of_accounts a " +
                "LEFT JOIN journal_lines jl ON jl.account_id = a.id AND jl.tenant_id = a.tenant_id " +
                "LEFT JOIN journal_entries je ON je.id = jl.journal_entry_id " +
                "AND je.tenant_id = jl.tenant_id AND je.status = 'POSTED' AND je.accounting_date <= ? " +
                "WHERE a.tenant_id = ? " +
                "GROUP BY a.id, a.account_code, a.name, a.account_type, a.active " +
                "ORDER BY a.account_code ASC, a.id ASC";

        return jdbcTemplate.query(sql, (rs, rowNum) -> new TrialBalanceAccountRow(
                rs.getLong("account_id"),
                rs.getString("account_code"),
                rs.getString("account_name"),
                rs.getString("account_type"),
                rs.getBoolean("active"),
                rs.getBigDecimal("opening_debit"),
                rs.getBigDecimal("opening_credit"),
                rs.getBigDecimal("period_debit"),
                rs.getBigDecimal("period_credit")),
                fromDate, fromDate, fromDate, toDate, fromDate, toDate, toDate, tenantId);
    }
}
