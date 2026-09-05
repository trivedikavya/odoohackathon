package com.urbanfurniture.accounting.common.sequence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DocSequenceRepository extends JpaRepository<DocSequence, Long> {

    /**
     * Pessimistic write lock so two concurrent requests can never be handed the
     * same document number.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from DocSequence s where s.docType = :docType")
    Optional<DocSequence> findByDocTypeForUpdate(@Param("docType") String docType);
}
