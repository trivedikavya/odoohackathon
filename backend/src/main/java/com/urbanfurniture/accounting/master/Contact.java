package com.urbanfurniture.accounting.master;

import com.urbanfurniture.accounting.common.domain.Auditable;
import com.urbanfurniture.accounting.identity.Book;
import com.urbanfurniture.accounting.identity.Party;
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

/**
 * One book's view of a counterparty.
 * <p>
 * The counterparty itself is a global {@link Party}. This row adds the
 * book-local details — credit terms, whether they are still active for
 * this book — without duplicating the identity. If that party also owns
 * a book, deals with them mirror; if not, deals stay single-sided.
 */
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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "party_id", nullable = false)
    private Party party;

    @Builder.Default
    @Column(name = "credit_days", nullable = false)
    private Integer creditDays = 30;

    @Builder.Default
    @Column(nullable = false)
    private Boolean active = true;
}
