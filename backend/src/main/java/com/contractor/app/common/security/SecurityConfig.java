package com.contractor.app.common.security;

import com.contractor.app.platform.infra.PlatformOwnerLoader;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtService jwtService,
            AuthenticatedUserLoader userLoader,
            PlatformOwnerLoader ownerLoader,
            RestAuthenticationEntryPoint entryPoint,
            RestAccessDeniedHandler accessDeniedHandler) throws Exception {
        http.csrf().disable()
                .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                .and().exceptionHandling().authenticationEntryPoint(entryPoint).accessDeniedHandler(accessDeniedHandler)
                .and().authorizeRequests()
                .antMatchers("/activate-admin/**", "/api/v1/health", "/api/v1/auth/register-tenant", "/api/v1/auth/login",
                        "/api/v1/auth/refresh", "/api/v1/auth/logout",
                        "/api/v1/platform/auth/login", "/api/v1/platform/auth/mfa/setup", "/api/v1/platform/auth/mfa/enable", "/api/v1/platform/auth/mfa/verify", "/api/v1/platform/admin-activation").permitAll()
                .antMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .antMatchers("/api/v1/platform/**").hasRole("PLATFORM_OWNER")
                .anyRequest().authenticated()
                .and().addFilterBefore(new JwtAuthenticationFilter(jwtService, userLoader, ownerLoader),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
