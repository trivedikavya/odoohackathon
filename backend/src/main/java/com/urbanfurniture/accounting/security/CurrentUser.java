package com.urbanfurniture.accounting.security;

import com.urbanfurniture.accounting.common.exception.ApiExceptions;
import com.urbanfurniture.accounting.identity.AccessLevel;
import com.urbanfurniture.accounting.identity.Book;
import com.urbanfurniture.accounting.identity.BookRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Accessor for the authenticated principal, and the single source of the
 * book scope every query is restricted to.
 * <p>
 * Both the book id and the party id come from the security context, never
 * from a request parameter, so a user cannot widen their own scope by
 * tampering with a payload.
 */
@Component
@RequiredArgsConstructor
public class CurrentUser {

    private final BookRepository bookRepository;

    public Optional<AppUserPrincipal> principal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || !(auth.getPrincipal() instanceof AppUserPrincipal p)) {
            return Optional.empty();
        }
        return Optional.of(p);
    }

    public AppUserPrincipal require() {
        return principal().orElseThrow(() -> new ApiExceptions.ForbiddenException("Not authenticated"));
    }

    public String loginIdOrSystem() {
        return principal().map(AppUserPrincipal::getLoginId).orElse("system");
    }

    public Long requirePartyId() {
        return require().getPartyId();
    }

    public boolean isAdmin() {
        return principal().map(p -> p.getAccessLevel() == AccessLevel.ADMIN).orElse(false);
    }

    public boolean isPortalUser() {
        return principal().map(p -> p.getAccessLevel() == AccessLevel.USER).orElse(false);
    }

    /**
     * The book every query in a back-office request must be restricted to.
     * <p>
     * Throws rather than returning null for a user with no book. That is
     * deliberately fail-closed: a null book id would mean "no restriction"
     * downstream, so a routing mistake that let a customer reach a
     * back-office service would return every book's data. Failing loudly
     * is the safer default.
     */
    public Long requireBookId() {
        AppUserPrincipal p = require();
        if (p.getBookId() == null) {
            throw new ApiExceptions.ForbiddenException(
                    "Your account is not linked to a set of books");
        }
        return p.getBookId();
    }

    @Transactional(readOnly = true)
    public Book requireBook() {
        Long bookId = requireBookId();
        return bookRepository.findById(bookId)
                .orElseThrow(() -> new ApiExceptions.NotFoundException("Book", bookId));
    }

    /**
     * Guard for any record owned by a book. Refuses access to another
     * book's row even if its id was guessed.
     */
    public void assertOwnsBook(Long bookId) {
        if (!requireBookId().equals(bookId)) {
            throw new ApiExceptions.ForbiddenException("This record belongs to another set of books");
        }
    }

    /** Guard for a portal user reading a document addressed to them. */
    public void assertIsParty(Long partyId) {
        if (!requirePartyId().equals(partyId)) {
            throw new ApiExceptions.ForbiddenException("This record belongs to another party");
        }
    }
}
