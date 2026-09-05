package com.urbanfurniture.accounting.identity;

import com.urbanfurniture.accounting.common.exception.ApiExceptions;
import com.urbanfurniture.accounting.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Colleague management, scoped to the signed-in user's own organisation.
 * <p>
 * An ADMIN administers their own party and nothing else — there is no
 * cross-organisation user administration, by design.
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "Users within your organisation (Admin only)")
public class UserController {

    private final AppUserRepository userRepository;
    private final PartyRepository partyRepository;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUser currentUser;

    @GetMapping
    @Transactional(readOnly = true)
    public List<AuthDtos.UserResponse> list() {
        return userRepository.findByPartyIdOrderByIdAsc(currentUser.requirePartyId())
                .stream().map(this::toResponse).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    @Operation(summary = "Add a colleague to your own organisation")
    public AuthDtos.UserResponse create(@Valid @RequestBody AuthDtos.CreateStaffRequest request) {
        Long partyId = currentUser.requirePartyId();
        Party party = partyRepository.findById(partyId)
                .orElseThrow(() -> new ApiExceptions.NotFoundException("Party", partyId));

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

        // A party that keeps no books has no back office to staff.
        if (!party.keepsBooks() && request.accessLevel().isStaff()) {
            throw new ApiExceptions.BusinessRuleException(
                    "A customer account cannot have staff users");
        }

        return toResponse(userRepository.save(AppUser.builder()
                .loginId(loginId)
                .email(email)
                .passwordHash(passwordEncoder.encode(request.password()))
                .fullName(request.fullName().trim())
                .party(party)
                .accessLevel(request.accessLevel())
                .active(true)
                .build()));
    }

    @PutMapping("/{id}/deactivate")
    @Transactional
    public AuthDtos.UserResponse deactivate(@PathVariable Long id) {
        AppUser user = requireOwnColleague(id);
        if (user.getId().equals(currentUser.require().getId())) {
            throw new ApiExceptions.BusinessRuleException("You cannot deactivate your own account");
        }
        user.setActive(false);
        return toResponse(userRepository.save(user));
    }

    @PutMapping("/{id}/activate")
    @Transactional
    public AuthDtos.UserResponse activate(@PathVariable Long id) {
        AppUser user = requireOwnColleague(id);
        user.setActive(true);
        return toResponse(userRepository.save(user));
    }

    private AppUser requireOwnColleague(Long id) {
        AppUser user = userRepository.findById(id)
                .orElseThrow(() -> new ApiExceptions.NotFoundException("User", id));
        if (!user.getParty().getId().equals(currentUser.requirePartyId())) {
            throw new ApiExceptions.ForbiddenException("That user belongs to another organisation");
        }
        return user;
    }

    private AuthDtos.UserResponse toResponse(AppUser u) {
        return new AuthDtos.UserResponse(
                u.getId(), u.getLoginId(), u.getEmail(), u.getFullName(),
                u.getAccessLevel(), u.getActive());
    }
}
