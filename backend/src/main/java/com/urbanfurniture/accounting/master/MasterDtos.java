package com.urbanfurniture.accounting.master;

import com.urbanfurniture.accounting.identity.PartyType;
import com.urbanfurniture.accounting.ledger.AccountType;
import com.urbanfurniture.accounting.ledger.JournalType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public final class MasterDtos {

    private MasterDtos() {
    }

    // ---------------- parties and contacts ----------------

    /** A party you can trade with, for the counterparty picker. */
    public record PartyOption(
            Long id, String name, PartyType type, String city, String state, boolean keepsBooks) {
    }

    public record ContactResponse(
            Long id, Long partyId, String name, PartyType type,
            String email, String phone, String gstin,
            String city, String state, Integer creditDays, Boolean active,
            /** True when this counterparty keeps books, so deals mirror. */
            boolean keepsBooks) {
    }

    /**
     * Adding a counterparty who is not registered on the platform — a
     * walk-in customer or an offline supplier. Creates the party and the
     * contact together.
     */
    public record CreateContactRequest(
            @NotBlank @Size(max = 180) String name,
            @NotNull PartyType type,
            @Email @Size(max = 180) String email,
            @Size(max = 30) String phone,
            @Size(max = 20) String gstin,
            @Size(max = 255) String addressLine,
            @Size(max = 100) String city,
            @Size(max = 100) String state,
            @Size(max = 20) String pincode,
            @Min(0) Integer creditDays) {
    }

    /** Linking an already-registered party into this book's address list. */
    public record LinkContactRequest(@NotNull Long partyId, @Min(0) Integer creditDays) {
    }

    // ---------------- products ----------------

    public record ProductRequest(
            @NotBlank @Size(max = 180) String name,
            @NotNull ProductType type,
            @NotNull @DecimalMin("0.00") @Digits(integer = 13, fraction = 2) BigDecimal salesPrice,
            @DecimalMin("0.00") @Digits(integer = 13, fraction = 2) BigDecimal cost,
            @Size(max = 20) String hsnCode,
            @DecimalMin("0.00") @Digits(integer = 3, fraction = 2) BigDecimal taxRate,
            @Size(max = 100) String category) {
    }

    public record ProductResponse(
            Long id, String name, ProductType type, BigDecimal salesPrice, BigDecimal cost,
            String hsnCode, BigDecimal taxRate, String category, Boolean active) {
    }

    // ---------------- chart of accounts ----------------

    public record AccountResponse(
            Long id, String code, String name, AccountType type,
            String systemCode, Boolean isSystem, Boolean active) {
    }

    public record AccountRequest(
            @NotBlank @Size(max = 20) String code,
            @NotBlank @Size(max = 150) String name,
            @NotNull AccountType type) {
    }

    public record JournalResponse(Long id, String code, String name, JournalType type, Boolean active) {
    }

    // ---------------- the signed-in organisation ----------------

    public record OrganisationResponse(
            Long partyId, Long bookId, String name, PartyType type,
            String email, String phone, String gstin,
            String addressLine, String city, String state, String pincode) {
    }

    public record OrganisationRequest(
            @NotBlank @Size(max = 180) String name,
            @Size(max = 20) String gstin,
            @Size(max = 255) String addressLine,
            @Size(max = 100) String city,
            /** Changing this changes the GST treatment of every future deal. */
            @NotBlank @Size(max = 100) String state,
            @Size(max = 20) String pincode,
            @Size(max = 30) String phone) {
    }
}
