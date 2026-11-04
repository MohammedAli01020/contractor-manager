package com.contractor.app.workforce.api.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

public class WorkforceRoleRequest {

    @NotBlank
    @Pattern(regexp = "[A-Za-z][A-Za-z0-9_]{1,49}")
    private String code;

    @NotBlank
    @Size(max = 100)
    private String name;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
