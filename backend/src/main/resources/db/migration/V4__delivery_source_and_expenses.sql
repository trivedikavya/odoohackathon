-- =====================================================================
-- V4: allow the two entry sources that inventory and real overheads
-- introduce.
--
-- DELIVERY  - cost of goods, booked when a sale ships
-- (OPENING already existed; MANUAL now also carries operating expenses)
-- =====================================================================

ALTER TABLE journal_entry DROP CONSTRAINT ck_journal_entry_source;
ALTER TABLE journal_entry ADD CONSTRAINT ck_journal_entry_source
    CHECK (source_type IN ('INVOICE', 'BILL', 'PAYMENT', 'DELIVERY', 'OPENING', 'MANUAL'));
