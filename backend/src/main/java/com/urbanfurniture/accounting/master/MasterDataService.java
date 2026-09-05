package com.urbanfurniture.accounting.master;

import com.urbanfurniture.accounting.common.SearchTerms;
import com.urbanfurniture.accounting.common.exception.ApiExceptions;
import com.urbanfurniture.accounting.identity.Book;
import com.urbanfurniture.accounting.identity.BookRepository;
import com.urbanfurniture.accounting.identity.Party;
import com.urbanfurniture.accounting.identity.PartyRepository;
import com.urbanfurniture.accounting.identity.PartyType;
import com.urbanfurniture.accounting.ledger.Account;
import com.urbanfurniture.accounting.ledger.AccountRepository;
import com.urbanfurniture.accounting.ledger.Journal;
import com.urbanfurniture.accounting.ledger.JournalRepository;
import com.urbanfurniture.accounting.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Master data for the signed-in book: counterparties, catalogue, chart of
 * accounts and the organisation's own details.
 * <p>
 * Every read and write is scoped to {@code currentUser.requireBookId()},
 * which comes from the security context and never from the request.
 */
@Service
@RequiredArgsConstructor
public class MasterDataService {

    private final ContactRepository contactRepository;
    private final ProductRepository productRepository;
    private final AccountRepository accountRepository;
    private final JournalRepository journalRepository;
    private final PartyRepository partyRepository;
    private final BookRepository bookRepository;
    private final CurrentUser currentUser;

    // ---------------- counterparties ----------------

    /**
     * Registered parties available to trade with, excluding yourself.
     * <p>
     * Deliberately platform-wide rather than book-local: the whole point
     * of the model is that you can raise a request on any registered
     * seller or vendor, whether or not they are already in your address
     * book.
     */
    @Transactional(readOnly = true)
    public List<MasterDtos.PartyOption> supplierOptions() {
        Long myPartyId = currentUser.requirePartyId();
        return partyRepository.findSuppliers().stream()
                .filter(p -> !p.getId().equals(myPartyId))
                .map(this::toOption)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MasterDtos.ContactResponse> contacts(String search, boolean includeArchived) {
        Long bookId = currentUser.requireBookId();
        return contactRepository.search(bookId, SearchTerms.normalize(search), includeArchived).stream()
                .map(this::toContactResponse)
                .toList();
    }

    /** Adds a counterparty who is not registered on the platform. */
    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public MasterDtos.ContactResponse createContact(MasterDtos.CreateContactRequest request) {
        Book book = currentUser.requireBook();

        Party party = partyRepository.save(Party.builder()
                .name(request.name().trim())
                .type(request.type())
                .email(trimToNull(request.email()))
                .phone(trimToNull(request.phone()))
                .gstin(trimToNull(request.gstin()))
                .addressLine(trimToNull(request.addressLine()))
                .city(trimToNull(request.city()))
                .state(trimToNull(request.state()))
                .pincode(trimToNull(request.pincode()))
                .active(true)
                .build());

        Contact contact = contactRepository.save(Contact.builder()
                .book(book)
                .party(party)
                .creditDays(request.creditDays() == null ? 30 : request.creditDays())
                .active(true)
                .build());

        return toContactResponse(contact);
    }

    /** Brings an already-registered party into this book's address list. */
    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public MasterDtos.ContactResponse linkContact(MasterDtos.LinkContactRequest request) {
        Book book = currentUser.requireBook();
        Party party = partyRepository.findById(request.partyId())
                .orElseThrow(() -> new ApiExceptions.NotFoundException("Party", request.partyId()));

        if (party.getId().equals(book.getParty().getId())) {
            throw new ApiExceptions.BusinessRuleException("You cannot add yourself as a contact");
        }

        Contact contact = contactRepository.findByBookIdAndPartyId(book.getId(), party.getId())
                .orElseGet(() -> contactRepository.save(Contact.builder()
                        .book(book).party(party)
                        .creditDays(request.creditDays() == null ? 30 : request.creditDays())
                        .active(true).build()));

        return toContactResponse(contact);
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public MasterDtos.ContactResponse setContactArchived(Long id, boolean archived) {
        Contact contact = requireContact(id);
        contact.setActive(!archived);
        return toContactResponse(contactRepository.save(contact));
    }

    // ---------------- products ----------------

    @Transactional(readOnly = true)
    public List<MasterDtos.ProductResponse> products(String search, boolean includeArchived) {
        Long bookId = currentUser.requireBookId();
        return productRepository.search(bookId, SearchTerms.normalize(search), includeArchived).stream()
                .map(this::toProductResponse)
                .toList();
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public MasterDtos.ProductResponse createProduct(MasterDtos.ProductRequest request) {
        Book book = currentUser.requireBook();
        return toProductResponse(productRepository.save(Product.builder()
                .book(book)
                .name(request.name().trim())
                .type(request.type())
                .salesPrice(nz(request.salesPrice()))
                .cost(nz(request.cost()))
                .hsnCode(trimToNull(request.hsnCode()))
                .taxRate(nz(request.taxRate()))
                .category(trimToNull(request.category()))
                .active(true)
                .build()));
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public MasterDtos.ProductResponse updateProduct(Long id, MasterDtos.ProductRequest request) {
        Product product = requireProduct(id);
        product.setName(request.name().trim());
        product.setType(request.type());
        product.setSalesPrice(nz(request.salesPrice()));
        product.setCost(nz(request.cost()));
        product.setHsnCode(trimToNull(request.hsnCode()));
        product.setTaxRate(nz(request.taxRate()));
        product.setCategory(trimToNull(request.category()));
        return toProductResponse(productRepository.save(product));
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public MasterDtos.ProductResponse setProductArchived(Long id, boolean archived) {
        Product product = requireProduct(id);
        product.setActive(!archived);
        return toProductResponse(productRepository.save(product));
    }

    // ---------------- chart of accounts ----------------

    @Transactional(readOnly = true)
    public List<MasterDtos.AccountResponse> accounts(boolean includeArchived) {
        return accountRepository.findForBook(currentUser.requireBookId(), includeArchived).stream()
                .map(this::toAccountResponse)
                .toList();
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public MasterDtos.AccountResponse createAccount(MasterDtos.AccountRequest request) {
        Book book = currentUser.requireBook();
        String code = request.code().trim();
        if (accountRepository.existsByBookIdAndCodeIgnoreCase(book.getId(), code)) {
            throw new ApiExceptions.ConflictException("An account with code '" + code + "' already exists");
        }
        return toAccountResponse(accountRepository.save(Account.builder()
                .book(book).code(code).name(request.name().trim()).type(request.type())
                .isSystem(false).active(true).build()));
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public MasterDtos.AccountResponse archiveAccount(Long id) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new ApiExceptions.NotFoundException("Account", id));
        currentUser.assertOwnsBook(account.getBook().getId());

        // The posting engine resolves these by code; archiving one would
        // break every future entry that needs it.
        if (Boolean.TRUE.equals(account.getIsSystem())) {
            throw new ApiExceptions.BusinessRuleException(
                    "'" + account.getName() + "' is a system account and cannot be archived");
        }
        account.setActive(false);
        return toAccountResponse(accountRepository.save(account));
    }

    @Transactional(readOnly = true)
    public List<MasterDtos.JournalResponse> journals() {
        return journalRepository.findByBookIdOrderByCodeAsc(currentUser.requireBookId()).stream()
                .map(j -> new MasterDtos.JournalResponse(
                        j.getId(), j.getCode(), j.getName(), j.getType(), j.getActive()))
                .toList();
    }

    // ---------------- the organisation ----------------

    @Transactional(readOnly = true)
    public MasterDtos.OrganisationResponse organisation() {
        Book book = currentUser.requireBook();
        Party p = book.getParty();
        return new MasterDtos.OrganisationResponse(
                p.getId(), book.getId(), p.getName(), p.getType(), p.getEmail(), p.getPhone(),
                p.getGstin(), p.getAddressLine(), p.getCity(), p.getState(), p.getPincode());
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public MasterDtos.OrganisationResponse updateOrganisation(MasterDtos.OrganisationRequest request) {
        Book book = currentUser.requireBook();
        Party p = book.getParty();

        p.setName(request.name().trim());
        p.setGstin(trimToNull(request.gstin()));
        p.setAddressLine(trimToNull(request.addressLine()));
        p.setCity(trimToNull(request.city()));
        p.setState(request.state().trim());
        p.setPincode(trimToNull(request.pincode()));
        p.setPhone(trimToNull(request.phone()));
        partyRepository.save(p);

        book.setName(p.getName());
        bookRepository.save(book);

        return organisation();
    }

    // ---------------- internals ----------------

    public Product requireProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ApiExceptions.NotFoundException("Product", id));
        currentUser.assertOwnsBook(product.getBook().getId());
        return product;
    }

    public Contact requireContact(Long id) {
        Contact contact = contactRepository.findById(id)
                .orElseThrow(() -> new ApiExceptions.NotFoundException("Contact", id));
        currentUser.assertOwnsBook(contact.getBook().getId());
        return contact;
    }

    private MasterDtos.PartyOption toOption(Party p) {
        return new MasterDtos.PartyOption(
                p.getId(), p.getName(), p.getType(), p.getCity(), p.getState(), p.keepsBooks());
    }

    private MasterDtos.ContactResponse toContactResponse(Contact c) {
        Party p = c.getParty();
        return new MasterDtos.ContactResponse(
                c.getId(), p.getId(), p.getName(), p.getType(), p.getEmail(), p.getPhone(),
                p.getGstin(), p.getCity(), p.getState(), c.getCreditDays(), c.getActive(),
                p.keepsBooks());
    }

    private MasterDtos.ProductResponse toProductResponse(Product p) {
        return new MasterDtos.ProductResponse(
                p.getId(), p.getName(), p.getType(), p.getSalesPrice(), p.getCost(),
                p.getHsnCode(), p.getTaxRate(), p.getCategory(), p.getActive());
    }

    private MasterDtos.AccountResponse toAccountResponse(Account a) {
        return new MasterDtos.AccountResponse(
                a.getId(), a.getCode(), a.getName(), a.getType(),
                a.getSystemCode() == null ? null : a.getSystemCode().name(),
                a.getIsSystem(), a.getActive());
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** Unused type reference kept for the compiler; see {@link PartyType}. */
    @SuppressWarnings("unused")
    private static final Class<PartyType> PARTY_TYPE = PartyType.class;
}
