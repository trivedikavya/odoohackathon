-- =====================================================================
-- Platform-level seed.
--
-- Deliberately tiny. Books, their charts of accounts, journals and
-- sequences are all provisioned in application code when a Seller or
-- Vendor registers - so a book created by signup and a book created by
-- a seed script are byte-for-byte identical. Anything seeded here would
-- be a second, divergent definition of "a new book".
-- =====================================================================

INSERT INTO platform_sequence (doc_type, prefix, next_value, padding) VALUES
    ('DEAL',       'DEAL', 1, 5),
    ('SETTLEMENT', 'STL',  1, 5);
