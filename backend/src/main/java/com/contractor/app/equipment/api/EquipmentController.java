package com.contractor.app.equipment.api;

import com.contractor.app.common.api.PageResponse;
import com.contractor.app.common.security.CurrentUser;
import com.contractor.app.equipment.api.dto.EquipmentRequest;
import com.contractor.app.equipment.api.dto.EquipmentResponse;
import com.contractor.app.equipment.domain.EquipmentService;
import com.contractor.app.equipment.domain.EquipmentStatus;
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
@RequestMapping("/api/v1/equipment")
@PreAuthorize("hasAnyRole('ADMIN','OPERATOR','VIEWER')")
@Tag(name = "Equipment")
public class EquipmentController {

    private final EquipmentService service;

    public EquipmentController(EquipmentService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "المعدات (بحث بالكود أو الاسم، وفلتر النوع والحالة)")
    public PageResponse<EquipmentResponse> list(@RequestParam(required = false) String q,
                                                @RequestParam(required = false) Long typeId,
                                                @RequestParam(required = false) EquipmentStatus status,
                                                @RequestParam(defaultValue = "false") boolean includeInactive,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        return service.list(CurrentUser.tenantId(), q, typeId, status, includeInactive, page, size);
    }

    @GetMapping("/{id}")
    public EquipmentResponse get(@PathVariable Long id) {
        return service.get(CurrentUser.tenantId(), id);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EquipmentResponse create(@Valid @RequestBody EquipmentRequest request) {
        return service.create(CurrentUser.tenantId(), request);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    @Operation(summary = "تعديل معدة (يتطلب version)")
    public EquipmentResponse update(@PathVariable Long id, @Valid @RequestBody EquipmentRequest request) {
        return service.update(CurrentUser.tenantId(), id, request);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "تعطيل معدة (حذف منطقي)")
    public void deactivate(@PathVariable Long id) {
        service.deactivate(CurrentUser.tenantId(), id);
    }
}
