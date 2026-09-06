-- =====================================================================
-- V3: make the product master real, and give every book a stock ledger.
--
-- Until now `deal_line` carried a free-text description and the product
-- master was decorative: nothing that traded ever referenced it. That
-- one gap is what blocked inventory, stock checks, per-product reporting
-- and any honest notion of gross margin.
--
-- Stock is modelled the same way the general ledger is: an append-only
-- table of movements, with quantity on hand and average cost DERIVED by
-- aggregation rather than stored. A stored quantity is a second source
-- of truth that drifts the first time anything is back-dated.
-- =====================================================================

-- ---------------------------------------------------------------------
-- Products: add COMBO, and say explicitly which items carry stock.
-- ---------------------------------------------------------------------
ALTER TABLE product DROP CONSTRAINT ck_product_type;
ALTER TABLE product ADD CONSTRAINT ck_product_type
    CHECK (type IN ('GOODS', 'SERVICE', 'COMBO'));

-- Services have nothing to count. Combos have nothing of their own to
-- count either - selling one consumes its components, and it is those
-- that carry the stock.
ALTER TABLE product ADD COLUMN track_inventory BOOLEAN NOT NULL DEFAULT TRUE;
UPDATE product SET track_inventory = (type = 'GOODS');

-- ---------------------------------------------------------------------
-- Combo composition.
--
-- A "Study Set" is one desk plus one chair. Selling a set moves both
-- components out of stock and costs the sale at the sum of their
-- averages, so a bundle is never a way to sell stock you do not have.
-- ---------------------------------------------------------------------
CREATE TABLE product_component (
    id                   BIGSERIAL PRIMARY KEY,
    combo_product_id     BIGINT         NOT NULL REFERENCES product (id) ON DELETE CASCADE,
    component_product_id BIGINT         NOT NULL REFERENCES product (id),
    quantity             NUMERIC(15, 3) NOT NULL,

    CONSTRAINT uq_product_component UNIQUE (combo_product_id, component_product_id),
    CONSTRAINT ck_product_component_qty CHECK (quantity > 0),
    -- A combo containing itself would recurse forever when expanded.
    CONSTRAINT ck_product_component_not_self CHECK (combo_product_id <> component_product_id)
);

CREATE INDEX idx_product_component_combo ON product_component (combo_product_id);

-- ---------------------------------------------------------------------
-- The stock ledger.
--
-- `unit_cost` means different things by direction, and both are needed:
--   IN  - what was actually paid for that receipt
--   OUT - the weighted average at the instant of issue
-- Recording the average on the OUT row is what makes a historical
-- valuation reproducible: recomputing it later from today's average
-- would silently restate past cost of sales.
-- ---------------------------------------------------------------------
CREATE TABLE stock_movement (
    id                 BIGSERIAL PRIMARY KEY,
    book_id            BIGINT         NOT NULL REFERENCES book (id),
    product_id         BIGINT         NOT NULL REFERENCES product (id),
    movement_date      DATE           NOT NULL,
    direction          VARCHAR(10)    NOT NULL,
    quantity           NUMERIC(15, 3) NOT NULL,
    unit_cost          NUMERIC(15, 2) NOT NULL,
    total_cost         NUMERIC(15, 2) NOT NULL,
    source_type        VARCHAR(20)    NOT NULL,
    source_document_id BIGINT,
    journal_entry_id   BIGINT         REFERENCES journal_entry (id),
    note               VARCHAR(255),
    created_at         TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    created_by         VARCHAR(180),

    CONSTRAINT ck_stock_direction CHECK (direction IN ('IN', 'OUT')),
    CONSTRAINT ck_stock_source CHECK (
        source_type IN ('BILL', 'DELIVERY', 'OPENING', 'ADJUSTMENT')),
    CONSTRAINT ck_stock_quantity CHECK (quantity > 0),
    CONSTRAINT ck_stock_cost CHECK (unit_cost >= 0 AND total_cost >= 0)
);

CREATE INDEX idx_stock_movement_product ON stock_movement (book_id, product_id, movement_date);
CREATE INDEX idx_stock_movement_book ON stock_movement (book_id, movement_date DESC);

-- ---------------------------------------------------------------------
-- Link trading to the catalogue.
--
-- deal_line.product_id is the SELLER's item - the thing being sold, which
-- both sides agreed on. Each document line additionally carries the
-- owning book's own product, because the buyer stocks it under their own
-- catalogue entry, not the seller's.
-- ---------------------------------------------------------------------
ALTER TABLE deal_line ADD COLUMN product_id BIGINT REFERENCES product (id);
ALTER TABLE document_line ADD COLUMN product_id BIGINT REFERENCES product (id);

CREATE INDEX idx_document_line_product ON document_line (product_id) WHERE product_id IS NOT NULL;

-- ---------------------------------------------------------------------
-- How a book relates to a counterparty.
--
-- Trading direction is decided per deal, so a party can already both
-- supply and buy. This column is the address-book label - it drives
-- filtering and nothing else, which is why BOTH is allowed here but the
-- party's own registered type stays a single value.
-- ---------------------------------------------------------------------
ALTER TABLE contact ADD COLUMN relationship VARCHAR(20) NOT NULL DEFAULT 'CUSTOMER';
ALTER TABLE contact ADD CONSTRAINT ck_contact_relationship
    CHECK (relationship IN ('CUSTOMER', 'VENDOR', 'BOTH'));
