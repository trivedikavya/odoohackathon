package com.urbanfurniture.accounting.security;

import com.urbanfurniture.accounting.identity.AppUser;
import com.urbanfurniture.accounting.identity.AppUserRepository;
import com.urbanfurniture.accounting.identity.BookRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loads a user by login ID, falling back to email.
 * <p>
 * Accepting either is a convenience for people who remember one but not
 * the other; both are unique, so there is no ambiguity.
 */
@Service
@RequiredArgsConstructor
public class AppUserDetailsService implements UserDetailsService {

    private final AppUserRepository userRepository;
    private final BookRepository bookRepository;

    @Override
    @Transactional(readOnly = true)
    public AppUserPrincipal loadUserByUsername(String identifier) throws UsernameNotFoundException {
        AppUser user = userRepository.findByLoginIdIgnoreCase(identifier)
                .or(() -> userRepository.findByEmailIgnoreCase(identifier))
                .orElseThrow(() -> new UsernameNotFoundException("No account for '" + identifier + "'"));

        // Resolved once, here, and carried on the principal for the rest
        // of the request.
        Long bookId = bookRepository.findByPartyId(user.getParty().getId())
                .map(b -> b.getId())
                .orElse(null);

        return new AppUserPrincipal(user, bookId);
    }
}
