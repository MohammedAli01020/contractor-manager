package com.contractor.app.expense.api;

import com.contractor.app.common.security.CurrentUser;
import com.contractor.app.expense.api.dto.CreateExpensePaymentRequest;
import com.contractor.app.expense.api.dto.CreateExpenseRequest;
import com.contractor.app.expense.api.dto.ExpenseResponse;
import com.contractor.app.expense.domain.ExpenseService;
import java.util.List;
import javax.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/expenses")
@PreAuthorize("hasAnyRole('ADMIN','OPERATOR','VIEWER')")
public class ExpenseController {
    private final ExpenseService service;
    public ExpenseController(ExpenseService service){this.service=service;}

    @GetMapping
    public List<ExpenseResponse> list(){return service.list();}

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public ExpenseResponse create(@Valid @RequestBody CreateExpenseRequest request){
        return service.create(request);
    }

    @PostMapping("/{expenseId}/payments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public ExpenseResponse addPayment(@PathVariable Long expenseId,
                                      @Valid @RequestBody CreateExpensePaymentRequest request){
        return service.addPayment(expenseId,request);
    }
}
