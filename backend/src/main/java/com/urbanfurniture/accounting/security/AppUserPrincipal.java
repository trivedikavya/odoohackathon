package com.urbanfurniture.accounting.security;

import com.urbanfurniture.accounting.identity.AccessLevel;
import com.urbanfurniture.accounting.identity.AppUser;
import com.urbanfurniture.accounting.identity.PartyType;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;

/**
 * The authenticated user, carrying both dimensions of their identity:
 * the party they belong to (and that party's book, if it keeps one) and
 * what they are allowed to do inside it.
 * <p>
 * {@code bookId} is resolved once at authentication and read from here on
 * every request. It never comes from a request parameter, so a user
 * cannot widen their own scope by editing a payload.
 */
@Getter
public class AppUserPrincipal implements UserDetails {

    private final Long id;
    private final String loginId;
    private final String email;
    private final String password;
    private final String fullName;
    private final AccessLevel accessLevel;
    private final Long partyId;
    private final PartyType partyType;
    private final String partyName;
    /** Null for customers, who keep no books. */
    private final Long bookId;
    private final boolean active;

    public AppUserPrincipal(AppUser user, Long bookId) {
        this.id = user.getId();
        this.loginId = user.getLoginId();
        this.email = user.getEmail();
        this.password = user.getPasswordHash();
        this.fullName = user.getFullName();
        this.accessLevel = user.getAccessLevel();
        this.partyId = user.getParty().getId();
        this.partyType = user.getParty().getType();
        this.partyName = user.getParty().getName();
        this.bookId = bookId;
        this.active = Boolean.TRUE.equals(user.getActive());
    }

    @Override
    public List<GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + accessLevel.name()));
    }

    /** Spring Security signs in by login ID, not email. */
    @Override
    public String getUsername() {
        return loginId;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}
