package com.contractor.app.equipment.api;

import com.contractor.app.common.security.CurrentUser;
import com.contractor.app.equipment.api.dto.EquipmentTypeRequest;
import com.contractor.app.equipment.api.dto.EquipmentTypeResponse;
import com.contractor.app.equipment.domain.EquipmentTypeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import javax.validation.Valid;
import org.springframework.http.HttpStatus;
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
@RequestMapping("/api/v1/equipment-types")
@Tag(name = "Equipment types")
public class EquipmentTypeController {

    private final EquipmentTypeService service;

    public EquipmentTypeController(EquipmentTypeService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "أنواع المعدات (قائمة كاملة بلا ترقيم)")
    public List<EquipmentTypeResponse> list(@RequestParam(defaultValue = "false") boolean includeInactive) {
        return service.list(CurrentUser.tenantId(), includeInactive);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EquipmentTypeResponse create(@Valid @RequestBody EquipmentTypeRequest request) {
        return service.create(CurrentUser.tenantId(), request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "تعديل نوع (يتطلب version)")
    public EquipmentTypeResponse update(@PathVariable Long id, @Valid @RequestBody EquipmentTypeRequest request) {
        return service.update(CurrentUser.tenantId(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "تعطيل نوع (مرفوض إن كانت هناك معدات فعّالة منه)")
    public void deactivate(@PathVariable Long id) {
        service.deactivate(CurrentUser.tenantId(), id);
    }
}
