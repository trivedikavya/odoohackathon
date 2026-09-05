package com.urbanfurniture.accounting.master.account;

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
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
@Tag(name = "Chart of Accounts")
public class AccountController {

    private final AccountService accountService;

    @GetMapping
    @Operation(summary = "List the chart of accounts")
    public List<AccountDtos.AccountResponse> list(@RequestParam(defaultValue = "false") boolean includeArchived) {
        return accountService.list(includeArchived);
    }

    @GetMapping("/{id}")
    public AccountDtos.AccountResponse get(@PathVariable Long id) {
        return accountService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add an account (Admin only)")
    public AccountDtos.AccountResponse create(@Valid @RequestBody AccountDtos.AccountRequest request) {
        return accountService.create(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an account (Admin only)")
    public AccountDtos.AccountResponse update(@PathVariable Long id,
                                              @Valid @RequestBody AccountDtos.AccountRequest request) {
        return accountService.update(id, request);
    }

    @PutMapping("/{id}/archive")
    @Operation(summary = "Archive a non-system account (Admin only)")
    public AccountDtos.AccountResponse archive(@PathVariable Long id) {
        return accountService.setArchived(id, true);
    }

    @PutMapping("/{id}/restore")
    public AccountDtos.AccountResponse restore(@PathVariable Long id) {
        return accountService.setArchived(id, false);
    }
}
