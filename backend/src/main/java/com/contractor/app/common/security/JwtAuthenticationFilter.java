package com.contractor.app.common.security;

import com.contractor.app.platform.domain.PlatformOwner;
import com.contractor.app.platform.infra.PlatformOwnerLoader;
import java.io.IOException;
import java.util.Collections;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

/** Runs only inside the security filter chain. Owner and tenant principals remain distinct. */
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private static final String BEARER = "Bearer ";
    private final JwtService jwtService;
    private final AuthenticatedUserLoader userLoader;
    private final PlatformOwnerLoader ownerLoader;

    public JwtAuthenticationFilter(JwtService jwtService, AuthenticatedUserLoader userLoader,
                                   PlatformOwnerLoader ownerLoader) {
        this.jwtService = jwtService;
        this.userLoader = userLoader;
        this.ownerLoader = ownerLoader;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER)) {
            String token = header.substring(BEARER.length()).trim();
            jwtService.parse(token).ifPresent(claims -> {
                if (claims.isPlatformOwner()) {
                    if (request.getRequestURI().startsWith("/api/v1/platform/")) {
                        ownerLoader.load(claims.getUserId()).ifPresent(owner -> authenticate(
                                request, owner, "ROLE_PLATFORM_OWNER"));
                    }
                } else {
                    userLoader.load(claims.getUserId(), claims.getTenantId()).ifPresent(user -> authenticate(
                            request, user, "ROLE_" + user.getRole().name()));
                }
            });
        }
        chain.doFilter(request, response);
    }

    private void authenticate(HttpServletRequest request, Object principal, String authority) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                principal, null, Collections.singletonList(new SimpleGrantedAuthority(authority)));
        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
