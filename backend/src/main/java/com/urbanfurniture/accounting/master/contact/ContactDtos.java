package com.urbanfurniture.accounting.master.contact;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class ContactDtos {

    private ContactDtos() {
    }

    public record ContactRequest(
            @NotBlank @Size(max = 180) String name,
            @NotNull ContactType type,
            @Email @Size(max = 180) String email,
            @Size(max = 30) String mobile,
            @Size(max = 255) String addressLine,
            @Size(max = 100) String city,
            @Size(max = 100) String state,
            @Size(max = 20) String pincode,
            @Size(max = 20) String gstin,
            @Size(max = 500) String profileImageUrl) {
    }

    public record ContactResponse(
            Long id,
            String name,
            ContactType type,
            String email,
            String mobile,
            String addressLine,
            String city,
            String state,
            String pincode,
            String gstin,
            String profileImageUrl,
            Boolean active) {

        public static ContactResponse from(Contact c) {
            return new ContactResponse(c.getId(), c.getName(), c.getType(), c.getEmail(), c.getMobile(),
                    c.getAddressLine(), c.getCity(), c.getState(), c.getPincode(), c.getGstin(),
                    c.getProfileImageUrl(), c.getActive());
        }
    }

    /** Lightweight shape for dropdowns. */
    public record ContactOption(Long id, String name, ContactType type) {
        public static ContactOption from(Contact c) {
            return new ContactOption(c.getId(), c.getName(), c.getType());
        }
    }
}
