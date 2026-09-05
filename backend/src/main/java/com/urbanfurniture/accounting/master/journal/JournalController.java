package com.urbanfurniture.accounting.master.journal;

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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/journals")
@RequiredArgsConstructor
@Tag(name = "Journals")
public class JournalController {

    private final JournalService journalService;

    @GetMapping
    @Operation(summary = "List configured journals")
    public List<JournalDtos.JournalResponse> list() {
        return journalService.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a journal (Admin only)")
    public JournalDtos.JournalResponse create(@Valid @RequestBody JournalDtos.JournalRequest request) {
        return journalService.create(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a journal (Admin only)")
    public JournalDtos.JournalResponse update(@PathVariable Long id,
                                              @Valid @RequestBody JournalDtos.JournalRequest request) {
        return journalService.update(id, request);
    }
}
