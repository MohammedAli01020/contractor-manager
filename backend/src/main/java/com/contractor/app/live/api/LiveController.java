package com.contractor.app.live.api;

import com.contractor.app.common.security.CurrentUser;
import com.contractor.app.live.api.dto.LiveDriverResponse;
import com.contractor.app.live.api.dto.LiveEquipmentResponse;
import com.contractor.app.live.domain.LiveStatusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/live")
@Tag(name = "Live status")
public class LiveController {

    private final LiveStatusService service;

    public LiveController(LiveStatusService service) {
        this.service = service;
    }

    @GetMapping("/equipment")
    @Operation(summary = "حالة المعدات الفعّالة الآن: WORKING / AVAILABLE / MAINTENANCE / OUT_OF_SERVICE. "
            + "onlyFree=true يعرض المتاحة للتسجيل فقط")
    public List<LiveEquipmentResponse> equipment(@RequestParam(defaultValue = "false") boolean onlyFree) {
        return service.equipment(CurrentUser.tenantId(), onlyFree);
    }

    @GetMapping("/drivers")
    @Operation(summary = "حالة السائقين والعمال الفعّالين الآن: WORKING / AVAILABLE / UNAVAILABLE. "
            + "onlyFree=true يعرض المتاحين للتسجيل فقط")
    public List<LiveDriverResponse> drivers(@RequestParam(defaultValue = "false") boolean onlyFree) {
        return service.drivers(CurrentUser.tenantId(), onlyFree);
    }
}
