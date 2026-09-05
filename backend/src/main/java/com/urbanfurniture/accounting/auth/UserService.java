package com.urbanfurniture.accounting.auth;

import com.urbanfurniture.accounting.common.exception.ApiExceptions;
import com.urbanfurniture.accounting.master.contact.ContactRepository;
import com.urbanfurniture.accounting.security.AppUser;
import com.urbanfurniture.accounting.security.AppUserRepository;
import com.urbanfurniture.accounting.security.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * User administration. Every method is owner-only, enforced server-side with
 * {@code @PreAuthorize} so bypassing the frontend route achieves nothing.
 */
@Service
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class UserService {

    private final AppUserRepository userRepository;
    private final ContactRepository contactRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public List<AuthDtos.UserResponse> list() {
        return userRepository.findAllByOrderByIdAsc().stream().map(this::toResponse).toList();
    }

    @Transactional
    public AuthDtos.UserResponse create(AuthDtos.CreateUserRequest request) {
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new ApiExceptions.ConflictException("A user with this email already exists");
        }

        // A portal user is meaningless without exactly one contact to scope it to.
        if (request.role() == Role.CONTACT) {
            if (request.contactId() == null) {
                throw new ApiExceptions.BusinessRuleException("A CONTACT user must be linked to a contact");
            }
            if (!contactRepository.existsById(request.contactId())) {
                throw new ApiExceptions.NotFoundException("Contact", request.contactId());
            }
            if (userRepository.findByContactId(request.contactId()).isPresent()) {
                throw new ApiExceptions.ConflictException("This contact already has a portal login");
            }
        } else if (request.contactId() != null) {
            throw new ApiExceptions.BusinessRuleException(
                    "Only CONTACT users may be linked to a contact");
        }

        AppUser user = AppUser.builder()
                .email(request.email().trim().toLowerCase())
                .passwordHash(passwordEncoder.encode(request.password()))
                .fullName(request.fullName().trim())
                .role(request.role())
                .contactId(request.contactId())
                .active(true)
                .build();

        return toResponse(userRepository.save(user));
    }

    @Transactional
    public AuthDtos.UserResponse update(Long id, AuthDtos.UpdateUserRequest request) {
        AppUser user = userRepository.findById(id)
                .orElseThrow(() -> new ApiExceptions.NotFoundException("User", id));

        if (request.fullName() != null && !request.fullName().isBlank()) {
            user.setFullName(request.fullName().trim());
        }
        if (request.role() != null) {
            // Changing role would break the contact-link invariant enforced in the schema.
            if (request.role() == Role.CONTACT || user.getRole() == Role.CONTACT) {
                throw new ApiExceptions.BusinessRuleException(
                        "Portal (CONTACT) logins cannot be converted to or from staff roles");
            }
            user.setRole(request.role());
        }
        if (request.active() != null) {
            user.setActive(request.active());
        }
        return toResponse(userRepository.save(user));
    }

    @Transactional
    public void resetPassword(Long id, String newPassword) {
        AppUser user = userRepository.findById(id)
                .orElseThrow(() -> new ApiExceptions.NotFoundException("User", id));
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    private AuthDtos.UserResponse toResponse(AppUser u) {
        return new AuthDtos.UserResponse(u.getId(), u.getEmail(), u.getFullName(), u.getRole(),
                u.getContactId(), u.getActive());
    }
}
