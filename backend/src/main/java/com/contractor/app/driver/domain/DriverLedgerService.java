package com.contractor.app.driver.domain;

import com.contractor.app.accounting.domain.ChartAccount;
import com.contractor.app.accounting.domain.DefaultChartOfAccountsService;
import com.contractor.app.accounting.domain.SystemAccountCode;
import com.contractor.app.accounting.domain.AccountType;
import com.contractor.app.accounting.domain.JournalEntry;
import com.contractor.app.accounting.domain.JournalLineRequest;
import com.contractor.app.accounting.domain.JournalPostingRequest;
import com.contractor.app.accounting.domain.JournalPostingService;
import com.contractor.app.accounting.infra.ChartAccountRepository;
import com.contractor.app.accounting.infra.JournalEntryRepository;
import com.contractor.app.common.error.ApiException;
import com.contractor.app.common.security.AuthenticatedUser;
import com.contractor.app.common.security.CurrentUser;
import com.contractor.app.driver.api.dto.DriverLedgerEntryResponse;
import com.contractor.app.driver.api.dto.DriverLedgerPaymentRequest;
import com.contractor.app.driver.api.dto.DriverLedgerAdvanceRequest;
import com.contractor.app.driver.infra.DriverLedgerEntryRepository;
import com.contractor.app.driver.infra.DriverRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DriverLedgerService {
    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private final DriverRepository drivers;
    private final DriverLedgerEntryRepository ledger;
    private final ChartAccountRepository accounts;
    private final JournalEntryRepository journalsRepo;
    private final JournalPostingService journals;
    private final DefaultChartOfAccountsService defaultAccounts;

    public DriverLedgerService(DriverRepository drivers, DriverLedgerEntryRepository ledger,
            ChartAccountRepository accounts, JournalEntryRepository journalsRepo, JournalPostingService journals,
            DefaultChartOfAccountsService defaultAccounts) {
        this.drivers=drivers; this.ledger=ledger; this.accounts=accounts; this.journalsRepo=journalsRepo;
        this.journals=journals; this.defaultAccounts=defaultAccounts;
    }

    @Transactional(readOnly=true)
    public List<DriverLedgerEntryResponse> statement(Long driverId) {
        Long tenantId=CurrentUser.tenantId();
        drivers.findByIdAndTenantId(driverId,tenantId).orElseThrow(()->ApiException.notFound("العامل غير موجود."));
        BigDecimal running=ZERO;
        List<DriverLedgerEntryResponse> result=new ArrayList<>();
        for(DriverLedgerEntry e:ledger.findByTenantIdAndDriverIdOrderByTransactionDateAscIdAsc(tenantId,driverId)) {
            running=running.add(e.getBalanceEffect());
            result.add(new DriverLedgerEntryResponse(e,running));
        }
        return result;
    }

    @Transactional
    public DriverLedgerEntryResponse pay(Long driverId, DriverLedgerPaymentRequest request) {
        AuthenticatedUser user=CurrentUser.get();
        Long tenantId=user.getTenantId();
        if(request==null) throw ApiException.badRequest("بيانات صرف المستحقات مطلوبة.");
        String key=required(request.getClientRequestKey(),120,"مفتاح العملية");
        String description=required(request.getDescription(),300,"وصف العملية");
        BigDecimal amount=money(request.getAmount());
        if(request.getPaymentDate()==null) throw ApiException.badRequest("تاريخ الصرف مطلوب.");

        drivers.findByIdAndTenantIdForUpdate(driverId,tenantId)
                .orElseThrow(()->ApiException.notFound("العامل غير موجود."));
        Optional<DriverLedgerEntry> prior=ledger.findByTenantIdAndClientRequestKey(tenantId,key);
        if(prior.isPresent()) {
            DriverLedgerEntry p=prior.get();
            if(!p.getDriverId().equals(driverId)||!p.getAmount().equals(amount)
                    ||!p.getTransactionDate().equals(request.getPaymentDate())
                    ||!p.getDescription().equals(description)||!"PAYMENT".equals(p.getTransactionType()))
                throw ApiException.conflict("مفتاح العملية مستخدم لعملية أخرى.");
            return new DriverLedgerEntryResponse(p,ledger.sumBalance(tenantId,driverId));
        }
        BigDecimal balance=ledger.sumBalance(tenantId,driverId);
        if(balance.compareTo(amount)<0) throw ApiException.badRequest("المبلغ أكبر من مستحقات العامل المتاحة للصرف.");
        defaultAccounts.initializeForTenant(tenantId);
        ChartAccount payable=defaultAccounts.requireSystemAccount(tenantId,SystemAccountCode.DRIVER_PAYABLE);
        ChartAccount cash=accounts.findByIdAndTenantId(request.getPaymentAccountId(),tenantId)
                .filter(ChartAccount::isActive).orElseThrow(()->ApiException.notFound("حساب الخزينة أو البنك غير موجود."));
        if(cash.getAccountType()!=AccountType.ASSET || !cash.isCashBankAccount())
            throw ApiException.badRequest("الصرف يكون من حساب خزينة أو بنك مُعرّف لهذا الغرض فقط.");
        JournalEntry journal=journals.post(tenantId,user.getUserId(),new JournalPostingRequest(
                "driver-payment:"+key,"DRIVER_PAYMENT",null,request.getPaymentDate(),description,null,Arrays.asList(
                new JournalLineRequest(payable.getId(),"صرف مستحقات العامل",amount,BigDecimal.ZERO,driverId,null),
                new JournalLineRequest(cash.getId(),"صرف من الخزينة/البنك",BigDecimal.ZERO,amount,null,null))));
        DriverLedgerEntry entry=ledger.saveAndFlush(new DriverLedgerEntry(tenantId,driverId,"PAYMENT",
                request.getPaymentDate(),amount,amount.negate(),description,key,null,journal.getId(),user.getUserId()));
        return new DriverLedgerEntryResponse(entry,balance.subtract(amount));
    }


    @Transactional
    public DriverLedgerEntryResponse advance(Long driverId, DriverLedgerAdvanceRequest request) {
        AuthenticatedUser user=CurrentUser.get();
        Long tenantId=user.getTenantId();
        if(request==null) throw ApiException.badRequest("بيانات السلفة مطلوبة.");
        String key=required(request.getClientRequestKey(),120,"مفتاح العملية");
        String description=required(request.getDescription(),300,"وصف العملية");
        BigDecimal amount=money(request.getAmount());
        if(request.getAdvanceDate()==null) throw ApiException.badRequest("تاريخ السلفة مطلوب.");
        String type=request.getAdvanceType()==null?"":request.getAdvanceType().trim().toUpperCase(java.util.Locale.ROOT);
        if(!Arrays.asList("AGAINST_EARNED","RECOVERABLE").contains(type))
            throw ApiException.badRequest("نوع السلفة غير صالح.");

        drivers.findByIdAndTenantIdForUpdate(driverId,tenantId)
                .orElseThrow(()->ApiException.notFound("العامل غير موجود."));
        String ledgerType="AGAINST_EARNED".equals(type)?"ADVANCE":"RECOVERABLE_ADVANCE";
        Optional<DriverLedgerEntry> prior=ledger.findByTenantIdAndClientRequestKey(tenantId,key);
        if(prior.isPresent()) {
            DriverLedgerEntry p=prior.get();
            if(!p.getDriverId().equals(driverId)||p.getAmount().compareTo(amount)!=0
                    ||!p.getTransactionDate().equals(request.getAdvanceDate())
                    ||!p.getDescription().equals(description)||!ledgerType.equals(p.getTransactionType()))
                throw ApiException.conflict("مفتاح العملية مستخدم بالفعل لحركة مالية مختلفة.");
            return new DriverLedgerEntryResponse(p,ledger.sumBalance(tenantId,driverId));
        }
        BigDecimal balance=ledger.sumBalance(tenantId,driverId);
        if("AGAINST_EARNED".equals(type) && balance.compareTo(amount)<0)
            throw ApiException.badRequest("السلفة أكبر من مستحقات العامل المتاحة.");
        defaultAccounts.initializeForTenant(tenantId);
        ChartAccount debitAccount="AGAINST_EARNED".equals(type)
                ?defaultAccounts.requireSystemAccount(tenantId,SystemAccountCode.DRIVER_PAYABLE)
                :defaultAccounts.requireSystemAccount(tenantId,SystemAccountCode.RECOVERABLE_WORKER_ADVANCES);
        ChartAccount cash=accounts.findByIdAndTenantId(request.getPaymentAccountId(),tenantId)
                .filter(ChartAccount::isActive).orElseThrow(()->ApiException.notFound("حساب الخزينة أو البنك غير موجود."));
        if(cash.getAccountType()!=AccountType.ASSET || !cash.isCashBankAccount())
            throw ApiException.badRequest("صرف السلفة يكون من حساب خزينة أو بنك مُعرّف لهذا الغرض فقط.");
        JournalEntry journal=journals.post(tenantId,user.getUserId(),new JournalPostingRequest(
                "driver-advance:"+key,"AGAINST_EARNED".equals(type)?"DRIVER_ADVANCE_EARNED":"DRIVER_ADVANCE_RECOVERABLE",
                null,request.getAdvanceDate(),description,null,Arrays.asList(
                new JournalLineRequest(debitAccount.getId(),"سلفة للعامل",amount,BigDecimal.ZERO,driverId,null),
                new JournalLineRequest(cash.getId(),"صرف سلفة من الخزينة/البنك",BigDecimal.ZERO,amount,null,null))));
        BigDecimal effect="AGAINST_EARNED".equals(type)?amount.negate():ZERO;
        DriverLedgerEntry entry=ledger.saveAndFlush(new DriverLedgerEntry(tenantId,driverId,ledgerType,
                request.getAdvanceDate(),amount,effect,description,key,null,journal.getId(),user.getUserId()));
        return new DriverLedgerEntryResponse(entry,balance.add(effect));
    }

    private BigDecimal money(BigDecimal v) {
        if(v==null||v.signum()<=0) throw ApiException.badRequest("المبلغ يجب أن يكون أكبر من صفر.");
        try{return v.setScale(2,RoundingMode.UNNECESSARY);}
        catch(ArithmeticException e){throw ApiException.badRequest("المبلغ لا يقبل أكثر من خانتين عشريتين.");}
    }
    private String required(String v,int max,String label) {
        String x=v==null?"":v.trim();
        if(x.isEmpty()||x.length()>max) throw ApiException.badRequest(label+" مطلوب وبحد أقصى "+max+" حرفًا.");
        return x;
    }
}
