package com.contractor.app.driver.api;

import com.contractor.app.driver.api.dto.DriverLedgerEntryResponse;
import com.contractor.app.driver.api.dto.DriverLedgerPaymentRequest;
import com.contractor.app.driver.domain.DriverLedgerService;
import java.util.List;
import javax.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/drivers/{driverId}/ledger")
@PreAuthorize("hasAnyRole('ADMIN','OPERATOR','VIEWER')")
public class DriverLedgerController {
    private final DriverLedgerService service;
    public DriverLedgerController(DriverLedgerService service){this.service=service;}
    @GetMapping
    public List<DriverLedgerEntryResponse> statement(@PathVariable Long driverId){return service.statement(driverId);}
    @PostMapping("/payments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public DriverLedgerEntryResponse pay(@PathVariable Long driverId,
            @Valid @RequestBody DriverLedgerPaymentRequest request){return service.pay(driverId,request);}
}
