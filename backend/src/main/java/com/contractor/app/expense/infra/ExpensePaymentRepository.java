package com.contractor.app.expense.infra;

import com.contractor.app.expense.domain.ExpensePayment;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExpensePaymentRepository extends JpaRepository<ExpensePayment, Long> {

    Optional<ExpensePayment> findByTenantIdAndPaymentKey(Long tenantId, String paymentKey);

    List<ExpensePayment> findByTenantIdAndExpenseIdOrderByPaymentDateAscIdAsc(Long tenantId, Long expenseId);

    @Query("select coalesce(sum(p.amount), 0) from ExpensePayment p "
            + "where p.tenantId = :tenantId and p.expenseId = :expenseId")
    BigDecimal sumAmountByTenantAndExpense(@Param("tenantId") Long tenantId,
                                           @Param("expenseId") Long expenseId);
}
