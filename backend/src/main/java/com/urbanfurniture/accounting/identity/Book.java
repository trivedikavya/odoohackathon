package com.urbanfurniture.accounting.identity;

import com.urbanfurniture.accounting.common.domain.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One party's complete, self-contained set of accounts.
 * <p>
 * A Seller and a Vendor each own exactly one. A Customer owns none —
 * which is expressed structurally by simply never creating a row here,
 * rather than by a flag someone could forget to check.
 * <p>
 * Everything downstream — accounts, journals, document numbers, ledger
 * entries — hangs off a book. No journal entry may reference an account
 * belonging to a different one.
 */
@Entity
@Table(name = "book")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Book extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "party_id", nullable = false, unique = true)
    private Party party;

    @Column(nullable = false, length = 180)
    private String name;

    @Builder.Default
    @Column(name = "base_currency", nullable = false, length = 3)
    private String baseCurrency = "INR";

    /** April by default: the Indian financial year starts on 1 April. */
    @Builder.Default
    @Column(name = "fiscal_year_start_month", nullable = false)
    private Integer fiscalYearStartMonth = 4;
}
