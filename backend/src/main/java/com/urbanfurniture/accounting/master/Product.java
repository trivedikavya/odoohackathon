package com.urbanfurniture.accounting.master;

import com.urbanfurniture.accounting.common.domain.Auditable;
import com.urbanfurniture.accounting.identity.Book;
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
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** A book's own catalogue item. Each book keeps its own; they are not shared. */
@Entity
@Table(name = "product")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @Column(nullable = false, length = 180)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductType type;

    @Builder.Default
    @Column(name = "sales_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal salesPrice = BigDecimal.ZERO;

    @Builder.Default
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal cost = BigDecimal.ZERO;

    @Column(name = "hsn_code", length = 20)
    private String hsnCode;

    @Builder.Default
    @Column(name = "tax_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal taxRate = BigDecimal.ZERO;

    @Column(length = 100)
    private String category;
    /**
     * Whether this item carries stock. Derived from the type on save, but
     * stored so a query can filter on it without decoding the enum.
     */
    @Builder.Default
    @Column(name = "track_inventory", nullable = false)
    private Boolean trackInventory = true;

    /** Populated only for COMBO items. */
    @Builder.Default
    @OneToMany(mappedBy = "combo", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ProductComponent> components = new ArrayList<>();

    @Builder.Default
    @Column(nullable = false)
    private Boolean active = true;

    public boolean isCombo() {
        return type == ProductType.COMBO;
    }

    public boolean tracksStock() {
        return Boolean.TRUE.equals(trackInventory);
    }
}
