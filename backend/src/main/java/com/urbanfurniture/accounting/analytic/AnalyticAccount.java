package com.urbanfurniture.accounting.analytic;

import com.urbanfurniture.accounting.common.domain.Auditable;
import com.urbanfurniture.accounting.identity.Book;
import jakarta.persistence.*;
import lombok.*;

/**
 * A project, department or cost centre that costs can be attributed to.
 * <p>
 * A second classification running alongside the chart of accounts, not a
 * part of it: the financial account says what kind of money moved, this
 * says whose budget it came out of. Tagging never alters debits and
 * credits, so it cannot affect whether the ledger balances.
 */
@Entity
@Table(name = "analytic_account")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalyticAccount extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @Column(nullable = false, length = 20)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AnalyticAccountType type;

    @Column(length = 500)
    private String notes;

    @Builder.Default
    @Column(nullable = false)
    private Boolean active = true;
}
