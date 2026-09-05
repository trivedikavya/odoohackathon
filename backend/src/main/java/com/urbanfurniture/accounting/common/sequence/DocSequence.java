package com.urbanfurniture.accounting.common.sequence;

import com.urbanfurniture.accounting.identity.Book;
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

/** A per-book running number, so each company numbers its own documents from 1. */
@Entity
@Table(name = "doc_sequence")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocSequence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @Column(name = "doc_type", nullable = false, length = 30)
    private String docType;

    @Column(nullable = false, length = 10)
    private String prefix;

    @Builder.Default
    @Column(name = "next_value", nullable = false)
    private Long nextValue = 1L;

    @Builder.Default
    @Column(nullable = false)
    private Integer padding = 4;
}
