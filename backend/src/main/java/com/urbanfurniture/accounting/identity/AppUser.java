package com.urbanfurniture.accounting.identity;

import com.urbanfurniture.accounting.common.domain.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "app_user")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppUser extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Unique across the platform, 6-12 characters. Sign-in identifier. */
    @Column(name = "login_id", nullable = false, unique = true, length = 12)
    private String loginId;

    @Column(nullable = false, unique = true, length = 180)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    /** The party whose books (if any) this user works in. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "party_id", nullable = false)
    private Party party;

    @Enumerated(EnumType.STRING)
    @Column(name = "access_level", nullable = false, length = 20)
    private AccessLevel accessLevel;

    @Builder.Default
    @Column(nullable = false)
    private Boolean active = true;
}
