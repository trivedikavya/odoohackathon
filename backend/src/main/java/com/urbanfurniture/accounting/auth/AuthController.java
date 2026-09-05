package com.urbanfurniture.accounting.auth;

import com.urbanfurniture.accounting.common.exception.ApiExceptions;
import com.urbanfurniture.accounting.security.AppUser;
import com.urbanfurniture.accounting.security.AppUserPrincipal;
import com.urbanfurniture.accounting.security.AppUserRepository;
import com.urbanfurniture.accounting.security.CurrentUser;
import com.urbanfurniture.accounting.security.JwtService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final CurrentUser currentUser;
    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @PostMapping("/login")
    @Operation(summary = "Exchange email + password for a JWT")
    public AuthDtos.LoginResponse login(@Valid @RequestBody AuthDtos.LoginRequest request) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        } catch (BadCredentialsException ex) {
            throw new ApiExceptions.ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED,
                    "Invalid email or password");
        }

        AppUserPrincipal principal = (AppUserPrincipal) authentication.getPrincipal();
        String token = jwtService.generateToken(principal);

        return new AuthDtos.LoginResponse(token, jwtService.getExpirationMs(), toProfile(principal));
    }

    @GetMapping("/me")
    @Operation(summary = "Profile of the currently authenticated user")
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
            throw new ApiExceptions.BusinessRuleException("Current password is incorrect");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        return ResponseEntity.noContent().build();
    }

    private AuthDtos.UserProfile toProfile(AppUserPrincipal p) {
        return new AuthDtos.UserProfile(p.getId(), p.getEmail(), p.getFullName(), p.getRole(), p.getContactId());
    }
}
