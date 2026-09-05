package com.urbanfurniture.accounting.master.contact;

import com.urbanfurniture.accounting.common.PageResponse;
import com.urbanfurniture.accounting.common.SearchTerms;
import com.urbanfurniture.accounting.common.exception.ApiExceptions;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ContactService {

    private final ContactRepository contactRepository;

    @Transactional(readOnly = true)
    public PageResponse<ContactDtos.ContactResponse> search(String search, ContactType type,
                                                            boolean includeArchived, Pageable pageable) {
        String term = SearchTerms.normalize(search);
        return PageResponse.of(contactRepository.search(term, type, includeArchived, pageable),
                ContactDtos.ContactResponse::from);
    }

    @Transactional(readOnly = true)
    public List<ContactDtos.ContactOption> options() {
        return contactRepository.findByActiveTrueOrderByNameAsc().stream()
                .map(ContactDtos.ContactOption::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ContactDtos.ContactResponse get(Long id) {
        return ContactDtos.ContactResponse.from(requireContact(id));
    }

    @Transactional(readOnly = true)
    public Contact requireContact(Long id) {
        return contactRepository.findById(id)
                .orElseThrow(() -> new ApiExceptions.NotFoundException("Contact", id));
    }

    @Transactional
    public ContactDtos.ContactResponse create(ContactDtos.ContactRequest request) {
        Contact contact = new Contact();
        apply(contact, request);
        contact.setActive(true);
        return ContactDtos.ContactResponse.from(contactRepository.save(contact));
    }

    @Transactional
    public ContactDtos.ContactResponse update(Long id, ContactDtos.ContactRequest request) {
        Contact contact = requireContact(id);
        apply(contact, request);
        return ContactDtos.ContactResponse.from(contactRepository.save(contact));
    }

    /**
     * Soft-delete. Owner-only: an accountant may create master data but never
     * retire it, because archived contacts still carry historical ledger rows.
     */
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public ContactDtos.ContactResponse setArchived(Long id, boolean archived) {
        Contact contact = requireContact(id);
        contact.setActive(!archived);
        return ContactDtos.ContactResponse.from(contactRepository.save(contact));
    }

    private void apply(Contact contact, ContactDtos.ContactRequest request) {
        contact.setName(request.name().trim());
        contact.setType(request.type());
        contact.setEmail(blankToNull(request.email()));
        contact.setMobile(blankToNull(request.mobile()));
        contact.setAddressLine(blankToNull(request.addressLine()));
        contact.setCity(blankToNull(request.city()));
        contact.setState(blankToNull(request.state()));
        contact.setPincode(blankToNull(request.pincode()));
        contact.setGstin(blankToNull(request.gstin()));
        contact.setProfileImageUrl(blankToNull(request.profileImageUrl()));
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
