package com.urbanfurniture.accounting.common.sequence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "doc_sequence")
@Getter
@Setter
@NoArgsConstructor
public class DocSequence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "doc_type", nullable = false, unique = true, length = 30)
    private String docType;

    @Column(nullable = false, length = 10)
    private String prefix;

    @Column(name = "next_value", nullable = false)
    private Long nextValue;

    @Column(nullable = false)
    private Integer padding;
}
