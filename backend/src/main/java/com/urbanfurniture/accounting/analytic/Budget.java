package com.urbanfurniture.accounting.analytic;

import com.urbanfurniture.accounting.common.domain.Auditable;
import com.urbanfurniture.accounting.identity.Book;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A spending plan for one analytic account over one period.
 * <p>
 * Note what is absent: there is no actual or remaining column. Those are
 * computed from journal lines on every read, because storing them would
 * create a second source of truth that drifts the moment anyone posts or
 * back-dates an entry.
 */
@Entity
@Table(name = "budget")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Budget extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @Column(nullable = false, length = 150)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "analytic_account_id", nullable = false)
    private AnalyticAccount analyticAccount;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Builder.Default
    @Column(name = "planned_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal plannedAmount = BigDecimal.ZERO;

    @Column(length = 150)
    private String responsible;

    @Column(length = 500)
    private String notes;

    @Builder.Default
    @Column(nullable = false)
    private Boolean active = true;
}
