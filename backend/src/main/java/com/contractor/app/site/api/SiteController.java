package com.contractor.app.site.api;

import com.contractor.app.common.api.PageResponse;
import com.contractor.app.common.security.CurrentUser;
import com.contractor.app.site.api.dto.SiteRequest;
import com.contractor.app.site.api.dto.SiteResponse;
import com.contractor.app.site.domain.SiteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@RequestMapping("/api/v1/sites")
@Tag(name = "Sites")
public class SiteController {

    private final SiteService service;

    public SiteController(SiteService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "المواقع (بحث وترقيم صفحات)")
    public PageResponse<SiteResponse> list(@RequestParam(required = false) String q,
                                           @RequestParam(defaultValue = "false") boolean includeInactive,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        return service.list(CurrentUser.tenantId(), q, includeInactive, page, size);
    }

    @GetMapping("/{id}")
    public SiteResponse get(@PathVariable Long id) {
        return service.get(CurrentUser.tenantId(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SiteResponse create(@Valid @RequestBody SiteRequest request) {
        return service.create(CurrentUser.tenantId(), request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "تعديل موقع (يتطلب version)")
    public SiteResponse update(@PathVariable Long id, @Valid @RequestBody SiteRequest request) {
        return service.update(CurrentUser.tenantId(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "تعطيل موقع (حذف منطقي)")
    public void deactivate(@PathVariable Long id) {
        service.deactivate(CurrentUser.tenantId(), id);
    }
}
