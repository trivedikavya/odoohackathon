package com.urbanfurniture.accounting.ledger;

import com.urbanfurniture.accounting.master.account.Account;
import com.urbanfurniture.accounting.master.contact.Contact;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

import java.math.BigDecimal;

/**
 * One side of a ledger entry. Exactly one of {@code debit} / {@code credit}
 * is positive - enforced both here and by a database CHECK constraint.
 * <p>
 * {@code contact} is stamped on receivable/payable lines so per-customer and
 * per-vendor balances (and the aging report) can be derived from the ledger
 * itself rather than from a parallel summary table.
 */
@Entity
@Table(name = "journal_line")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JournalLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "journal_entry_id", nullable = false)
    private JournalEntry journalEntry;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contact_id")
    private Contact contact;

    @Builder.Default
    @Column(name = "line_no", nullable = false)
    private Integer lineNo = 1;

    @Column(length = 255)
    private String label;

    @Builder.Default
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal debit = BigDecimal.ZERO;

    @Builder.Default
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal credit = BigDecimal.ZERO;
}
