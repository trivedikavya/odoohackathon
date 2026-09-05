package com.urbanfurniture.accounting.identity;

import com.urbanfurniture.accounting.common.exception.ApiExceptions;
import com.urbanfurniture.accounting.security.AppUserPrincipal;
import com.urbanfurniture.accounting.security.CurrentUser;
import com.urbanfurniture.accounting.security.JwtService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication and registration")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final RegistrationService registrationService;
    private final AppUserRepository userRepository;
    private final BookRepository bookRepository;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUser currentUser;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register. Sellers and Vendors are provisioned with a full set of books.")
    public AuthDtos.LoginResponse register(@Valid @RequestBody AuthDtos.RegisterRequest request) {
        AppUser user = registrationService.register(request);
        // Sign them straight in — asking someone to type the credentials
        // they just chose adds nothing.
        return issueToken(user);
    }

    @GetMapping("/check-login-id")
    @Operation(summary = "Is this login ID free? Used for live feedback on the signup form.")
    public AuthDtos.AvailabilityResponse checkLoginId(@RequestParam String loginId) {
        String normalized = CredentialPolicy.normalizeLoginId(loginId);
        try {
            CredentialPolicy.validateLoginId(normalized);
        } catch (ApiExceptions.BusinessRuleException e) {
            return new AuthDtos.AvailabilityResponse(false, e.getMessage());
        }
        boolean taken = userRepository.existsByLoginIdIgnoreCase(normalized);
        return new AuthDtos.AvailabilityResponse(!taken,
                taken ? "That login ID is already taken" : "Available");
    }

    @PostMapping("/login")
    @Operation(summary = "Sign in with login ID or email")
    public AuthDtos.LoginResponse login(@Valid @RequestBody AuthDtos.LoginRequest request) {
        try {
            Authentication auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.identifier(), request.password()));
            AppUserPrincipal principal = (AppUserPrincipal) auth.getPrincipal();
            return new AuthDtos.LoginResponse(
                    jwtService.generateToken(principal),
                    jwtService.expiryMillis(),
                    toProfile(principal));
        } catch (BadCredentialsException e) {
            // Deliberately identical whether the account exists or not.
            throw new ApiExceptions.ForbiddenException("Incorrect login ID or password");
        }
    }

    @GetMapping("/me")
    @Operation(summary = "The signed-in user's profile")
    public AuthDtos.UserProfile me() {
        return toProfile(currentUser.require());
    }

    @PostMapping("/change-password")
    @Transactional
    @Operation(summary = "Change your own password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody AuthDtos.ChangePasswordRequest request) {
        AppUserPrincipal principal = currentUser.require();
        AppUser user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ApiExceptions.NotFoundException("User", principal.getId()));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new ApiExceptions.ForbiddenException("Current password is incorrect");
        }
        CredentialPolicy.validatePassword(request.newPassword());

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        return ResponseEntity.noContent().build();
    }

    private AuthDtos.LoginResponse issueToken(AppUser user) {
        Long bookId = bookRepository.findByPartyId(user.getParty().getId())
                .map(Book::getId).orElse(null);
        AppUserPrincipal principal = new AppUserPrincipal(user, bookId);
        return new AuthDtos.LoginResponse(
                jwtService.generateToken(principal), jwtService.expiryMillis(), toProfile(principal));
    }

    private AuthDtos.UserProfile toProfile(AppUserPrincipal p) {
        return new AuthDtos.UserProfile(
                p.getId(), p.getLoginId(), p.getEmail(), p.getFullName(),
                p.getAccessLevel(), p.getPartyId(), p.getPartyType(), p.getPartyName(), p.getBookId());
    }
}
