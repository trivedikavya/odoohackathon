package com.urbanfurniture.accounting.master;

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

/** One line of a combo's recipe: how much of a component makes one bundle. */
@Entity
@Table(name = "product_component")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductComponent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "combo_product_id", nullable = false)
    private Product combo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "component_product_id", nullable = false)
    private Product component;

    /** Units of the component consumed per one unit of the combo. */
    @Column(nullable = false, precision = 15, scale = 3)
    private BigDecimal quantity;
}
