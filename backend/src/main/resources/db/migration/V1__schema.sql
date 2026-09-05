-- =====================================================================
-- Multi-party double-entry accounting.
--
-- The organising idea: a BOOK is a complete, self-contained set of
-- accounts belonging to one party. Sellers and Vendors each get one.
-- Customers do not - they buy, they pay, they never keep accounts.
--
-- Every ledger row is owned by exactly one book, and no journal entry
-- may ever reference an account from another book. That isolation is
-- what makes "the seller's books" and "the vendor's books" meaningful
-- rather than one shared pot with a filter on top.
--
-- A trade between two book-keeping parties is a single DEAL that
-- produces one DOCUMENT per book: the buyer's Bill and the seller's
-- Invoice are two views of the same economic event, each posting its
-- own balanced entry into its own ledger.
-- =====================================================================

-- ---------------------------------------------------------------------
-- Platform-level sequences.
--
-- Deals and settlements span two books, so their numbers cannot come
-- from either book's own sequence.
-- ---------------------------------------------------------------------
CREATE TABLE platform_sequence (
    doc_type   VARCHAR(30) PRIMARY KEY,
    prefix     VARCHAR(10) NOT NULL,
    next_value BIGINT      NOT NULL DEFAULT 1,
    padding    INT         NOT NULL DEFAULT 5
);

-- ---------------------------------------------------------------------
-- Party: any tradeable identity.
--
-- One row per real-world organisation or person, shared across every
-- book that deals with them. That is what lets a customer who bought
-- from both a seller and a vendor see all their invoices in one place.
-- ---------------------------------------------------------------------
CREATE TABLE party (
    id           BIGSERIAL PRIMARY KEY,
    name         VARCHAR(180) NOT NULL,
    type         VARCHAR(20)  NOT NULL,
    email        VARCHAR(180),
    phone        VARCHAR(30),
    gstin        VARCHAR(20),
    address_line VARCHAR(255),
    city         VARCHAR(100),
    -- Drives the CGST/SGST versus IGST decision against the other side.
    state        VARCHAR(100),
    pincode      VARCHAR(20),
    active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ,
    created_by   VARCHAR(180),
    updated_by   VARCHAR(180),

    CONSTRAINT ck_party_type CHECK (type IN ('SELLER', 'VENDOR', 'CUSTOMER'))
);

CREATE INDEX idx_party_type ON party (type) WHERE active;

-- ---------------------------------------------------------------------
-- Book: one party's complete set of accounts.
--
-- The one-to-one with party is enforced by the UNIQUE on party_id. A
-- CUSTOMER party simply never gets a row here, which is how "customers
-- keep no books" is expressed structurally rather than by convention.
-- ---------------------------------------------------------------------
CREATE TABLE book (
    id                      BIGSERIAL PRIMARY KEY,
    party_id                BIGINT       NOT NULL UNIQUE REFERENCES party (id),
    name                    VARCHAR(180) NOT NULL,
    base_currency           VARCHAR(3)   NOT NULL DEFAULT 'INR',
    fiscal_year_start_month INT          NOT NULL DEFAULT 4,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ,
    created_by              VARCHAR(180),
    updated_by              VARCHAR(180),

    CONSTRAINT ck_book_fiscal_month CHECK (fiscal_year_start_month BETWEEN 1 AND 12)
);

-- ---------------------------------------------------------------------
-- Users.
--
-- Two independent dimensions:
--   * the party's TYPE says what you are in a trade (seller/vendor/customer)
--   * access_level says what you may do inside your own party's book
-- so a vendor company can have its own admin and its own accountant.
-- ---------------------------------------------------------------------
CREATE TABLE app_user (
    id            BIGSERIAL PRIMARY KEY,
    login_id      VARCHAR(12)  NOT NULL UNIQUE,
    email         VARCHAR(180) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    full_name     VARCHAR(150) NOT NULL,
    party_id      BIGINT       NOT NULL REFERENCES party (id),
    access_level  VARCHAR(20)  NOT NULL,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ,
    created_by    VARCHAR(180),
    updated_by    VARCHAR(180),

    CONSTRAINT ck_app_user_access_level CHECK (access_level IN ('ADMIN', 'ACCOUNTANT', 'USER')),
    -- The 6-12 rule from the brief, enforced in the database as well as
    -- the validator so it holds regardless of entry point.
    CONSTRAINT ck_app_user_login_id_length CHECK (char_length(login_id) BETWEEN 6 AND 12)
);

CREATE INDEX idx_app_user_party ON app_user (party_id);

-- ---------------------------------------------------------------------
-- Chart of accounts, per book.
--
-- `system_code` is how the posting engine finds an account. Resolving by
-- a stable code rather than by name means renaming "Bank" to "HDFC
-- Current A/c" in the UI cannot break the double-entry mapping.
-- ---------------------------------------------------------------------
CREATE TABLE account (
    id          BIGSERIAL PRIMARY KEY,
    book_id     BIGINT       NOT NULL REFERENCES book (id),
    code        VARCHAR(20)  NOT NULL,
    name        VARCHAR(150) NOT NULL,
    type        VARCHAR(20)  NOT NULL,
    system_code VARCHAR(40),
    is_system   BOOLEAN      NOT NULL DEFAULT FALSE,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ,
    created_by  VARCHAR(180),
    updated_by  VARCHAR(180),

    CONSTRAINT ck_account_type CHECK (type IN ('ASSET', 'LIABILITY', 'EQUITY', 'INCOME', 'EXPENSE')),
    CONSTRAINT uq_account_book_code UNIQUE (book_id, code),
    -- Postgres treats NULLs as distinct, so many non-system accounts can
    -- coexist while each system code stays unique within its book.
    CONSTRAINT uq_account_book_system UNIQUE (book_id, system_code)
);

CREATE INDEX idx_account_book ON account (book_id);

CREATE TABLE journal (
    id         BIGSERIAL PRIMARY KEY,
    book_id    BIGINT       NOT NULL REFERENCES book (id),
    code       VARCHAR(20)  NOT NULL,
    name       VARCHAR(120) NOT NULL,
    type       VARCHAR(20)  NOT NULL,
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    created_by VARCHAR(180),
    updated_by VARCHAR(180),

    CONSTRAINT ck_journal_type CHECK (type IN ('SALES', 'PURCHASE', 'CASH', 'BANK', 'GENERAL')),
    CONSTRAINT uq_journal_book_code UNIQUE (book_id, code),
    CONSTRAINT uq_journal_book_type UNIQUE (book_id, type)
);

-- Each book numbers its own documents from 1.
CREATE TABLE doc_sequence (
    id         BIGSERIAL PRIMARY KEY,
    book_id    BIGINT      NOT NULL REFERENCES book (id),
    doc_type   VARCHAR(30) NOT NULL,
    prefix     VARCHAR(10) NOT NULL,
    next_value BIGINT      NOT NULL DEFAULT 1,
    padding    INT         NOT NULL DEFAULT 4,

    CONSTRAINT uq_doc_sequence_book_type UNIQUE (book_id, doc_type)
);

-- ---------------------------------------------------------------------
-- Contact: one book's view of a counterparty.
--
-- The counterparty is a global party, so if that party also owns a book
-- the deal can be mirrored into it. If it does not - a walk-in customer,
-- an unregistered supplier - the deal stays single-sided.
-- ---------------------------------------------------------------------
CREATE TABLE contact (
    id          BIGSERIAL PRIMARY KEY,
    book_id     BIGINT      NOT NULL REFERENCES book (id),
    party_id    BIGINT      NOT NULL REFERENCES party (id),
    credit_days INT         NOT NULL DEFAULT 30,
    active      BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ,
    created_by  VARCHAR(180),
    updated_by  VARCHAR(180),

    CONSTRAINT uq_contact_book_party UNIQUE (book_id, party_id),
    CONSTRAINT ck_contact_credit_days CHECK (credit_days >= 0)
);

CREATE INDEX idx_contact_book ON contact (book_id);

CREATE TABLE product (
    id          BIGSERIAL PRIMARY KEY,
    book_id     BIGINT         NOT NULL REFERENCES book (id),
    name        VARCHAR(180)   NOT NULL,
    type        VARCHAR(20)    NOT NULL,
    sales_price NUMERIC(15, 2) NOT NULL DEFAULT 0.00,
    cost        NUMERIC(15, 2) NOT NULL DEFAULT 0.00,
    hsn_code    VARCHAR(20),
    tax_rate    NUMERIC(5, 2)  NOT NULL DEFAULT 0.00,
    category    VARCHAR(100),
    active      BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ,
    created_by  VARCHAR(180),
    updated_by  VARCHAR(180),

    CONSTRAINT ck_product_type CHECK (type IN ('GOODS', 'SERVICE')),
    CONSTRAINT ck_product_tax_rate CHECK (tax_rate >= 0 AND tax_rate <= 100)
);

CREATE INDEX idx_product_book ON product (book_id);

-- ---------------------------------------------------------------------
-- Analytic accounting: a second classification running alongside the
-- chart of accounts. The financial account says what kind of money
-- moved; the analytic account says whose project it belongs to.
-- ---------------------------------------------------------------------
CREATE TABLE analytic_account (
    id         BIGSERIAL PRIMARY KEY,
    book_id    BIGINT       NOT NULL REFERENCES book (id),
    code       VARCHAR(20)  NOT NULL,
    name       VARCHAR(150) NOT NULL,
    type       VARCHAR(30)  NOT NULL,
    notes      VARCHAR(500),
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    created_by VARCHAR(180),
    updated_by VARCHAR(180),

    CONSTRAINT ck_analytic_type CHECK (type IN ('PROJECT', 'DEPARTMENT', 'COST_CENTER')),
    CONSTRAINT uq_analytic_book_code UNIQUE (book_id, code)
);

CREATE TABLE budget (
    id                  BIGSERIAL PRIMARY KEY,
    book_id             BIGINT         NOT NULL REFERENCES book (id),
    name                VARCHAR(150)   NOT NULL,
    analytic_account_id BIGINT         NOT NULL REFERENCES analytic_account (id),
    period_start        DATE           NOT NULL,
    period_end          DATE           NOT NULL,
    -- The only stored figure. Actual spend is always aggregated from
    -- journal lines so the report cannot drift from the ledger.
    planned_amount      NUMERIC(15, 2) NOT NULL DEFAULT 0.00,
    responsible         VARCHAR(150),
    notes               VARCHAR(500),
    active              BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ,
    created_by          VARCHAR(180),
    updated_by          VARCHAR(180),

    CONSTRAINT ck_budget_period CHECK (period_end >= period_start),
    CONSTRAINT ck_budget_planned CHECK (planned_amount >= 0)
);

-- ---------------------------------------------------------------------
-- The ledger.
-- ---------------------------------------------------------------------
CREATE TABLE journal_entry (
    id          BIGSERIAL PRIMARY KEY,
    book_id     BIGINT      NOT NULL REFERENCES book (id),
    entry_no    VARCHAR(30) NOT NULL,
    journal_id  BIGINT      NOT NULL REFERENCES journal (id),
    entry_date  DATE        NOT NULL,
    source_type VARCHAR(20) NOT NULL,
    source_id   BIGINT,
    narration   VARCHAR(255),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by  VARCHAR(180),

    CONSTRAINT ck_journal_entry_source CHECK (
        source_type IN ('INVOICE', 'BILL', 'PAYMENT', 'OPENING', 'MANUAL')),
    CONSTRAINT uq_journal_entry_book_no UNIQUE (book_id, entry_no)
);

CREATE INDEX idx_journal_entry_book_date ON journal_entry (book_id, entry_date);

CREATE TABLE journal_line (
    id                  BIGSERIAL PRIMARY KEY,
    journal_entry_id    BIGINT         NOT NULL REFERENCES journal_entry (id) ON DELETE CASCADE,
    account_id          BIGINT         NOT NULL REFERENCES account (id),
    contact_id          BIGINT         REFERENCES contact (id),
    analytic_account_id BIGINT         REFERENCES analytic_account (id),
    line_no             INT            NOT NULL DEFAULT 1,
    label               VARCHAR(255),
    debit               NUMERIC(15, 2) NOT NULL DEFAULT 0.00,
    credit              NUMERIC(15, 2) NOT NULL DEFAULT 0.00,

    -- A line is one-sided by definition. Allowing both would let an
    -- entry "balance" while saying nothing.
    CONSTRAINT ck_journal_line_one_sided CHECK (
        (debit > 0 AND credit = 0) OR (credit > 0 AND debit = 0))
);

CREATE INDEX idx_journal_line_entry ON journal_line (journal_entry_id);
CREATE INDEX idx_journal_line_account ON journal_line (account_id);
CREATE INDEX idx_journal_line_contact ON journal_line (contact_id) WHERE contact_id IS NOT NULL;
CREATE INDEX idx_journal_line_analytic ON journal_line (analytic_account_id) WHERE analytic_account_id IS NOT NULL;

-- ---------------------------------------------------------------------
-- Deal: the shared economic event.
--
-- Exists once, above both books. The buyer's purchase order and the
-- seller's sales order are two views of this row, which is why
-- confirming once moves the deal for both sides and the two can never
-- disagree about what was agreed.
-- ---------------------------------------------------------------------
CREATE TABLE deal (
    id                    BIGSERIAL PRIMARY KEY,
    deal_no               VARCHAR(30)    NOT NULL UNIQUE,
    buyer_party_id        BIGINT         NOT NULL REFERENCES party (id),
    seller_party_id       BIGINT         NOT NULL REFERENCES party (id),
    initiated_by_party_id BIGINT         NOT NULL REFERENCES party (id),
    status                VARCHAR(20)    NOT NULL,
    deal_date             DATE           NOT NULL,
    expected_delivery     DATE,
    delivered_at          DATE,
    -- The buyer's state, frozen when the deal is raised. A customer who
    -- relocates next year must not retroactively change the tax that was
    -- correctly charged today.
    place_of_supply       VARCHAR(100),
    untaxed_amount        NUMERIC(15, 2) NOT NULL DEFAULT 0.00,
    tax_amount            NUMERIC(15, 2) NOT NULL DEFAULT 0.00,
    total_amount          NUMERIC(15, 2) NOT NULL DEFAULT 0.00,
    notes                 VARCHAR(500),
    reject_reason         VARCHAR(500),
    created_at            TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ,
    created_by            VARCHAR(180),
    updated_by            VARCHAR(180),

    CONSTRAINT ck_deal_status CHECK (status IN (
        'RFQ_DRAFT', 'RFQ_SENT', 'REJECTED', 'ACCEPTED',
        'DELIVERED', 'INVOICED', 'PARTIALLY_PAID', 'PAID', 'CANCELLED')),
    -- Nobody trades with themselves.
    CONSTRAINT ck_deal_distinct_parties CHECK (buyer_party_id <> seller_party_id)
);

CREATE INDEX idx_deal_buyer ON deal (buyer_party_id);
CREATE INDEX idx_deal_seller ON deal (seller_party_id);
CREATE INDEX idx_deal_status ON deal (status);

CREATE TABLE deal_line (
    id             BIGSERIAL PRIMARY KEY,
    deal_id        BIGINT         NOT NULL REFERENCES deal (id) ON DELETE CASCADE,
    line_no        INT            NOT NULL DEFAULT 1,
    -- A snapshot, not a foreign key: products are book-scoped, and the
    -- two sides of a deal have different catalogues.
    description    VARCHAR(180)   NOT NULL,
    hsn_code       VARCHAR(20),
    quantity       NUMERIC(15, 3) NOT NULL,
    unit_price     NUMERIC(15, 2) NOT NULL,
    tax_rate       NUMERIC(5, 2)  NOT NULL DEFAULT 0.00,
    untaxed_amount NUMERIC(15, 2) NOT NULL DEFAULT 0.00,
    tax_amount     NUMERIC(15, 2) NOT NULL DEFAULT 0.00,
    -- tax_amount decomposed; the three always sum back to it.
    cgst_amount    NUMERIC(15, 2) NOT NULL DEFAULT 0.00,
    sgst_amount    NUMERIC(15, 2) NOT NULL DEFAULT 0.00,
    igst_amount    NUMERIC(15, 2) NOT NULL DEFAULT 0.00,
    line_total     NUMERIC(15, 2) NOT NULL DEFAULT 0.00,

    CONSTRAINT ck_deal_line_quantity CHECK (quantity > 0)
);

CREATE INDEX idx_deal_line_deal ON deal_line (deal_id);

-- ---------------------------------------------------------------------
-- Document: one book's paperwork for a deal.
--
-- Two rows for a seller-to-vendor deal (the buyer's and the seller's),
-- one row when the counterparty keeps no books.
-- ---------------------------------------------------------------------
CREATE TABLE document (
    id               BIGSERIAL PRIMARY KEY,
    deal_id          BIGINT         NOT NULL REFERENCES deal (id),
    book_id          BIGINT         NOT NULL REFERENCES book (id),
    doc_type         VARCHAR(20)    NOT NULL,
    doc_no           VARCHAR(30)    NOT NULL,
    doc_date         DATE           NOT NULL,
    due_date         DATE,
    status           VARCHAR(20)    NOT NULL,
    contact_id       BIGINT         REFERENCES contact (id),
    place_of_supply  VARCHAR(100),
    untaxed_amount   NUMERIC(15, 2) NOT NULL DEFAULT 0.00,
    tax_amount       NUMERIC(15, 2) NOT NULL DEFAULT 0.00,
    cgst_amount      NUMERIC(15, 2) NOT NULL DEFAULT 0.00,
    sgst_amount      NUMERIC(15, 2) NOT NULL DEFAULT 0.00,
    igst_amount      NUMERIC(15, 2) NOT NULL DEFAULT 0.00,
    total_amount     NUMERIC(15, 2) NOT NULL DEFAULT 0.00,
    amount_settled   NUMERIC(15, 2) NOT NULL DEFAULT 0.00,
    journal_entry_id BIGINT         REFERENCES journal_entry (id),
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ,
    created_by       VARCHAR(180),
    updated_by       VARCHAR(180),

    CONSTRAINT ck_document_type CHECK (doc_type IN (
        'SALES_ORDER', 'PURCHASE_ORDER', 'INVOICE', 'BILL')),
    CONSTRAINT ck_document_status CHECK (status IN (
        'OPEN', 'POSTED', 'PARTIALLY_PAID', 'PAID', 'CANCELLED')),
    CONSTRAINT uq_document_book_no UNIQUE (book_id, doc_no),
    -- One document of each kind per book per deal.
    CONSTRAINT uq_document_deal_book_type UNIQUE (deal_id, book_id, doc_type)
);

CREATE INDEX idx_document_book ON document (book_id, doc_type);
CREATE INDEX idx_document_deal ON document (deal_id);

CREATE TABLE document_line (
    id                  BIGSERIAL PRIMARY KEY,
    document_id         BIGINT         NOT NULL REFERENCES document (id) ON DELETE CASCADE,
    deal_line_id        BIGINT         REFERENCES deal_line (id),
    line_no             INT            NOT NULL DEFAULT 1,
    description         VARCHAR(180)   NOT NULL,
    hsn_code            VARCHAR(20),
    -- Each side may tag the same line to its own project, so this lives
    -- on the document rather than on the shared deal line.
    analytic_account_id BIGINT         REFERENCES analytic_account (id),
    quantity            NUMERIC(15, 3) NOT NULL,
    unit_price          NUMERIC(15, 2) NOT NULL,
    tax_rate            NUMERIC(5, 2)  NOT NULL DEFAULT 0.00,
    untaxed_amount      NUMERIC(15, 2) NOT NULL DEFAULT 0.00,
    tax_amount          NUMERIC(15, 2) NOT NULL DEFAULT 0.00,
    cgst_amount         NUMERIC(15, 2) NOT NULL DEFAULT 0.00,
    sgst_amount         NUMERIC(15, 2) NOT NULL DEFAULT 0.00,
    igst_amount         NUMERIC(15, 2) NOT NULL DEFAULT 0.00,
    line_total          NUMERIC(15, 2) NOT NULL DEFAULT 0.00
);

CREATE INDEX idx_document_line_document ON document_line (document_id);

-- ---------------------------------------------------------------------
-- Settlement: the shared money movement, mirrored like the deal.
--
-- One payment of a mirrored deal is simultaneously the seller's receipt
-- and the buyer's disbursement, so it is recorded once here and drawn
-- into each book as its own payment row.
-- ---------------------------------------------------------------------
CREATE TABLE settlement (
    id                   BIGSERIAL PRIMARY KEY,
    settlement_no        VARCHAR(30)    NOT NULL UNIQUE,
    deal_id              BIGINT         NOT NULL REFERENCES deal (id),
    settlement_date      DATE           NOT NULL,
    amount               NUMERIC(15, 2) NOT NULL,
    method               VARCHAR(10)    NOT NULL,
    reference            VARCHAR(120),
    recorded_by_party_id BIGINT         NOT NULL REFERENCES party (id),
    created_at           TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    created_by           VARCHAR(180),

    CONSTRAINT ck_settlement_method CHECK (method IN ('CASH', 'BANK')),
    CONSTRAINT ck_settlement_amount CHECK (amount > 0)
);

CREATE TABLE payment (
    id               BIGSERIAL PRIMARY KEY,
    book_id          BIGINT         NOT NULL REFERENCES book (id),
    settlement_id    BIGINT         NOT NULL REFERENCES settlement (id),
    document_id      BIGINT         NOT NULL REFERENCES document (id),
    payment_no       VARCHAR(30)    NOT NULL,
    direction        VARCHAR(10)    NOT NULL,
    method           VARCHAR(10)    NOT NULL,
    payment_date     DATE           NOT NULL,
    amount           NUMERIC(15, 2) NOT NULL,
    reference        VARCHAR(120),
    journal_entry_id BIGINT         REFERENCES journal_entry (id),
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    created_by       VARCHAR(180),

    CONSTRAINT ck_payment_direction CHECK (direction IN ('IN', 'OUT')),
    CONSTRAINT ck_payment_method CHECK (method IN ('CASH', 'BANK')),
    CONSTRAINT ck_payment_amount CHECK (amount > 0),
    CONSTRAINT uq_payment_book_no UNIQUE (book_id, payment_no),
    CONSTRAINT uq_payment_settlement_book UNIQUE (settlement_id, book_id)
);

CREATE INDEX idx_payment_book ON payment (book_id);
CREATE INDEX idx_payment_document ON payment (document_id);

-- ---------------------------------------------------------------------
-- Audit log.
--
-- No foreign key to the audited row on purpose: archiving or deleting a
-- record must never erase the evidence of what was done to it.
-- book_id is nullable because some events (signup) precede any book.
-- ---------------------------------------------------------------------
CREATE TABLE audit_log (
    id          BIGSERIAL PRIMARY KEY,
    book_id     BIGINT,
    entity_type VARCHAR(80)  NOT NULL,
    entity_id   BIGINT       NOT NULL,
    action      VARCHAR(20)  NOT NULL,
    field_name  VARCHAR(80),
    old_value   VARCHAR(500),
    new_value   VARCHAR(500),
    changed_by  VARCHAR(180) NOT NULL,
    changed_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_audit_action CHECK (action IN ('CREATE', 'UPDATE', 'DELETE'))
);

CREATE INDEX idx_audit_entity ON audit_log (entity_type, entity_id, changed_at DESC);
CREATE INDEX idx_audit_changed_at ON audit_log (changed_at DESC);
CREATE INDEX idx_audit_book ON audit_log (book_id, changed_at DESC);
