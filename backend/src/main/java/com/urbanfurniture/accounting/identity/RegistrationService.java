package com.urbanfurniture.accounting.identity;

import com.urbanfurniture.accounting.common.exception.ApiExceptions;
import com.urbanfurniture.accounting.ledger.BookProvisioningService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Self-registration.
 * <p>
 * A Seller or Vendor signing up gets a Party, a Book fitted out with a
 * full chart of accounts, and an ADMIN user — all in one transaction, so
 * a failure part-way through cannot leave a party with half a set of
 * books. A Customer gets a Party and a USER, and no book at all.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegistrationService {

    private final PartyRepository partyRepository;
    private final BookRepository bookRepository;
    private final AppUserRepository userRepository;
    private final BookProvisioningService bookProvisioningService;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public AppUser register(AuthDtos.RegisterRequest request) {
        String loginId = CredentialPolicy.normalizeLoginId(request.loginId());
        String email = CredentialPolicy.normalizeEmail(request.email());

        CredentialPolicy.validateLoginId(loginId);
        CredentialPolicy.validatePassword(request.password());

        if (userRepository.existsByLoginIdIgnoreCase(loginId)) {
            throw new ApiExceptions.ConflictException("That login ID is already taken");
        }
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ApiExceptions.ConflictException("An account with this email already exists");
        }

        String organisation = request.organisationName() != null && !request.organisationName().isBlank()
                ? request.organisationName().trim()
                : request.fullName().trim();

        Party party = partyRepository.save(Party.builder()
                .name(organisation)
                .type(request.partyType())
                .email(email)
                .phone(trimToNull(request.phone()))
                .gstin(trimToNull(request.gstin()))
                .addressLine(trimToNull(request.addressLine()))
                .city(trimToNull(request.city()))
                .state(trimToNull(request.state()))
                .pincode(trimToNull(request.pincode()))
                .active(true)
                .build());

        // Sellers and vendors keep books; customers do not. Expressed by
        // simply not creating the row, so there is nothing to forget to check.
        if (party.keepsBooks()) {
            Book book = bookRepository.save(Book.builder()
                    .party(party)
                    .name(organisation)
                    .baseCurrency("INR")
                    .fiscalYearStartMonth(4)
                    .build());
            bookProvisioningService.provision(book);
        }

        // The first user of a new organisation administers it. A customer
        // only ever gets portal access.
        AccessLevel level = party.keepsBooks() ? AccessLevel.ADMIN : AccessLevel.USER;

        AppUser user = userRepository.save(AppUser.builder()
                .loginId(loginId)
                .email(email)
                .passwordHash(passwordEncoder.encode(request.password()))
                .fullName(request.fullName().trim())
                .party(party)
                .accessLevel(level)
                .active(true)
                .build());

        log.info("Registered {} '{}' as {} ({})", party.getType(), organisation, loginId, level);
        return user;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
