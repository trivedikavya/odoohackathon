package com.urbanfurniture.accounting.identity;

import com.urbanfurniture.accounting.common.domain.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A tradeable identity: one row per real-world organisation or person,
 * shared by every book that deals with them.
 * <p>
 * That sharing is the point. Because a customer is one party rather than
 * a copy inside each supplier's contact list, a customer who bought from
 * both a seller and a vendor can be shown all their invoices in one
 * place, and a deal between two registered parties can be mirrored into
 * both of their books.
 */
@Entity
@Table(name = "party")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Party extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 180)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PartyType type;

    @Column(length = 180)
    private String email;

    @Column(length = 30)
    private String phone;

    @Column(length = 20)
    private String gstin;

    @Column(name = "address_line", length = 255)
    private String addressLine;

    @Column(length = 100)
    private String city;

    /**
     * Half of the GST comparison. Compared against the counterparty's
     * state to decide CGST + SGST versus IGST.
     */
    @Column(length = 100)
    private String state;

    @Column(length = 20)
    private String pincode;

    @Builder.Default
    @Column(nullable = false)
    private Boolean active = true;

    /** True for Sellers and Vendors — the party types that keep books. */
    public boolean keepsBooks() {
        return type != null && type.keepsBooks();
    }
}
