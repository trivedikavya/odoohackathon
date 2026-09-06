package com.urbanfurniture.accounting.master;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Master data for the signed-in book")
public class MasterDataController {

    private final MasterDataService service;

    // ---------------- counterparties ----------------

    @GetMapping("/suppliers")
    @Operation(summary = "Registered sellers and vendors you can raise a request on")
    public List<MasterDtos.PartyOption> suppliers() {
        return service.supplierOptions();
    }

    @GetMapping("/suppliers/{partyId}/catalogue")
    @Operation(summary = "What that supplier sells, with live stock")
    public List<MasterDtos.ProductResponse> supplierCatalogue(@PathVariable Long partyId) {
        return service.catalogueOf(partyId);
    }

    @GetMapping("/contacts")
    public List<MasterDtos.ContactResponse> contacts(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "false") boolean includeArchived) {
        return service.contacts(search, includeArchived);
    }

    @PostMapping("/contacts")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a counterparty who is not registered on the platform")
    public MasterDtos.ContactResponse createContact(
            @Valid @RequestBody MasterDtos.CreateContactRequest request) {
        return service.createContact(request);
    }

    @PostMapping("/contacts/link")
    @Operation(summary = "Add an already-registered party to your address list")
    public MasterDtos.ContactResponse linkContact(
            @Valid @RequestBody MasterDtos.LinkContactRequest request) {
        return service.linkContact(request);
    }

    @PutMapping("/contacts/{id}/archive")
    public MasterDtos.ContactResponse archiveContact(@PathVariable Long id) {
        return service.setContactArchived(id, true);
    }

    @PutMapping("/contacts/{id}/restore")
    public MasterDtos.ContactResponse restoreContact(@PathVariable Long id) {
        return service.setContactArchived(id, false);
    }

    // ---------------- products ----------------

    @GetMapping("/products")
    public List<MasterDtos.ProductResponse> products(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "false") boolean includeArchived) {
        return service.products(search, includeArchived);
    }

    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    public MasterDtos.ProductResponse createProduct(@Valid @RequestBody MasterDtos.ProductRequest request) {
        return service.createProduct(request);
    }

    @PutMapping("/products/{id}")
    public MasterDtos.ProductResponse updateProduct(@PathVariable Long id,
                                                    @Valid @RequestBody MasterDtos.ProductRequest request) {
        return service.updateProduct(id, request);
    }

    @PutMapping("/products/{id}/archive")
    public MasterDtos.ProductResponse archiveProduct(@PathVariable Long id) {
        return service.setProductArchived(id, true);
    }

    @PutMapping("/products/{id}/restore")
    public MasterDtos.ProductResponse restoreProduct(@PathVariable Long id) {
        return service.setProductArchived(id, false);
    }

    // ---------------- chart of accounts ----------------

    @GetMapping("/accounts")
    public List<MasterDtos.AccountResponse> accounts(
            @RequestParam(defaultValue = "false") boolean includeArchived) {
        return service.accounts(includeArchived);
    }

    @PostMapping("/accounts")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add an account. Admin only.")
    public MasterDtos.AccountResponse createAccount(@Valid @RequestBody MasterDtos.AccountRequest request) {
        return service.createAccount(request);
    }

    @PutMapping("/accounts/{id}/archive")
    public MasterDtos.AccountResponse archiveAccount(@PathVariable Long id) {
        return service.archiveAccount(id);
    }

    @GetMapping("/journals")
    public List<MasterDtos.JournalResponse> journals() {
        return service.journals();
    }

    // ---------------- the organisation ----------------

    @GetMapping("/organisation")
    @Operation(summary = "Your own details. The state field drives GST treatment.")
    public MasterDtos.OrganisationResponse organisation() {
        return service.organisation();
    }

    @PutMapping("/organisation")
    @Operation(summary = "Update your own details. Admin only.")
    public MasterDtos.OrganisationResponse updateOrganisation(
            @Valid @RequestBody MasterDtos.OrganisationRequest request) {
        return service.updateOrganisation(request);
    }
}
