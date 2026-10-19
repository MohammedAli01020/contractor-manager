package com.contractor.app.shift.api;

import com.contractor.app.common.api.PageResponse;
import com.contractor.app.common.security.AuthenticatedUser;
import com.contractor.app.common.security.CurrentUser;
import com.contractor.app.shift.api.dto.ShiftCancelRequest;
import com.contractor.app.shift.api.dto.EndShiftRequest;
import com.contractor.app.shift.api.dto.ShiftDefaultsResponse;
import com.contractor.app.shift.api.dto.ShiftHistoryResponse;
import com.contractor.app.shift.api.dto.ShiftResponse;
import com.contractor.app.shift.api.dto.ShiftStartOptionsResponse;
import com.contractor.app.shift.api.dto.StartShiftRequest;
import com.contractor.app.shift.api.dto.ShiftVersionRequest;
import com.contractor.app.shift.domain.ShiftService;
import com.contractor.app.shift.domain.ShiftStatus;
import com.contractor.app.shift.domain.ShiftType;
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

    public ShiftController(ShiftService service) {
        this.service = service;
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

    @GetMapping("/defaults")
    @Operation(summary = "الأجر والسعر وتكلفة التشغيل المقترحة لسائق ومعدة ونوع وردية (لتعبئة النموذج)")
    public ShiftDefaultsResponse defaults(@RequestParam Long driverId, @RequestParam Long equipmentId,
                                          @RequestParam ShiftType shiftType) {
        return service.defaults(CurrentUser.tenantId(), driverId, equipmentId, shiftType);
    }

    @GetMapping("/start-options")
    @Operation(summary = "السائقون والمعدات المتاحون لبدء وردية الآن")
    public ShiftStartOptionsResponse startOptions() {
        return service.startOptions(CurrentUser.tenantId());
    }

    @GetMapping("/{id}")
    public ShiftResponse get(@PathVariable Long id) {
        return service.get(CurrentUser.tenantId(), id);
    }

    @GetMapping("/{id}/history")
    @Operation(summary = "سجل تعديلات الوردية (الأحدث أولًا)")
    public List<ShiftHistoryResponse> history(@PathVariable Long id) {
        return service.history(CurrentUser.tenantId(), id);
    }

    @PostMapping("/start")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "بدء وردية جديدة (حالتها IN_PROGRESS)")
    public ShiftResponse start(@Valid @RequestBody StartShiftRequest request) {
        return service.start(CurrentUser.get(), request);
    }

    @PostMapping("/{id}/end")
    @Operation(summary = "إنهاء وردية قيد التنفيذ (يتطلب version)")
    public ShiftResponse end(@PathVariable Long id, @Valid @RequestBody EndShiftRequest request) {
        return service.end(CurrentUser.get(), id, request);
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "إلغاء وردية بسبب (تُستبعد من الحسابات ويتحرر موعدها)")
    public ShiftResponse cancel(@PathVariable Long id, @Valid @RequestBody ShiftCancelRequest request) {
        AuthenticatedUser me = CurrentUser.get();
        return service.cancel(me, id, request);
    }

    @PostMapping("/{id}/reinstate")
    @Operation(summary = "إعادة تفعيل وردية ملغاة (إن كان موعدها ما زال حرًّا)")
    public ShiftResponse reinstate(@PathVariable Long id, @Valid @RequestBody ShiftVersionRequest request) {
        return service.reinstate(CurrentUser.get(), id, request.getVersion());
    }
}
