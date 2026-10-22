package com.contractor.app.shift.api;

import com.contractor.app.audit.api.dto.AuditResponse;
import com.contractor.app.common.api.PageResponse;
import com.contractor.app.common.security.CurrentUser;
import com.contractor.app.shift.api.dto.ShiftCancelRequest;
import com.contractor.app.shift.api.dto.ShiftDefaultsResponse;
import com.contractor.app.shift.api.dto.ShiftRequest;
import com.contractor.app.shift.api.dto.ShiftResponse;
import com.contractor.app.shift.api.dto.ShiftVersionRequest;
import com.contractor.app.shift.domain.ShiftService;
import com.contractor.app.shift.domain.ShiftStatus;
import com.contractor.app.shift.domain.ShiftType;
import com.contractor.app.audit.domain.AuditEntityType;
import com.contractor.app.audit.domain.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import javax.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
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
@RequestMapping("/api/v1/shifts")
@Tag(name = "Shifts")
public class ShiftController {

    private final ShiftService service;
    private final AuditService auditService;

    public ShiftController(ShiftService service, AuditService auditService) {
        this.service = service;
        this.auditService = auditService;
    }

    @GetMapping
    @Operation(summary = "الورديات (فلاتر: فترة، موقع، سائق، معدة، نوع، حالة). الأحدث أولًا")
    public PageResponse<ShiftResponse> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long siteId,
            @RequestParam(required = false) Long driverId,
            @RequestParam(required = false) Long equipmentId,
            @RequestParam(required = false) ShiftType type,
            @RequestParam(required = false) ShiftStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.list(CurrentUser.tenantId(), from, to, siteId, driverId, equipmentId, type, status, page, size);
    }

    @GetMapping("/running")
    @Operation(summary = "الورديات الشغالة الآن (الأقدم أولًا، فتظهر المنسية في الأعلى)")
    public List<ShiftResponse> running() {
        return service.running(CurrentUser.tenantId());
    }

    @GetMapping("/defaults")
    @Operation(summary = "الأجر والسعر وتكلفة التشغيل المقترحة لسائق ومعدة ونوع وردية (لتعبئة النموذج)")
    public ShiftDefaultsResponse defaults(@RequestParam(required = false) Long driverId,
                                          @RequestParam(required = false) Long equipmentId,
                                          @RequestParam ShiftType shiftType) {
        return service.defaults(CurrentUser.tenantId(), driverId, equipmentId, shiftType);
    }

    @GetMapping("/{id}")
    public ShiftResponse get(@PathVariable Long id) {
        return service.get(CurrentUser.tenantId(), id);
    }

    @GetMapping("/{id}/history")
    @Operation(summary = "سجل تعديلات الوردية (الأحدث أولًا)")
    public List<AuditResponse> history(@PathVariable Long id) {
        service.get(CurrentUser.tenantId(), id);
        return auditService.history(CurrentUser.tenantId(), AuditEntityType.SHIFT, id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "بدء وردية (تُسجَّل شغالة). التاريخ الافتراضي اليوم، ويقبل تاريخًا سابقًا")
    public ShiftResponse start(@Valid @RequestBody ShiftRequest request) {
        return service.start(CurrentUser.tenantId(), request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "تعديل وردية (يتطلب version، ويُسجَّل كل تغيير في سجل التدقيق)")
    public ShiftResponse update(@PathVariable Long id, @Valid @RequestBody ShiftRequest request) {
        return service.update(CurrentUser.tenantId(), id, request);
    }

    @PostMapping("/{id}/finish")
    @Operation(summary = "إنهاء وردية شغالة: يغيّر المؤشر فقط")
    public ShiftResponse finish(@PathVariable Long id, @Valid @RequestBody ShiftVersionRequest request) {
        return service.finish(CurrentUser.tenantId(), id, request.getVersion());
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "إلغاء وردية بسبب (تُستبعد من الحسابات)")
    public ShiftResponse cancel(@PathVariable Long id, @Valid @RequestBody ShiftCancelRequest request) {
        return service.cancel(CurrentUser.tenantId(), id, request);
    }

    @PostMapping("/{id}/reinstate")
    @Operation(summary = "إعادة تفعيل وردية ملغاة: المنتهية تعود منتهية، والتي أُلغيت أثناء التشغيل تعود شغالة إذا كان السائق والمعدة متاحين")
    public ShiftResponse reinstate(@PathVariable Long id, @Valid @RequestBody ShiftVersionRequest request) {
        return service.reinstate(CurrentUser.tenantId(), id, request.getVersion());
    }
}
