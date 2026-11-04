package com.contractor.app.workforce.api.dto;

import java.time.LocalDate;
import javax.validation.constraints.NotNull;

public class EndWorkforceAssignmentRequest {
    @NotNull
    private LocalDate effectiveTo;
    @NotNull
    private Long version;
    public LocalDate getEffectiveTo() { return effectiveTo; }
    public void setEffectiveTo(LocalDate effectiveTo) { this.effectiveTo = effectiveTo; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
