# Multi-party double-entry accounting

Sellers and Vendors each keep their **own complete set of books**. Customers buy from
either and keep none. Every rupee that moves is recorded as a balanced journal entry
in the correct party's ledger.

**Stack:** React 19 + TypeScript + Vite + Tailwind v4 · Spring Boot 3.5 (Java 21) ·
PostgreSQL (Neon) · Spring Security with JWT

---

## The idea in one picture

```
Seller raises RFQ ──► Vendor ACCEPTS ──► Vendor DELIVERS ──► Vendor INVOICES
                                                                   │
                              ┌────────────────────────────────────┴───────────────┐
                              ▼                                                    ▼
                    VENDOR'S BOOKS                                        SELLER'S BOOKS
              Dr Accounts Receivable 11,800                    Dr Purchase Expense    10,000
                  Cr Sales Income        10,000                Dr Input CGST + SGST    1,800
                  Cr Output CGST + SGST   1,800                    Cr Accounts Payable 11,800
```

One deal. Two ledgers. Each entry balances **on its own**, and neither references the
other's accounts — the posting engine refuses that outright.

---

## Rules the system actually enforces

Enforced in code, server-side. Not guidelines.

1. **Every journal entry balances.** `JournalPostingService` is the only class permitted
   to write to `journal_entry` / `journal_line`. It refuses anything where
   `SUM(debit) ≠ SUM(credit)`, checked on the assembled entity and not merely on the
   draft, and runs with `Propagation.MANDATORY` so a bad entry rolls the originating
   document back with it.

2. **Books never mix.** An entry may not reference an account or a journal belonging to
   another book. Without this, a mirrored deal could debit the seller's cash and credit
   the buyer's payable — arithmetically balanced, meaningless in either set of books.

3. **Reports are computed from the ledger at request time.** No summary tables, no
   cached totals, no denormalised balances anywhere in the schema.

4. **Amounts are never trusted from the client.** Requests carry quantity, unit price and
   tax rate only. Every total and posted amount is computed server-side.

5. **Access control lives at the service layer**, not just on routes.

6. **Scope comes from the session, never a request parameter.** A user cannot widen their
   own scope by editing a payload. Portal endpoints additionally *fail closed*: they
   throw for a non-customer rather than returning an unrestricted result.

7. **The ledger is append-only.** No edit or delete path. Corrections are reversing
   entries; a posted deal cannot be cancelled.

---

## Domain model

| Concept | Meaning |
|---|---|
| **Party** | Any tradeable identity — SELLER, VENDOR or CUSTOMER. One row per real organisation, shared across every book that deals with them. |
| **Book** | One party's complete set of accounts. Created **only** for Sellers and Vendors. |
| **Contact** | How one book sees a counterparty. Points at a global Party. |
| **Deal** | The shared trade, sitting above both books. |
| **Document** | One book's paperwork for a deal — two for a mirrored trade, one for a customer sale. |
| **Settlement** | The shared money movement, drawn into each book as its own Payment. |

**Two independent dimensions of identity:**
- **Party type** (SELLER / VENDOR / CUSTOMER) — what you *are* in a trade
- **Access level** (ADMIN / ACCOUNTANT / USER) — what you may *do* inside your own book

So a vendor company has its own admin and its own accountant. Access level never grants
access to another book.

| Access level | Can do |
|---|---|
| **ADMIN** | Everything within their own book, including users and chart of accounts |
| **ACCOUNTANT** | Master data, transactions, reports. Not users, not the chart of accounts |
| **USER** | Portal only — their own invoices, paid/unpaid, and paying them |

---

## Deal lifecycle

```
RFQ_DRAFT → RFQ_SENT → ACCEPTED → DELIVERED → INVOICED → PARTIALLY_PAID → PAID
                ↓
            REJECTED
```
`CANCELLED` is reachable from any state **before** `INVOICED`.

| Stage | Ledger effect | Who may act |
|---|---|---|
| Raise / send RFQ | none | The buyer |
| Accept / Reject | none | **Only the counterparty** — you cannot accept your own request |
| Mark delivered | none | **Only the supplying side** |
| **Invoice** | **posts to both books** | Only the supplier, and only after delivery |
| Settle | posts to both books | Either side; cannot exceed the outstanding amount |

**Nothing reaches the ledger until the invoice is posted.** An order is a commitment,
not a transaction.

### GST

The buyer's state is frozen onto the deal as **place of supply** and compared against
the seller's own state:

| Comparison | Treatment | Accounts |
|---|---|---|
| Same state | Intra-state | CGST + SGST, half the rate each |
| Different state | Inter-state | IGST at the full rate |
| Either unknown | Unspecified | Undifferentiated tax account |

Halves are computed as `cgst = round(tax / 2)` and `sgst = tax − cgst`. Rounding both
independently would put an odd number of paise out and unbalance the entry.

---

## Running it

### Prerequisites
- JDK 21
- Node 20+
- A PostgreSQL database (this repo is pointed at Neon)

### 1. Backend configuration

```bash
cd backend/src/main/resources
cp application-local.yml.example application-local.yml
```

Fill in your database URL, username, password, and a base64 32-byte JWT secret. This
file is gitignored.

### 2. Start the backend

```bash
cd backend
./mvnw spring-boot:run          # Windows: .\mvnw.cmd spring-boot:run
```

Flyway creates the schema on first boot. `http://localhost:8080` ·
Swagger at `/swagger-ui.html`.

### 3. Start the frontend

```bash
cd frontend
npm install
npm run dev
```

`http://localhost:5173`, proxying `/api` to port 8080.

### 4. Load demo data

```bash
pwsh -File scripts/seed-demo.ps1
```

Creates three organisations and two customers and drives nine trades through every
stage — entirely through the public REST API, so every figure is produced by the real
posting engine.

| Login ID | Who | Password |
|---|---|---|
| `urbanfurn` | Urban Furniture — SELLER, admin | `Passw0rd!23` |
| `timbertrd` | Timber Traders — VENDOR, admin | `Passw0rd!23` |
| `mumbaifit` | Mumbai Fittings — VENDOR (Maharashtra, for IGST) | `Passw0rd!23` |
| `skylineco` | Skyline Offices — CUSTOMER portal | `Passw0rd!23` |
| `casaliving` | Casa Living — CUSTOMER portal | `Passw0rd!23` |

You can also register fresh accounts at `/register` and pick any role.

---

## Credential rules

| Field | Rule |
|---|---|
| **Login ID** | Unique, case-insensitive. 6–12 characters, `^[A-Za-z0-9._-]{6,12}$` |
| **Email** | Unique, case-insensitive |
| **Password** | 8+ characters, with at least one lowercase, one uppercase and one special character. Rejected if it appears in a common-password blocklist |

> **One deliberate deviation from the brief.** It asked that passwords be *unique across
> users*. That cannot be built safely: bcrypt salts every hash, so checking uniqueness
> would mean re-hashing the candidate against every stored row — seconds of CPU per
> signup — and a "that password is taken" response tells an attacker that another
> account uses it. A common-password blocklist delivers the intended protection with
> neither problem.

---

## Verification

```bash
cd backend  && ./mvnw test     # 63 unit tests
cd frontend && npm run build   # type-check + production build
```

The suite defends the invariants everything else rests on:

- **`JournalPostingServiceTest`** — balanced entries persist; unbalanced,
  off-by-one-paisa, single-sided and negative-amount entries are rejected and never
  reach the repository. Two tests specifically prove an entry cannot reference another
  book's **account** or **journal**.
- **`CredentialPolicyTest`** — every login-ID and password rule, including the
  blocklist.
- **`TaxCalculatorTest`** — GST components always sum back to the tax they came from,
  across odd paise; a blank place of supply is never guessed.
- **`AgingBucketTest`** — every bucket boundary and one day either side.

Beyond that, `scripts/` and the live flows were exercised end to end against a real
database and driven in a real browser: registration and credential rejection, book
provisioning, all three trade flows, mirrored posting, cross-book 403s, portal
isolation, overpayment rejection, and per-book balance-sheet, trial-balance, aging and
reconciliation integrity.

---

## Project layout

```
backend/src/main/java/com/urbanfurniture/accounting/
  identity/    Party, Book, AppUser, registration, credential policy, auth
  security/    JWT filter, principal, SecurityConfig, CurrentUser (book scope)
  ledger/      Account, Journal, JournalEntry/Line, JournalPostingService  ← the engine
               BookProvisioningService — the single definition of "a new book"
  master/      Contact, Product, chart of accounts, organisation details
  trade/       Deal, Document, Settlement, Payment + the RFQ→invoice→settle workflow
  tax/         TaxCalculator, GstSplit, GstTotals — CGST/SGST/IGST rules
  analytic/    Analytic accounts and budgets
  portal/      Customer self-service, scoped by counterparty
  report/      Balance sheet, P&L, trial balance, aging, reconciliation, budget
  common/      Money, per-book and platform sequences, exceptions
  db/migration/  V1 schema · V2 platform sequences

frontend/src/
  api/         axios client + typed endpoints
  auth/        auth context, route guards, role-based landing
  components/  UI primitives, back-office shell, portal shell
  pages/       back office, reports, portal, error pages
scripts/
  seed-demo.ps1  demo dataset via the REST API
```

### Notable decisions

- **A book is provisioned in application code, not a SQL seed.** Signup and the demo
  script call the same `BookProvisioningService`, so a book created by a user and one
  created by a script are identical. A seeded chart of accounts would be a second,
  drifting definition.
- **Order paperwork is drawn up on acceptance**, in both books at once. Acceptance is
  what turns a proposal into an order.
- **The supplier issues the invoice**, and the buyer's bill is its mirror — that is who
  raises a demand for payment in a real trade.
- **The GST split is stored per line, not derived on read**, so a return reprinted years
  later shows what was actually charged rather than what today's settings imply.
- **Analytic tags live on the document line, not the shared deal line.** Each side
  attributes its own costs; the buyer's cost centre is not the seller's business.
- **Contacts are created on first trade.** Trading with someone new adds them to your
  address book rather than making you key them in twice.
- **The portal is a separate route tree with its own shell**, not the staff app with
  items hidden. A customer is barred from every non-portal API path, so a trimmed-down
  back office would imply pages exist that cannot load.
- **Tax that cannot be attributed falls through to an undifferentiated account**, with
  the residual computed as *document tax minus components recorded*. The entry balances
  by construction rather than by assumption.

---

## Status

Working end to end: registration with role selection, per-party books, the full
RFQ → accept → deliver → invoice → settle workflow with mirroring, GST, customer portal,
analytic accounts and budgets, and seven live reports per book.

Not built: PDF export, bank reconciliation from CSV, inventory quantity tracking, and
the AI summary.
