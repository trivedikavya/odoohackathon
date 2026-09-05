package com.urbanfurniture.accounting.security;

import com.urbanfurniture.accounting.common.exception.ApiExceptions;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Accessor for the authenticated principal.
 * <p>
 * Services use {@link #requireContactIdForPortalUser()} to derive the
 * row-level filter for {@link Role#CONTACT} users. The value always comes from
 * the security context - never from a request parameter - so a portal user
 * cannot widen their own scope by tampering with the payload.
 */
@Component
public class CurrentUser {

    public Optional<AppUserPrincipal> principal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof AppUserPrincipal p)) {
            return Optional.empty();
        }
        return Optional.of(p);
    }

    public AppUserPrincipal require() {
        return principal().orElseThrow(() -> new ApiExceptions.ForbiddenException("Not authenticated"));
    }

    public String emailOrSystem() {
        return principal().map(AppUserPrincipal::getEmail).orElse("system");
    }

    public boolean isPortalUser() {
        return principal().map(p -> p.getRole() == Role.CONTACT).orElse(false);
    }

    public boolean isAdmin() {
        return principal().map(p -> p.getRole() == Role.ADMIN).orElse(false);
    }

    /**
     * For a CONTACT-role user, returns the contact id every query must be
     * restricted to. Returns {@code null} for staff users, meaning "no
     * row-level restriction".
     */
    public Long contactScopeOrNull() {
        AppUserPrincipal p = require();
        return p.getRole() == Role.CONTACT ? p.getContactId() : null;
    }

    public Long requireContactIdForPortalUser() {
        AppUserPrincipal p = require();
        if (p.getRole() != Role.CONTACT) {
            throw new ApiExceptions.ForbiddenException("Not a portal user");
        }
        if (p.getContactId() == null) {
            throw new ApiExceptions.ForbiddenException("Portal user is not linked to a contact");
        }
        return p.getContactId();
    }

    /**
     * Guard for any record that belongs to a contact. Staff users pass through;
     * portal users must own the row.
     */
    public void assertCanAccessContact(Long ownerContactId) {
        Long scope = contactScopeOrNull();
        if (scope != null && !scope.equals(ownerContactId)) {
            throw new ApiExceptions.ForbiddenException("This record belongs to another contact");
        }
    }
}
