package com.urbanfurniture.accounting.master.contact;

import com.urbanfurniture.accounting.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/contacts")
@RequiredArgsConstructor
@Tag(name = "Contacts (master data)")
public class ContactController {

    private final ContactService contactService;

    @GetMapping
    @Operation(summary = "Search contacts")
    public PageResponse<ContactDtos.ContactResponse> search(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) ContactType type,
            @RequestParam(defaultValue = "false") boolean includeArchived,
            @PageableDefault(size = 20) Pageable pageable) {
        return contactService.search(search, type, includeArchived, pageable);
    }

    @GetMapping("/options")
    @Operation(summary = "Active contacts, for dropdowns")
    public List<ContactDtos.ContactOption> options() {
        return contactService.options();
    }

    @GetMapping("/{id}")
    public ContactDtos.ContactResponse get(@PathVariable Long id) {
        return contactService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ContactDtos.ContactResponse create(@Valid @RequestBody ContactDtos.ContactRequest request) {
        return contactService.create(request);
    }

    @PutMapping("/{id}")
    public ContactDtos.ContactResponse update(@PathVariable Long id,
                                              @Valid @RequestBody ContactDtos.ContactRequest request) {
        return contactService.update(id, request);
    }

    @PutMapping("/{id}/archive")
    @Operation(summary = "Archive a contact (Admin only)")
    public ContactDtos.ContactResponse archive(@PathVariable Long id) {
        return contactService.setArchived(id, true);
    }

    @PutMapping("/{id}/restore")
    @Operation(summary = "Restore an archived contact (Admin only)")
    public ContactDtos.ContactResponse restore(@PathVariable Long id) {
        return contactService.setArchived(id, false);
    }
}
