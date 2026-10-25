package com.contractor.app.driver.api;

import com.contractor.app.common.api.PageResponse;
import com.contractor.app.common.security.CurrentUser;
import com.contractor.app.driver.api.dto.DriverRequest;
import com.contractor.app.driver.api.dto.DriverResponse;
import com.contractor.app.driver.domain.DriverService;
import com.contractor.app.driver.domain.WorkerType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import javax.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/drivers")
@PreAuthorize("hasAnyRole('ADMIN','OPERATOR','VIEWER')")
@Tag(name = "Drivers")
public class DriverController {

    private final DriverService service;

    public DriverController(DriverService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "السائقون والعمال (بحث بالاسم أو الهاتف، وفلتر النوع)")
    public PageResponse<DriverResponse> list(@RequestParam(required = false) String q,
                                             @RequestParam(required = false) WorkerType type,
                                             @RequestParam(defaultValue = "false") boolean includeInactive,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return service.list(CurrentUser.tenantId(), q, type, includeInactive, page, size);
    }

    @GetMapping("/{id}")
    public DriverResponse get(@PathVariable Long id) {
        return service.get(CurrentUser.tenantId(), id);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DriverResponse create(@Valid @RequestBody DriverRequest request) {
        return service.create(CurrentUser.tenantId(), request);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    @Operation(summary = "تعديل (يتطلب version)")
    public DriverResponse update(@PathVariable Long id, @Valid @RequestBody DriverRequest request) {
        return service.update(CurrentUser.tenantId(), id, request);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "تعطيل (حذف منطقي)")
    public void deactivate(@PathVariable Long id) {
        service.deactivate(CurrentUser.tenantId(), id);
    }
}
