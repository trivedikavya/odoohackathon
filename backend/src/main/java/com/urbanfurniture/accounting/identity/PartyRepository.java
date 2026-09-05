package com.urbanfurniture.accounting.identity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PartyRepository extends JpaRepository<Party, Long> {

    Optional<Party> findByEmailIgnoreCase(String email);

    @Query("""
            select p from Party p
            where p.active = true
              and (cast(:type as string) is null or p.type = :type)
              and lower(p.name) like lower(concat('%', :search, '%'))
            order by p.name asc
            """)
    List<Party> search(@Param("search") String search, @Param("type") PartyType type);

    /** Parties that keep books and can therefore be traded with as a supplier. */
    @Query("""
            select p from Party p
            where p.active = true and p.type in (com.urbanfurniture.accounting.identity.PartyType.SELLER,
                                                 com.urbanfurniture.accounting.identity.PartyType.VENDOR)
            order by p.name asc
            """)
    List<Party> findSuppliers();
}
