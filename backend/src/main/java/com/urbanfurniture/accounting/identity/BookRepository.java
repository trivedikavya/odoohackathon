package com.urbanfurniture.accounting.identity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BookRepository extends JpaRepository<Book, Long> {

    Optional<Book> findByPartyId(Long partyId);
}
