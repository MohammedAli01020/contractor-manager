package com.contractor.app.expense.infra;
import com.contractor.app.expense.domain.Expense;
import java.util.List;
import java.util.Optional;
import javax.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
public interface ExpenseRepository extends JpaRepository<Expense, Long> {
 Optional<Expense> findByIdAndTenantId(Long id, Long tenantId);
 Optional<Expense> findByTenantIdAndClientRequestKey(Long tenantId, String clientRequestKey);
 @Lock(LockModeType.PESSIMISTIC_WRITE)
 @Query("select e from Expense e where e.id = :id and e.tenantId = :tenantId")
 Optional<Expense> findByIdAndTenantIdForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);
 List<Expense> findByTenantIdOrderByExpenseDateDescIdDesc(Long tenantId);
 boolean existsByTenantIdAndExpenseNumber(Long tenantId, String expenseNumber);
}
