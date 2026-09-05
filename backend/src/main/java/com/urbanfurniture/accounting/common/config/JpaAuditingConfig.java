package com.urbanfurniture.accounting.common.config;

import com.urbanfurniture.accounting.security.AppUserPrincipal;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * Supplies {@code created_by} / {@code updated_by} for every audited entity
 * from the authenticated principal.
 */
@Configuration
public class JpaAuditingConfig {

    @Bean
    public AuditorAware<String> auditorAware() {
        return () -> {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated()) {
                return Optional.of("system");
            }
            if (auth.getPrincipal() instanceof AppUserPrincipal p) {
                // Login ID rather than email: it is what the user signs in
                // with and what appears everywhere else in the audit trail,
                // and it is stable if they change their address.
                return Optional.of(p.getLoginId());
            }
            return Optional.of(auth.getName() == null ? "system" : auth.getName());
        };
    }
}
