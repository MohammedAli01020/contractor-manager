package com.contractor.app.driver.domain;

import com.contractor.app.accounting.domain.ChartAccount;
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

    public DriverLedgerService(DriverRepository drivers, DriverLedgerEntryRepository ledger,
            ChartAccountRepository accounts, JournalEntryRepository journalsRepo, JournalPostingService journals) {
        this.drivers=drivers; this.ledger=ledger; this.accounts=accounts; this.journalsRepo=journalsRepo; this.journals=journals;
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
        ChartAccount payable=account(tenantId,"2100");
        ChartAccount cash=accounts.findByIdAndTenantId(request.getPaymentAccountId(),tenantId)
                .filter(ChartAccount::isActive).orElseThrow(()->ApiException.notFound("حساب الخزينة أو البنك غير موجود."));
        if(!Arrays.asList("1000","1010").contains(cash.getAccountCode()))
            throw ApiException.badRequest("الصرف يكون من حساب الخزينة 1000 أو البنك 1010 فقط.");
        JournalEntry journal=journals.post(tenantId,user.getUserId(),new JournalPostingRequest(
                "driver-payment:"+key,"DRIVER_PAYMENT",null,request.getPaymentDate(),description,null,Arrays.asList(
                new JournalLineRequest(payable.getId(),"صرف مستحقات العامل",amount,BigDecimal.ZERO,driverId,null),
                new JournalLineRequest(cash.getId(),"صرف من الخزينة/البنك",BigDecimal.ZERO,amount,null,null))));
        DriverLedgerEntry entry=ledger.saveAndFlush(new DriverLedgerEntry(tenantId,driverId,"PAYMENT",
                request.getPaymentDate(),amount,amount.negate(),description,key,null,journal.getId(),user.getUserId()));
        return new DriverLedgerEntryResponse(entry,balance.subtract(amount));
    }

    private ChartAccount account(Long tenantId,String code) {
        return accounts.findByTenantIdAndAccountCodeIgnoreCase(tenantId,code).filter(ChartAccount::isActive)
                .orElseThrow(()->ApiException.conflict("الحساب الافتراضي "+code+" غير مهيأ."));
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
