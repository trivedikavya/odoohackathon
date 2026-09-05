# Urban Furniture — Accounting System

A double-entry bookkeeping application: master data, purchase and sales workflows,
automatic journal entry generation, and financial reports computed live from the ledger.

**Stack:** React 19 + TypeScript + Vite + Tailwind v4 · Spring Boot 3.5 (Java 21) · PostgreSQL (Neon) · Spring Security with JWT

---

## The rules this system actually enforces

These are enforced in code, not just documented:

1. **Every financial transaction posts a balanced journal entry.**
   `JournalPostingService` is the only class in the system permitted to write to
   `journal_entry` / `journal_line`. It refuses to persist anything where
   `SUM(debit) != SUM(credit)`, and it runs inside the caller's transaction
   (`Propagation.MANDATORY`), so a bad entry rolls the originating document back
   with it. The ledger and the documents can never disagree.

2. **Reports are computed from the ledger, never from a summary table.**
   There is no summary/cache table anywhere in the schema. The Balance Sheet and
   P&L aggregate `journal_line` at request time, so they cannot drift.

3. **Amounts are never trusted from the client.**
   Requests carry quantity, unit price and tax rate only. Line totals, document
   totals and every posted amount are computed server-side.

4. **Admin-only actions are blocked on the server.**
   `@PreAuthorize` sits on the service methods, not just the routes, so bypassing
   the frontend achieves nothing. Verified by automated tests.

5. **Row-level access control is built into the queries.**
   Every transactional repository query takes a `contactScope` parameter that is
   read from the JWT principal — never from a request parameter. It is `null` for
   staff and pinned to the bound contact for portal users. (The `CONTACT` role
   itself lands in Phase 2; the enforcement mechanism is already in place.)

### Double-entry mapping

| Event | Debit | Credit |
|---|---|---|
| Customer invoice posted | Debtors (AR) — total | Sales Income — untaxed<br>Tax Payable — tax |
| Payment received | Cash / Bank | Debtors (AR) |
| Vendor bill posted | Purchase Expense — untaxed<br>Input Tax Credit — tax | Creditors (AP) — total |
| Payment made | Creditors (AP) | Cash / Bank |
| Owner capital contribution | Cash / Bank | Owner's Capital |

---

## Running it

### Prerequisites

- **JDK 21** (`winget install Microsoft.OpenJDK.21`)
- **Node.js 20+**
- A **PostgreSQL** database (this project targets [Neon](https://neon.tech))

Maven is not required — the repo ships the Maven wrapper (`mvnw`).

### 1. Backend configuration

Secrets are never committed. Copy the example file and fill it in:

```bash
cd backend/src/main/resources
cp application-local.yml.example application-local.yml
```

Then set your values in `application-local.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://<neon-host>/<db>?sslmode=require
    username: <user>
    password: <password>

app:
  jwt:
    # Generate: node -e "console.log(require('crypto').randomBytes(32).toString('base64'))"
    secret: <base64-encoded 32-byte secret>
```

`application-local.yml` is gitignored. The app refuses to start without a JWT
secret rather than falling back to an insecure default.

### 2. Start the backend

```bash
cd backend
./mvnw spring-boot:run          # macOS / Linux
.\mvnw.cmd spring-boot:run      # Windows
```

Flyway creates the schema and seeds the chart of accounts, journals, document
sequences and demo logins on first run. Hibernate is set to `validate` — Flyway
owns the schema and Hibernate may never modify it.

API: <http://localhost:8080> · Swagger UI: <http://localhost:8080/swagger-ui.html>

### 3. Start the frontend

```bash
cd frontend
npm install
npm run dev
```

App: <http://localhost:5173> (Vite proxies `/api` to port 8080, so there is no CORS setup in dev.)

### 4. Optional — load demo data

Populates a realistic business (5 contacts, 5 products, opening capital, 3
purchase flows and 4 sales flows in various payment states) entirely through the
public REST API, so every figure is produced by the real posting engine:

```bash
pwsh -File scripts/seed-demo.ps1
```

### Demo logins

| Role | Email | Password |
|---|---|---|
| Admin (business owner) | `admin@urbanfurniture.test` | `Admin@123` |
| Accountant (invoicing user) | `accountant@urbanfurniture.test` | `Accountant@123` |

> Seeded for the demo. Remove or change these before any real deployment.

---

## Demo walkthrough

A three-minute path that shows the accounting actually working:

1. **Sign in** as the admin.
2. **Dashboard** — KPIs and the balance check, all computed from the ledger.
3. **Contacts / Products** — create a vendor, a customer and a product.
4. **Purchases** — New Purchase Order → **Confirm** → **Create Bill** → **Post**.
   Posting is the moment it hits the ledger. Note the journal entry number appear
   on the row.
5. **Sales** — New Sales Order → **Confirm** → **Create Invoice** → **Post** →
   **Pay** (try a partial payment first; status becomes `Partially Paid`, then
   `Paid`).
6. **Click the eye icon** on any posted document — it shows the document lines
   *and* the exact journal entry they generated, side by side.
7. **Ledger** — every entry, every debit and credit, each marked balanced.
8. **Balance Sheet** — Assets = Liabilities + Equity, with the difference shown
   explicitly rather than hidden.
9. **Role check** — sign in as the accountant: the archive and user-management
   actions disappear, and the API rejects them with `403` even if called directly.

---

## Verification

```bash
cd backend && ./mvnw test     # 15 unit tests
cd frontend && npm run build  # type-check + production build
```

The test suite focuses on the invariant everything else depends on
(`JournalPostingServiceTest`): balanced entries persist, unbalanced entries are
rejected and never reach the repository, off-by-one-paisa entries are rejected,
single-sided entries are rejected, and zero-amount lines are dropped rather than
written.

Beyond that, the flows were exercised end-to-end against the live database and
the UI was driven in a real browser — full purchase and sales cycles, partial and
full payments, overpayment rejection, double-post rejection, and role
enforcement for admin, accountant, anonymous and forged-token callers.

---

## Project layout

```
backend/
  src/main/java/com/urbanfurniture/accounting/
    auth/          login, JWT issuing, user administration
    security/      JWT filter, principal, SecurityConfig, CurrentUser (row-level scope)
    common/        Money, audit base entity, exceptions, document numbering
    master/        contact · product · account (chart of accounts) · journal
    ledger/        JournalEntry/Line, JournalPostingService  ← the double-entry engine
    transaction/   sales · purchase · payment · capital
    report/        Balance Sheet, P&L, dashboard (all ledger-derived)
  src/main/resources/db/migration/   Flyway V1 schema, V2 seed
frontend/
  src/api/         axios client + typed endpoints
  src/auth/        auth context and route guards
  src/components/  UI primitives and app shell
  src/pages/       one page per module; sales & purchase share one parameterised workflow
scripts/
  seed-demo.ps1    realistic demo dataset via the REST API
```

### Notable design decisions

- **Orders have no accounting effect.** A sales/purchase order is a commercial
  document. Nothing reaches the ledger until the resulting invoice/bill is
  explicitly **posted**, which is also what makes the demo legible.
- **Ledger entries are immutable.** There is no edit or delete path. Posted
  documents cannot be cancelled; corrections belong in a reversing entry.
- **System accounts are resolved by a stable `system_code`**, not by name, so
  renaming an account in the UI can never silently break the posting rules.
  They also cannot be archived.
- **Document numbers are allocated under a row lock** (`doc_sequence`) inside the
  caller's transaction, so concurrent requests can't collide and abandoned
  attempts don't leave gaps.
- **Sales and purchase share one React workflow component** parameterised by a
  config object — the two flows are mirror images, so they are not duplicated.

---

## Status

**Phase 1 is complete**: auth with two roles, contact/product masters, seeded
chart of accounts and journals, both transaction flows end to end, automatic
balanced journal entries, live Balance Sheet and P&L, dashboard, and server-side
role enforcement.

Phase 2 (not yet started): `CONTACT` self-service portal, analytic accounts and
budgets, GST CGST/SGST/IGST engine, AR/AP aging, PDF invoices, audit log viewer,
bank reconciliation, dashboard charts, and stock reporting.
