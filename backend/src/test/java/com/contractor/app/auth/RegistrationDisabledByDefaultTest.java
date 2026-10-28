package com.contractor.app.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.contractor.app.accounting.domain.DefaultChartOfAccountsService;
import com.contractor.app.audit.domain.AuditService;
import com.contractor.app.auth.api.dto.RegisterTenantRequest;
import com.contractor.app.auth.domain.AuthService;
import com.contractor.app.auth.infra.RefreshTokenRepository;
import com.contractor.app.common.error.ApiException;
import com.contractor.app.common.security.JwtService;
import com.contractor.app.securityaudit.domain.LoginSecurityEventService;
import com.contractor.app.tenant.infra.TenantRepository;
import com.contractor.app.user.infra.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.core.env.StandardEnvironment;

class RegistrationDisabledByDefaultTest {

    @Test
    void disabledRegistrationRejectsTenantCreationBeforeAnyDatabaseWork() {
        TenantRepository tenants = mock(TenantRepository.class);
        UserRepository users = mock(UserRepository.class);
        RefreshTokenRepository refreshTokens = mock(RefreshTokenRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        JwtService jwt = mock(JwtService.class);
        AuditService audit = mock(AuditService.class);
        LoginSecurityEventService loginSecurityEvents = mock(LoginSecurityEventService.class);
        when(encoder.encode(anyString())).thenReturn("test-hash");

        AuthService service = new AuthService(
                tenants, users, refreshTokens, encoder, jwt, audit, false, 30L, new StandardEnvironment(), loginSecurityEvents, mock(DefaultChartOfAccountsService.class));

        assertThatThrownBy(() -> service.registerTenant(mock(RegisterTenantRequest.class)))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void explicitlyEnablingRegistrationDoesNotOpenItOutsideTestProfile() {
        TenantRepository tenants = mock(TenantRepository.class);
        UserRepository users = mock(UserRepository.class);
        RefreshTokenRepository refreshTokens = mock(RefreshTokenRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        JwtService jwt = mock(JwtService.class);
        AuditService audit = mock(AuditService.class);
        LoginSecurityEventService loginSecurityEvents = mock(LoginSecurityEventService.class);
        when(encoder.encode(anyString())).thenReturn("test-hash");

        AuthService service = new AuthService(
                tenants, users, refreshTokens, encoder, jwt, audit, true, 30L, new StandardEnvironment(), loginSecurityEvents, mock(DefaultChartOfAccountsService.class));

        assertThatThrownBy(() -> service.registerTenant(mock(RegisterTenantRequest.class)))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

}
