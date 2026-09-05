-- =====================================================================
-- Urban Furniture - Accounting System : core schema
-- Flyway owns the schema. Hibernate is set to `validate` and may never
-- mutate it. Money is NUMERIC(15,2); quantities are NUMERIC(15,3).
-- =====================================================================

-- ---------------------------------------------------------------------
-- Document numbering (PO/SO/BILL/INV/PAY/JE). Allocated under a row
-- lock so concurrent requests can never receive the same number.
-- ---------------------------------------------------------------------
CREATE TABLE doc_sequence (
    id          BIGSERIAL PRIMARY KEY,
    doc_type    VARCHAR(30)  NOT NULL UNIQUE,
    prefix      VARCHAR(10)  NOT NULL,
    next_value  BIGINT       NOT NULL DEFAULT 1,
    padding     INT          NOT NULL DEFAULT 4
);

-- ---------------------------------------------------------------------
-- Company profile (single row). Seller state drives CGST/SGST vs IGST.
-- ---------------------------------------------------------------------
CREATE TABLE company_profile (
    id            BIGSERIAL PRIMARY KEY,
    name          VARCHAR(180) NOT NULL,
    gstin         VARCHAR(20),
    address_line  VARCHAR(255),
    city          VARCHAR(100),
    state         VARCHAR(100),
    pincode       VARCHAR(20),
    email         VARCHAR(180),
    mobile        VARCHAR(30),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ,
    created_by    VARCHAR(180),
    updated_by    VARCHAR(180)
);

-- ---------------------------------------------------------------------
-- Contacts (customers / vendors)
-- ---------------------------------------------------------------------
CREATE TABLE contact (
    id                BIGSERIAL PRIMARY KEY,
    name              VARCHAR(180) NOT NULL,
    type              VARCHAR(20)  NOT NULL,
    email             VARCHAR(180),
    mobile            VARCHAR(30),
    address_line      VARCHAR(255),
    city              VARCHAR(100),
    state             VARCHAR(100),
    pincode           VARCHAR(20),
    gstin             VARCHAR(20),
    profile_image_url VARCHAR(500),
    active            BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ,
    created_by        VARCHAR(180),
    updated_by        VARCHAR(180),
    CONSTRAINT ck_contact_type CHECK (type IN ('CUSTOMER', 'VENDOR', 'BOTH'))
);
CREATE INDEX idx_contact_active ON contact (active);
CREATE INDEX idx_contact_type   ON contact (type);

-- ---------------------------------------------------------------------
-- Application users. A CONTACT-role user is bound to exactly one contact
-- row; that binding is what row-level filtering keys off of.
-- ---------------------------------------------------------------------
CREATE TABLE app_user (
    id             BIGSERIAL PRIMARY KEY,
    email          VARCHAR(180) NOT NULL UNIQUE,
    password_hash  VARCHAR(100) NOT NULL,
    full_name      VARCHAR(150) NOT NULL,
    role           VARCHAR(20)  NOT NULL,
    contact_id     BIGINT,
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ,
    created_by     VARCHAR(180),
    updated_by     VARCHAR(180),
    CONSTRAINT fk_app_user_contact FOREIGN KEY (contact_id) REFERENCES contact (id),
    CONSTRAINT ck_app_user_role CHECK (role IN ('ADMIN', 'ACCOUNTANT', 'CONTACT')),
    -- A CONTACT user MUST be linked to a contact; staff users must not be.
    CONSTRAINT ck_app_user_contact_link CHECK (
        (role = 'CONTACT' AND contact_id IS NOT NULL)
        OR (role <> 'CONTACT' AND contact_id IS NULL)
    )
);
CREATE UNIQUE INDEX uq_app_user_contact ON app_user (contact_id) WHERE contact_id IS NOT NULL;

-- ---------------------------------------------------------------------
-- Products
-- ---------------------------------------------------------------------
CREATE TABLE product (
    id           BIGSERIAL PRIMARY KEY,
    name         VARCHAR(180)   NOT NULL,
    type         VARCHAR(20)    NOT NULL,
    sales_price  NUMERIC(15, 2) NOT NULL DEFAULT 0,
    cost         NUMERIC(15, 2) NOT NULL DEFAULT 0,
    category     VARCHAR(100),
    hsn_code     VARCHAR(20),
    tax_rate     NUMERIC(5, 2)  NOT NULL DEFAULT 0,
    active       BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ,
    created_by   VARCHAR(180),
    updated_by   VARCHAR(180),
    CONSTRAINT ck_product_type CHECK (type IN ('GOODS', 'SERVICE', 'COMBO')),
    CONSTRAINT ck_product_prices CHECK (sales_price >= 0 AND cost >= 0 AND tax_rate >= 0)
);
CREATE INDEX idx_product_active ON product (active);

-- ---------------------------------------------------------------------
-- Chart of Accounts
-- `system_code` marks the accounts the posting engine resolves by name,
-- so they can never be renamed out from under the double-entry mapping.
-- ---------------------------------------------------------------------
CREATE TABLE account (
    id           BIGSERIAL PRIMARY KEY,
    code         VARCHAR(20)  NOT NULL UNIQUE,
    name         VARCHAR(150) NOT NULL,
    type         VARCHAR(20)  NOT NULL,
    system_code  VARCHAR(40) UNIQUE,
    is_system    BOOLEAN      NOT NULL DEFAULT FALSE,
    active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ,
    created_by   VARCHAR(180),
    updated_by   VARCHAR(180),
    CONSTRAINT ck_account_type CHECK (type IN ('ASSET', 'LIABILITY', 'EQUITY', 'INCOME', 'EXPENSE'))
);
CREATE INDEX idx_account_type ON account (type);

-- ---------------------------------------------------------------------
-- Journals
-- ---------------------------------------------------------------------
CREATE TABLE journal (
    id                 BIGSERIAL PRIMARY KEY,
    code               VARCHAR(20)  NOT NULL UNIQUE,
    name               VARCHAR(120) NOT NULL,
    type               VARCHAR(20)  NOT NULL,
    default_account_id BIGINT,
    active             BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ,
    created_by         VARCHAR(180),
    updated_by         VARCHAR(180),
    CONSTRAINT fk_journal_account FOREIGN KEY (default_account_id) REFERENCES account (id),
    CONSTRAINT ck_journal_type CHECK (type IN ('SALES', 'PURCHASE', 'CASH', 'BANK'))
);

-- ---------------------------------------------------------------------
-- The ledger. Every financial event lands here as a balanced entry.
-- Reports are computed by aggregating journal_line - never from a
-- separately maintained summary table.
-- ---------------------------------------------------------------------
CREATE TABLE journal_entry (
    id           BIGSERIAL PRIMARY KEY,
    entry_no     VARCHAR(30)  NOT NULL UNIQUE,
    journal_id   BIGINT       NOT NULL,
    entry_date   DATE         NOT NULL,
    source_type  VARCHAR(20)  NOT NULL,
    source_id    BIGINT,
    narration    VARCHAR(255),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by   VARCHAR(180),
    CONSTRAINT fk_je_journal FOREIGN KEY (journal_id) REFERENCES journal (id),
    CONSTRAINT ck_je_source CHECK (source_type IN ('INVOICE', 'BILL', 'PAYMENT', 'MANUAL'))
);
CREATE INDEX idx_je_date   ON journal_entry (entry_date);
CREATE INDEX idx_je_source ON journal_entry (source_type, source_id);

CREATE TABLE journal_line (
    id               BIGSERIAL PRIMARY KEY,
    journal_entry_id BIGINT         NOT NULL,
    account_id       BIGINT         NOT NULL,
    contact_id       BIGINT,
    line_no          INT            NOT NULL DEFAULT 1,
    label            VARCHAR(255),
    debit            NUMERIC(15, 2) NOT NULL DEFAULT 0,
    credit           NUMERIC(15, 2) NOT NULL DEFAULT 0,
    CONSTRAINT fk_jl_entry   FOREIGN KEY (journal_entry_id) REFERENCES journal_entry (id) ON DELETE CASCADE,
    CONSTRAINT fk_jl_account FOREIGN KEY (account_id) REFERENCES account (id),
    CONSTRAINT fk_jl_contact FOREIGN KEY (contact_id) REFERENCES contact (id),
    CONSTRAINT ck_jl_amounts_non_negative CHECK (debit >= 0 AND credit >= 0),
    -- A line is either a debit or a credit, never both, never neither.
    CONSTRAINT ck_jl_one_sided CHECK (
        (debit > 0 AND credit = 0) OR (credit > 0 AND debit = 0)
    )
);
CREATE INDEX idx_jl_entry   ON journal_line (journal_entry_id);
CREATE INDEX idx_jl_account ON journal_line (account_id);
CREATE INDEX idx_jl_contact ON journal_line (contact_id);

-- ---------------------------------------------------------------------
-- Sales: Order -> Invoice
-- ---------------------------------------------------------------------
CREATE TABLE sales_order (
    id              BIGSERIAL PRIMARY KEY,
    order_no        VARCHAR(30)    NOT NULL UNIQUE,
    contact_id      BIGINT         NOT NULL,
    order_date      DATE           NOT NULL,
    status          VARCHAR(20)    NOT NULL DEFAULT 'DRAFT',
    untaxed_amount  NUMERIC(15, 2) NOT NULL DEFAULT 0,
    tax_amount      NUMERIC(15, 2) NOT NULL DEFAULT 0,
    total_amount    NUMERIC(15, 2) NOT NULL DEFAULT 0,
    notes           VARCHAR(500),
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ,
    created_by      VARCHAR(180),
    updated_by      VARCHAR(180),
    CONSTRAINT fk_so_contact FOREIGN KEY (contact_id) REFERENCES contact (id),
    CONSTRAINT ck_so_status CHECK (status IN ('DRAFT', 'CONFIRMED', 'INVOICED', 'CANCELLED'))
);
CREATE INDEX idx_so_contact ON sales_order (contact_id);

CREATE TABLE sales_order_line (
    id             BIGSERIAL PRIMARY KEY,
    sales_order_id BIGINT         NOT NULL,
    product_id     BIGINT         NOT NULL,
    line_no        INT            NOT NULL DEFAULT 1,
    quantity       NUMERIC(15, 3) NOT NULL,
    unit_price     NUMERIC(15, 2) NOT NULL,
    tax_rate       NUMERIC(5, 2)  NOT NULL DEFAULT 0,
    untaxed_amount NUMERIC(15, 2) NOT NULL DEFAULT 0,
    tax_amount     NUMERIC(15, 2) NOT NULL DEFAULT 0,
    line_total     NUMERIC(15, 2) NOT NULL DEFAULT 0,
    CONSTRAINT fk_sol_order   FOREIGN KEY (sales_order_id) REFERENCES sales_order (id) ON DELETE CASCADE,
    CONSTRAINT fk_sol_product FOREIGN KEY (product_id) REFERENCES product (id),
    CONSTRAINT ck_sol_qty CHECK (quantity > 0 AND unit_price >= 0)
);
CREATE INDEX idx_sol_order ON sales_order_line (sales_order_id);

CREATE TABLE invoice (
    id               BIGSERIAL PRIMARY KEY,
    invoice_no       VARCHAR(30)    NOT NULL UNIQUE,
    sales_order_id   BIGINT,
    contact_id       BIGINT         NOT NULL,
    invoice_date     DATE           NOT NULL,
    due_date         DATE,
    status           VARCHAR(20)    NOT NULL DEFAULT 'DRAFT',
    untaxed_amount   NUMERIC(15, 2) NOT NULL DEFAULT 0,
    tax_amount       NUMERIC(15, 2) NOT NULL DEFAULT 0,
    total_amount     NUMERIC(15, 2) NOT NULL DEFAULT 0,
    amount_paid      NUMERIC(15, 2) NOT NULL DEFAULT 0,
    journal_entry_id BIGINT,
    notes            VARCHAR(500),
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ,
    created_by       VARCHAR(180),
    updated_by       VARCHAR(180),
    CONSTRAINT fk_inv_contact FOREIGN KEY (contact_id) REFERENCES contact (id),
    CONSTRAINT fk_inv_so      FOREIGN KEY (sales_order_id) REFERENCES sales_order (id),
    CONSTRAINT fk_inv_je      FOREIGN KEY (journal_entry_id) REFERENCES journal_entry (id),
    CONSTRAINT ck_inv_status CHECK (status IN ('DRAFT', 'POSTED', 'PARTIALLY_PAID', 'PAID', 'CANCELLED')),
    CONSTRAINT ck_inv_paid CHECK (amount_paid >= 0)
);
CREATE INDEX idx_inv_contact ON invoice (contact_id);
CREATE INDEX idx_inv_status  ON invoice (status);

CREATE TABLE invoice_line (
    id             BIGSERIAL PRIMARY KEY,
    invoice_id     BIGINT         NOT NULL,
    product_id     BIGINT         NOT NULL,
    line_no        INT            NOT NULL DEFAULT 1,
    quantity       NUMERIC(15, 3) NOT NULL,
    unit_price     NUMERIC(15, 2) NOT NULL,
    tax_rate       NUMERIC(5, 2)  NOT NULL DEFAULT 0,
    untaxed_amount NUMERIC(15, 2) NOT NULL DEFAULT 0,
    tax_amount     NUMERIC(15, 2) NOT NULL DEFAULT 0,
    line_total     NUMERIC(15, 2) NOT NULL DEFAULT 0,
    CONSTRAINT fk_invl_invoice FOREIGN KEY (invoice_id) REFERENCES invoice (id) ON DELETE CASCADE,
    CONSTRAINT fk_invl_product FOREIGN KEY (product_id) REFERENCES product (id),
    CONSTRAINT ck_invl_qty CHECK (quantity > 0 AND unit_price >= 0)
);
CREATE INDEX idx_invl_invoice ON invoice_line (invoice_id);

-- ---------------------------------------------------------------------
-- Purchases: Order -> Vendor Bill
-- ---------------------------------------------------------------------
CREATE TABLE purchase_order (
    id              BIGSERIAL PRIMARY KEY,
    order_no        VARCHAR(30)    NOT NULL UNIQUE,
    contact_id      BIGINT         NOT NULL,
    order_date      DATE           NOT NULL,
    status          VARCHAR(20)    NOT NULL DEFAULT 'DRAFT',
    untaxed_amount  NUMERIC(15, 2) NOT NULL DEFAULT 0,
    tax_amount      NUMERIC(15, 2) NOT NULL DEFAULT 0,
    total_amount    NUMERIC(15, 2) NOT NULL DEFAULT 0,
    notes           VARCHAR(500),
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ,
    created_by      VARCHAR(180),
    updated_by      VARCHAR(180),
    CONSTRAINT fk_po_contact FOREIGN KEY (contact_id) REFERENCES contact (id),
    CONSTRAINT ck_po_status CHECK (status IN ('DRAFT', 'CONFIRMED', 'BILLED', 'CANCELLED'))
);
CREATE INDEX idx_po_contact ON purchase_order (contact_id);

CREATE TABLE purchase_order_line (
    id                BIGSERIAL PRIMARY KEY,
    purchase_order_id BIGINT         NOT NULL,
    product_id        BIGINT         NOT NULL,
    line_no           INT            NOT NULL DEFAULT 1,
    quantity          NUMERIC(15, 3) NOT NULL,
    unit_price        NUMERIC(15, 2) NOT NULL,
    tax_rate          NUMERIC(5, 2)  NOT NULL DEFAULT 0,
    untaxed_amount    NUMERIC(15, 2) NOT NULL DEFAULT 0,
    tax_amount        NUMERIC(15, 2) NOT NULL DEFAULT 0,
    line_total        NUMERIC(15, 2) NOT NULL DEFAULT 0,
    CONSTRAINT fk_pol_order   FOREIGN KEY (purchase_order_id) REFERENCES purchase_order (id) ON DELETE CASCADE,
    CONSTRAINT fk_pol_product FOREIGN KEY (product_id) REFERENCES product (id),
    CONSTRAINT ck_pol_qty CHECK (quantity > 0 AND unit_price >= 0)
);
CREATE INDEX idx_pol_order ON purchase_order_line (purchase_order_id);

CREATE TABLE bill (
    id                BIGSERIAL PRIMARY KEY,
    bill_no           VARCHAR(30)    NOT NULL UNIQUE,
    purchase_order_id BIGINT,
    contact_id        BIGINT         NOT NULL,
    bill_date         DATE           NOT NULL,
    due_date          DATE,
    status            VARCHAR(20)    NOT NULL DEFAULT 'DRAFT',
    untaxed_amount    NUMERIC(15, 2) NOT NULL DEFAULT 0,
    tax_amount        NUMERIC(15, 2) NOT NULL DEFAULT 0,
    total_amount      NUMERIC(15, 2) NOT NULL DEFAULT 0,
    amount_paid       NUMERIC(15, 2) NOT NULL DEFAULT 0,
    journal_entry_id  BIGINT,
    notes             VARCHAR(500),
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ,
    created_by        VARCHAR(180),
    updated_by        VARCHAR(180),
    CONSTRAINT fk_bill_contact FOREIGN KEY (contact_id) REFERENCES contact (id),
    CONSTRAINT fk_bill_po      FOREIGN KEY (purchase_order_id) REFERENCES purchase_order (id),
    CONSTRAINT fk_bill_je      FOREIGN KEY (journal_entry_id) REFERENCES journal_entry (id),
    CONSTRAINT ck_bill_status CHECK (status IN ('DRAFT', 'POSTED', 'PARTIALLY_PAID', 'PAID', 'CANCELLED')),
    CONSTRAINT ck_bill_paid CHECK (amount_paid >= 0)
);
CREATE INDEX idx_bill_contact ON bill (contact_id);
CREATE INDEX idx_bill_status  ON bill (status);

CREATE TABLE bill_line (
    id             BIGSERIAL PRIMARY KEY,
    bill_id        BIGINT         NOT NULL,
    product_id     BIGINT         NOT NULL,
    line_no        INT            NOT NULL DEFAULT 1,
    quantity       NUMERIC(15, 3) NOT NULL,
    unit_price     NUMERIC(15, 2) NOT NULL,
    tax_rate       NUMERIC(5, 2)  NOT NULL DEFAULT 0,
    untaxed_amount NUMERIC(15, 2) NOT NULL DEFAULT 0,
    tax_amount     NUMERIC(15, 2) NOT NULL DEFAULT 0,
    line_total     NUMERIC(15, 2) NOT NULL DEFAULT 0,
    CONSTRAINT fk_billl_bill    FOREIGN KEY (bill_id) REFERENCES bill (id) ON DELETE CASCADE,
    CONSTRAINT fk_billl_product FOREIGN KEY (product_id) REFERENCES product (id),
    CONSTRAINT ck_billl_qty CHECK (quantity > 0 AND unit_price >= 0)
);
CREATE INDEX idx_billl_bill ON bill_line (bill_id);

-- ---------------------------------------------------------------------
-- Payments (against an invoice OR a bill, never both)
-- ---------------------------------------------------------------------
CREATE TABLE payment (
    id               BIGSERIAL PRIMARY KEY,
    payment_no       VARCHAR(30)    NOT NULL UNIQUE,
    contact_id       BIGINT         NOT NULL,
    direction        VARCHAR(20)    NOT NULL,
    method           VARCHAR(20)    NOT NULL,
    payment_date     DATE           NOT NULL,
    amount           NUMERIC(15, 2) NOT NULL,
    invoice_id       BIGINT,
    bill_id          BIGINT,
    journal_entry_id BIGINT,
    reference        VARCHAR(120),
    reconciled       BOOLEAN        NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ,
    created_by       VARCHAR(180),
    updated_by       VARCHAR(180),
    CONSTRAINT fk_pay_contact FOREIGN KEY (contact_id) REFERENCES contact (id),
    CONSTRAINT fk_pay_invoice FOREIGN KEY (invoice_id) REFERENCES invoice (id),
    CONSTRAINT fk_pay_bill    FOREIGN KEY (bill_id) REFERENCES bill (id),
    CONSTRAINT fk_pay_je      FOREIGN KEY (journal_entry_id) REFERENCES journal_entry (id),
    CONSTRAINT ck_pay_direction CHECK (direction IN ('RECEIVE', 'PAY')),
    CONSTRAINT ck_pay_method CHECK (method IN ('CASH', 'BANK')),
    CONSTRAINT ck_pay_amount CHECK (amount > 0),
    CONSTRAINT ck_pay_target CHECK (
        (invoice_id IS NOT NULL AND bill_id IS NULL)
        OR (invoice_id IS NULL AND bill_id IS NOT NULL)
    )
);
CREATE INDEX idx_pay_contact ON payment (contact_id);
CREATE INDEX idx_pay_invoice ON payment (invoice_id);
CREATE INDEX idx_pay_bill    ON payment (bill_id);
