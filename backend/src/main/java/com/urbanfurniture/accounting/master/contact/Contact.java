package com.urbanfurniture.accounting.master.contact;

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

@Entity
@Table(name = "contact")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Contact extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 180)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ContactType type;

    @Column(length = 180)
    private String email;

    @Column(length = 30)
    private String mobile;

    @Column(name = "address_line", length = 255)
    private String addressLine;

    @Column(length = 100)
    private String city;

    /** Drives CGST/SGST vs IGST selection against the company's own state. */
    @Column(length = 100)
    private String state;

    @Column(length = 20)
    private String pincode;

    @Column(length = 20)
    private String gstin;

    @Column(name = "profile_image_url", length = 500)
    private String profileImageUrl;

    @Builder.Default
    @Column(nullable = false)
    private Boolean active = true;

    public boolean canBeCustomer() {
        return type == ContactType.CUSTOMER || type == ContactType.BOTH;
    }

    public boolean canBeVendor() {
        return type == ContactType.VENDOR || type == ContactType.BOTH;
    }
}
