package com.urbanfurniture.accounting.security;

/**
 * Application roles.
 *
 * <ul>
 *   <li>{@link #ADMIN} - business owner: full CRUD + archive, user management, CoA/journal config.</li>
 *   <li>{@link #ACCOUNTANT} - invoicing user: create master data and transactions, view reports,
 *       but no archive/delete and no user management.</li>
 *   <li>{@link #CONTACT} - portal user bound to a single contact row; may only ever see and pay
 *       their own invoices/bills. Enforced at the repository layer, not the UI.</li>
 * </ul>
 */
public enum Role {
    ADMIN,
    ACCOUNTANT,
    CONTACT
}
