-- =====================================================================
-- Seed data: chart of accounts, journals, document sequences, company
-- profile and demo logins.
-- =====================================================================

-- ---------------------------------------------------------------------
-- Document sequences
-- ---------------------------------------------------------------------
INSERT INTO doc_sequence (doc_type, prefix, next_value, padding) VALUES
    ('SALES_ORDER',    'SO',   1, 4),
    ('PURCHASE_ORDER', 'PO',   1, 4),
    ('INVOICE',        'INV',  1, 4),
    ('BILL',           'BILL', 1, 4),
    ('PAYMENT',        'PAY',  1, 4),
    ('JOURNAL_ENTRY',  'JE',   1, 5);

-- ---------------------------------------------------------------------
-- Company profile (seller). `state` drives CGST/SGST vs IGST in Phase 2.
-- ---------------------------------------------------------------------
INSERT INTO company_profile (name, gstin, address_line, city, state, pincode, email, mobile, created_by)
VALUES ('Urban Furniture', '24ABCDE1234F1Z5', '12 Ashram Road', 'Ahmedabad', 'Gujarat', '380009',
        'accounts@urbanfurniture.test', '+91 79000 00000', 'system');

-- ---------------------------------------------------------------------
-- Chart of Accounts
-- `is_system = TRUE` accounts are referenced by the posting engine via
-- `system_code` and cannot be deleted.
-- ---------------------------------------------------------------------
INSERT INTO account (code, name, type, system_code, is_system, created_by) VALUES
    ('1000', 'Cash',                    'ASSET',     'CASH',             TRUE,  'system'),
    ('1010', 'Bank',                    'ASSET',     'BANK',             TRUE,  'system'),
    ('1100', 'Debtors (Accounts Receivable)', 'ASSET', 'DEBTORS',        TRUE,  'system'),
    ('1200', 'Input Tax Credit',        'ASSET',     'TAX_RECEIVABLE',   TRUE,  'system'),
    ('1300', 'Inventory',               'ASSET',     'INVENTORY',        TRUE,  'system'),
    ('2000', 'Creditors (Accounts Payable)', 'LIABILITY', 'CREDITORS',   TRUE,  'system'),
    ('2100', 'Tax Payable',             'LIABILITY', 'TAX_PAYABLE',      TRUE,  'system'),
    ('3000', 'Owner''s Capital',        'EQUITY',    'CAPITAL',          TRUE,  'system'),
    ('4000', 'Sales Income',            'INCOME',    'SALES_INCOME',     TRUE,  'system'),
    ('4100', 'Other Income',            'INCOME',    NULL,               FALSE, 'system'),
    ('5000', 'Purchase Expense',        'EXPENSE',   'PURCHASE_EXPENSE', TRUE,  'system'),
    ('5100', 'Operating Expense',       'EXPENSE',   NULL,               FALSE, 'system');

-- ---------------------------------------------------------------------
-- Journals
-- ---------------------------------------------------------------------
INSERT INTO journal (code, name, type, default_account_id, created_by)
SELECT 'SAL', 'Sales Journal', 'SALES', a.id, 'system' FROM account a WHERE a.system_code = 'SALES_INCOME';

INSERT INTO journal (code, name, type, default_account_id, created_by)
SELECT 'PUR', 'Purchase Journal', 'PURCHASE', a.id, 'system' FROM account a WHERE a.system_code = 'PURCHASE_EXPENSE';

INSERT INTO journal (code, name, type, default_account_id, created_by)
SELECT 'CSH', 'Cash Journal', 'CASH', a.id, 'system' FROM account a WHERE a.system_code = 'CASH';

INSERT INTO journal (code, name, type, default_account_id, created_by)
SELECT 'BNK', 'Bank Journal', 'BANK', a.id, 'system' FROM account a WHERE a.system_code = 'BANK';

-- ---------------------------------------------------------------------
-- Demo logins (for the hackathon demo - change/remove before production)
--   admin@urbanfurniture.test      / Admin@123
--   accountant@urbanfurniture.test / Accountant@123
-- ---------------------------------------------------------------------
INSERT INTO app_user (email, password_hash, full_name, role, created_by) VALUES
    ('admin@urbanfurniture.test',
     '$2b$10$GKEHNAmmaDUMHZFskk65eOqHEkkCIzKlcVs88nYpeCZVZ6e3ob99W',
     'Business Owner', 'ADMIN', 'system'),
    ('accountant@urbanfurniture.test',
     '$2b$10$sF5a01oeQzs4l3gVHyee7Os5sSEzDmhO05Ry3kdVXlvhG4n5C4nRS',
     'Invoicing User', 'ACCOUNTANT', 'system');
