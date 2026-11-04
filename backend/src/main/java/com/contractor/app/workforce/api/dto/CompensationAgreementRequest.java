package com.contractor.app.workforce.api.dto;

import com.contractor.app.workforce.domain.CompensationSource;
import java.time.LocalDate;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

public class CompensationAgreementRequest {
    private Long workforceAssignmentId;
    @Size(max = 80)
    private String agreementReference;
    @NotNull
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    @NotBlank @Pattern(regexp = "[A-Za-z]{3}")
    private String currencyCode;
    private CompensationSource source;
    private Long supersedesAgreementId;
    @Size(max = 500)
    private String changeReason;
    @NotEmpty @Valid
    private List<CompensationComponentRequest> components;

    public Long getWorkforceAssignmentId() { return workforceAssignmentId; }
    public void setWorkforceAssignmentId(Long value) { workforceAssignmentId = value; }
    public String getAgreementReference() { return agreementReference; }
    public void setAgreementReference(String value) { agreementReference = value; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(LocalDate value) { effectiveFrom = value; }
    public LocalDate getEffectiveTo() { return effectiveTo; }
    public void setEffectiveTo(LocalDate value) { effectiveTo = value; }
    public String getCurrencyCode() { return currencyCode; }
    public void setCurrencyCode(String value) { currencyCode = value; }
    public CompensationSource getSource() { return source; }
    public void setSource(CompensationSource value) { source = value; }
    public Long getSupersedesAgreementId() { return supersedesAgreementId; }
    public void setSupersedesAgreementId(Long value) { supersedesAgreementId = value; }
    public String getChangeReason() { return changeReason; }
    public void setChangeReason(String value) { changeReason = value; }
    public List<CompensationComponentRequest> getComponents() { return components; }
    public void setComponents(List<CompensationComponentRequest> value) { components = value; }
}
