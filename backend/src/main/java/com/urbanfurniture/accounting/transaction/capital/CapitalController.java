package com.urbanfurniture.accounting.transaction.capital;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/capital")
@RequiredArgsConstructor
@Tag(name = "Capital (Admin only)")
public class CapitalController {

    private final CapitalService capitalService;

    @PostMapping("/contributions")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Record an owner capital contribution into cash or bank")
    public CapitalService.CapitalContributionResponse contribute(
            @Valid @RequestBody CapitalService.CapitalContributionRequest request) {
        return capitalService.contribute(request);
    }
}
