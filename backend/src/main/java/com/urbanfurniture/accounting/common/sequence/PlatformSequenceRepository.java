package com.urbanfurniture.accounting.common.sequence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PlatformSequenceRepository extends JpaRepository<PlatformSequence, String> {

    /**
     * Pessimistic lock, so two concurrent deals queue for the counter
     * instead of racing and colliding on the unique index.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from PlatformSequence s where s.docType = :docType")
    Optional<PlatformSequence> lockByType(@Param("docType") String docType);
}
