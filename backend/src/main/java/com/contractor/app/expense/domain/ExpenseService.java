package com.contractor.app.expense.domain;

import com.contractor.app.accounting.domain.AccountType;
import com.contractor.app.accounting.domain.ChartAccount;
import com.contractor.app.accounting.domain.JournalEntry;
import com.contractor.app.accounting.domain.JournalLineRequest;
import com.contractor.app.accounting.domain.JournalPostingRequest;
import com.contractor.app.accounting.domain.JournalPostingService;
import com.contractor.app.accounting.infra.ChartAccountRepository;
import com.contractor.app.common.error.ApiException;
import com.contractor.app.common.security.AuthenticatedUser;
import com.contractor.app.common.security.CurrentUser;
import com.contractor.app.expense.api.dto.CreateExpensePaymentRequest;
import com.contractor.app.expense.api.dto.CreateExpenseRequest;
import com.contractor.app.expense.api.dto.ExpenseResponse;
import com.contractor.app.expense.infra.ExpensePaymentRepository;
import com.contractor.app.expense.infra.ExpenseRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExpenseService {
    private static final String PAYABLES_CODE = "2000";
    private static final List<String> PAYMENT_ACCOUNT_CODES = Arrays.asList("1000", "1010");
    private final ExpenseRepository expenses;
    private final ExpensePaymentRepository payments;
    private final ChartAccountRepository accounts;
    private final JournalPostingService journals;

    public ExpenseService(ExpenseRepository expenses, ExpensePaymentRepository payments,
                          ChartAccountRepository accounts, JournalPostingService journals) {
        this.expenses=expenses; this.payments=payments; this.accounts=accounts; this.journals=journals;
    }

    @Transactional
    public ExpenseResponse create(CreateExpenseRequest request) {
        AuthenticatedUser user=CurrentUser.get();
        Long tenantId=user.getTenantId();
        if(request==null) throw ApiException.badRequest("بيانات المصروف مطلوبة.");
        String requestKey=required(request.getClientRequestKey(),120,"مفتاح طلب المصروف");
        if(request.getExpenseDate()==null) throw ApiException.badRequest("تاريخ المصروف مطلوب.");
        BigDecimal amount=money(request.getAmount());
        String category=required(request.getCategoryCode(),40,"تصنيف المصروف");
        String description=required(request.getDescription(),300,"وصف المصروف");
        String supplier=trimToNull(request.getSupplierName());
        if(supplier!=null && supplier.length()>150) throw ApiException.badRequest("اسم المورد أطول من المسموح.");
        validateOptionalId(request.getEquipmentId(),"المعدة");
        validateOptionalId(request.getShiftId(),"الوردية");

        Optional<Expense> prior=expenses.findByTenantIdAndClientRequestKey(tenantId,requestKey);
        if(prior.isPresent()) {
            Expense existing=prior.get();
            if(!sameExpenseRequest(existing,request,amount,category,description,supplier))
                throw ApiException.conflict("مفتاح طلب المصروف مستخدم لبيانات مختلفة.");
            return response(existing);
        }

        ChartAccount expenseAccount=account(tenantId,request.getExpenseAccountId());
        if(expenseAccount.getAccountType()!=AccountType.EXPENSE)
            throw ApiException.badRequest("يجب اختيار حساب من نوع المصروفات.");
        ChartAccount payable=accountByCode(tenantId,PAYABLES_CODE);
        if(payable.getAccountType()!=AccountType.LIABILITY)
            throw ApiException.conflict("الحساب الافتراضي للموردين غير مضبوط كالتزام.");

        String number="EXP-"+UUID.randomUUID().toString().replace("-","").substring(0,20).toUpperCase();
        Expense expense=expenses.saveAndFlush(new Expense(tenantId,number,requestKey,request.getExpenseDate(),
                category,description,supplier,amount,expenseAccount.getId(),request.getEquipmentId(),
                request.getShiftId(),user.getUserId()));
        JournalEntry journal=journals.post(tenantId,user.getUserId(),new JournalPostingRequest(
                "expense:"+requestKey,"EXPENSE",expense.getId(),request.getExpenseDate(),description,null,
                Arrays.asList(
                    new JournalLineRequest(expenseAccount.getId(),description,amount,BigDecimal.ZERO,null,request.getEquipmentId()),
                    new JournalLineRequest(payable.getId(),"مستحق للموردين",BigDecimal.ZERO,amount,null,null))));
        expense.linkJournalEntry(journal.getId());
        expenses.flush();
        return new ExpenseResponse(expense,BigDecimal.ZERO.setScale(2));
    }

    @Transactional
    public ExpenseResponse addPayment(Long expenseId, CreateExpensePaymentRequest request) {
        AuthenticatedUser user=CurrentUser.get();
        Long tenantId=user.getTenantId();
        if(request==null) throw ApiException.badRequest("بيانات السداد مطلوبة.");
        String key=required(request.getPaymentKey(),120,"مفتاح السداد");
        BigDecimal amount=money(request.getAmount());
        if(request.getPaymentDate()==null) throw ApiException.badRequest("تاريخ السداد مطلوب.");
        Expense expense=expenses.findByIdAndTenantIdForUpdate(expenseId,tenantId)
                .orElseThrow(()->ApiException.notFound("المصروف غير موجود."));
        Optional<ExpensePayment> prior=payments.findByTenantIdAndPaymentKey(tenantId,key);
        if(prior.isPresent()){
            ExpensePayment p=prior.get();
            if(!p.getExpenseId().equals(expenseId)||p.getAmount().compareTo(amount)!=0
                    ||!p.getPaymentAccountId().equals(request.getPaymentAccountId())
                    ||!p.getPaymentDate().equals(request.getPaymentDate()))
                throw ApiException.conflict("مفتاح السداد مستخدم لعملية مختلفة.");
            return response(expense);
        }
        BigDecimal paid=payments.sumAmountByTenantAndExpense(tenantId,expenseId);
        if(paid.add(amount).compareTo(expense.getAmount())>0)
            throw ApiException.badRequest("قيمة السداد أكبر من المبلغ المتبقي على المصروف.");
        ChartAccount payable=accountByCode(tenantId,PAYABLES_CODE);
        ChartAccount paymentAccount=account(tenantId,request.getPaymentAccountId());
        if(payable.getAccountType()!=AccountType.LIABILITY)
            throw ApiException.conflict("حساب الموردين الافتراضي غير صالح.");
        if(paymentAccount.getAccountType()!=AccountType.ASSET
                || !PAYMENT_ACCOUNT_CODES.contains(paymentAccount.getAccountCode()))
            throw ApiException.badRequest("السداد يكون من حساب الخزينة 1000 أو البنك 1010 فقط.");
        ExpensePayment payment=payments.saveAndFlush(new ExpensePayment(tenantId,expenseId,key,
                request.getPaymentDate(),amount,paymentAccount.getId(),user.getUserId()));
        JournalEntry journal=journals.post(tenantId,user.getUserId(),new JournalPostingRequest(
                "expense-payment:"+key,"EXPENSE_PAYMENT",payment.getId(),request.getPaymentDate(),
                "سداد مصروف "+expense.getExpenseNumber(),null,Arrays.asList(
                    new JournalLineRequest(payable.getId(),"تخفيض مستحقات الموردين",amount,BigDecimal.ZERO,null,null),
                    new JournalLineRequest(paymentAccount.getId(),"سداد من الخزينة/البنك",BigDecimal.ZERO,amount,null,null))));
        payment.linkJournalEntry(journal.getId());
        payments.flush();
        return response(expense);
    }

    @Transactional(readOnly=true)
    public List<ExpenseResponse> list() {
        Long tenantId=CurrentUser.tenantId();
        return expenses.findByTenantIdOrderByExpenseDateDescIdDesc(tenantId).stream()
                .map(this::response).collect(Collectors.toList());
    }

    private boolean sameExpenseRequest(Expense e,CreateExpenseRequest r,BigDecimal amount,
                                       String category,String description,String supplier) {
        return e.getAmount().compareTo(amount)==0
                && e.getExpenseDate().equals(r.getExpenseDate())
                && e.getCategoryCode().equals(category)
                && e.getDescription().equals(description)
                && java.util.Objects.equals(e.getSupplierName(),supplier)
                && e.getExpenseAccountId().equals(r.getExpenseAccountId())
                && java.util.Objects.equals(e.getEquipmentId(),r.getEquipmentId())
                && java.util.Objects.equals(e.getShiftId(),r.getShiftId());
    }
    private ExpenseResponse response(Expense expense) {
        BigDecimal paid=payments.sumAmountByTenantAndExpense(expense.getTenantId(),expense.getId());
        return new ExpenseResponse(expense,paid.setScale(2,RoundingMode.UNNECESSARY));
    }
    private ChartAccount account(Long tenantId,Long id) {
        if(id==null||id<=0) throw ApiException.badRequest("الحساب المحاسبي مطلوب.");
        ChartAccount a=accounts.findByIdAndTenantId(id,tenantId)
                .orElseThrow(()->ApiException.notFound("الحساب المحاسبي غير موجود."));
        if(!a.isActive()) throw ApiException.badRequest("الحساب المحاسبي معطّل.");
        return a;
    }
    private ChartAccount accountByCode(Long tenantId,String code) {
        return accounts.findByTenantIdAndAccountCodeIgnoreCase(tenantId,code)
                .filter(ChartAccount::isActive)
                .orElseThrow(()->ApiException.conflict("لم يتم إعداد الحساب الافتراضي رقم "+code+"."));
    }
    private BigDecimal money(BigDecimal value) {
        if(value==null||value.signum()<=0) throw ApiException.badRequest("المبلغ يجب أن يكون أكبر من صفر.");
        try{return value.setScale(2,RoundingMode.UNNECESSARY);}
        catch(ArithmeticException ex){throw ApiException.badRequest("المبلغ لا يقبل أكثر من خانتين عشريتين.");}
    }
    private String required(String value,int max,String label) {
        String v=value==null?"":value.trim();
        if(v.isEmpty()||v.length()>max) throw ApiException.badRequest(label+" مطلوب وبحد أقصى "+max+" حرفًا.");
        return v;
    }
    private void validateOptionalId(Long id,String label) {
        if(id!=null&&id<=0) throw ApiException.badRequest("معرّف "+label+" غير صالح.");
    }
    private String trimToNull(String value){return value==null||value.trim().isEmpty()?null:value.trim();}
}
