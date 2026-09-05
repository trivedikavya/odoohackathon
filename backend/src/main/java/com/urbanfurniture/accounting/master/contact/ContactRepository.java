package com.urbanfurniture.accounting.master.contact;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ContactRepository extends JpaRepository<Contact, Long> {

    /**
     * {@code search} is never null - callers pass an empty string to mean
     * "no filter", which keeps the bound parameter typed for PostgreSQL.
     * {@code type} may be null and is guarded with an explicit cast for the
     * same reason.
     */
    @Query("""
            select c from Contact c
            where (:includeArchived = true or c.active = true)
              and (lower(c.name) like lower(concat('%', :search, '%'))
                   or lower(coalesce(c.email, '')) like lower(concat('%', :search, '%'))
                   or coalesce(c.mobile, '') like concat('%', :search, '%'))
              and (cast(:type as string) is null or c.type = :type
                   or c.type = com.urbanfurniture.accounting.master.contact.ContactType.BOTH)
            """)
    Page<Contact> search(@Param("search") String search,
                         @Param("type") ContactType type,
                         @Param("includeArchived") boolean includeArchived,
                         Pageable pageable);

    List<Contact> findByActiveTrueOrderByNameAsc();

    boolean existsByEmailIgnoreCase(String email);
}
