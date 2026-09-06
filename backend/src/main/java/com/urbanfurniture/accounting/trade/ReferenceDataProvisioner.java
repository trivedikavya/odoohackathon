package com.urbanfurniture.accounting.trade;

import com.urbanfurniture.accounting.identity.Book;
import com.urbanfurniture.accounting.identity.Party;
import com.urbanfurniture.accounting.master.Contact;
import com.urbanfurniture.accounting.master.ContactRelationship;
import com.urbanfurniture.accounting.master.ContactRepository;
import com.urbanfurniture.accounting.master.Product;
import com.urbanfurniture.accounting.master.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the address-book and catalogue rows that a trade implies, in
 * transactions of their own.
 * <p>
 * These are find-or-create, and find-or-create races. Two orders
 * confirmed at the same moment against the same new customer both look,
 * both find nothing, and both insert — one of them hits the unique
 * constraint. In PostgreSQL a failed statement poisons the whole
 * transaction, so catching the error where it happened would still leave
 * the caller unable to commit the order.
 * <p>
 * Running the insert in {@code REQUIRES_NEW} contains that damage: the
 * inner transaction is the only thing that rolls back, and the caller can
 * simply re-read the row the other request just created.
 */
@Service
@RequiredArgsConstructor
public class ReferenceDataProvisioner {

    private final ContactRepository contactRepository;
    private final ProductRepository productRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Contact createContact(Book book, Party counterparty, ContactRelationship relationship) {
        return contactRepository.save(Contact.builder()
                .book(book)
                .party(counterparty)
                .relationship(relationship)
                .creditDays(30)
                .active(true)
                .build());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Product createProduct(Product product) {
        return productRepository.save(product);
    }
}
