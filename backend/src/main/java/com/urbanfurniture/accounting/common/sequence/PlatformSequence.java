package com.urbanfurniture.accounting.common.sequence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A running number that spans books.
 * <p>
 * Deals and settlements belong to two books at once, so their numbers
 * cannot come from either book's own sequence without one side's
 * paperwork carrying the other's numbering.
 */
@Entity
@Table(name = "platform_sequence")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PlatformSequence {

    @Id
    @Column(name = "doc_type", length = 30)
    private String docType;

    @Column(nullable = false, length = 10)
    private String prefix;

    @Column(name = "next_value", nullable = false)
    private Long nextValue;

    @Column(nullable = false)
    private Integer padding;
}
