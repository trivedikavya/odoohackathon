package com.urbanfurniture.accounting.common.sequence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DocSequenceRepository extends JpaRepository<DocSequence, Long> {

    /**
     * Pessimistic lock, so concurrent requests in the same book queue for
     * the counter instead of racing and colliding on the unique index.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from DocSequence s where s.book.id = :bookId and s.docType = :docType")
    Optional<DocSequence> lockByBookAndType(@Param("bookId") Long bookId,
                                            @Param("docType") String docType);
}
