package com.urbanfurniture.accounting.trade;

import com.urbanfurniture.accounting.common.Money;
import com.urbanfurniture.accounting.common.domain.Auditable;
import com.urbanfurniture.accounting.identity.Party;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * The shared economic event: one agreement between a buyer and a seller.
 * <p>
 * It sits above both books. The buyer's purchase order and the seller's
 * sales order are two views of this row, which is why accepting once
 * moves the deal for both sides and the two can never disagree about
 * what was agreed.
 */
@Entity
@Table(name = "deal")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Deal extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "deal_no", nullable = false, unique = true, length = 30)
    private String dealNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "buyer_party_id", nullable = false)
    private Party buyer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_party_id", nullable = false)
    private Party seller;

    /** Who raised the request. Only the other side may accept or reject it. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "initiated_by_party_id", nullable = false)
    private Party initiatedBy;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DealStatus status = DealStatus.RFQ_DRAFT;

    @Column(name = "deal_date", nullable = false)
    private LocalDate dealDate;

    @Column(name = "expected_delivery")
    private LocalDate expectedDelivery;

    @Column(name = "delivered_at")
    private LocalDate deliveredAt;

    /**
     * The buyer's state, frozen when the deal is raised. A buyer who
     * relocates later must not retroactively change the tax that was
     * correctly charged today.
     */
    @Column(name = "place_of_supply", length = 100)
    private String placeOfSupply;

    @Builder.Default
    @Column(name = "untaxed_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal untaxedAmount = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "tax_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(length = 500)
    private String notes;

    @Column(name = "reject_reason", length = 500)
    private String rejectReason;

    @Builder.Default
    @OneToMany(mappedBy = "deal", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("lineNo asc")
    private List<DealLine> lines = new ArrayList<>();

    public void addLine(DealLine line) {
        line.setDeal(this);
        line.setLineNo(lines.size() + 1);
        lines.add(line);
    }

    public void recalculateTotals() {
        BigDecimal untaxed = Money.ZERO;
        BigDecimal tax = Money.ZERO;
        BigDecimal total = Money.ZERO;
        for (DealLine line : lines) {
            untaxed = Money.add(untaxed, line.getUntaxedAmount());
            tax = Money.add(tax, line.getTaxAmount());
            total = Money.add(total, line.getLineTotal());
        }
        this.untaxedAmount = untaxed;
        this.taxAmount = tax;
        this.totalAmount = total;
    }

    /** True when the given party is allowed to accept or reject this request. */
    public boolean canBeDecidedBy(Long partyId) {
        return status == DealStatus.RFQ_SENT && !initiatedBy.getId().equals(partyId)
                && (buyer.getId().equals(partyId) || seller.getId().equals(partyId));
    }

    public boolean involves(Long partyId) {
        return buyer.getId().equals(partyId) || seller.getId().equals(partyId);
    }
}
