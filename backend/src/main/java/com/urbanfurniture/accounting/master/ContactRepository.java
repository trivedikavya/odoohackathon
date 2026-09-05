package com.urbanfurniture.accounting.master;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ContactRepository extends JpaRepository<Contact, Long> {

    Optional<Contact> findByBookIdAndPartyId(Long bookId, Long partyId);

    @Query("""
            select c from Contact c
            join fetch c.party p
            where c.book.id = :bookId
              and (:includeArchived = true or c.active = true)
              and (lower(p.name) like lower(concat('%', :search, '%'))
                   or lower(coalesce(p.email, '')) like lower(concat('%', :search, '%')))
            order by p.name asc
            """)
    List<Contact> search(@Param("bookId") Long bookId,
                         @Param("search") String search,
                         @Param("includeArchived") boolean includeArchived);
}
