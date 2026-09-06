/**
 * Demo dataset — roughly 200 deals across eight back-dated months.
 *
 * Everything goes through the public REST API. Nothing is written to the
 * database directly, so every figure afterwards was produced by the real
 * posting engine, the real stock ledger and the real tax rules. That is
 * what makes the integrity checks meaningful: if the balance sheet
 * balances and stock ties to the Inventory account after this runs, it is
 * because the engine held them together across two hundred transactions.
 *
 *   node scripts/seed-demo.mjs
 *
 * Deal chains are independent, so they run concurrently. Stock issuing
 * takes a row lock server-side, so concurrent sales of the same item
 * queue rather than oversell.
 */

const BASE = process.env.API_BASE ?? 'http://localhost:8080/api'
const PASSWORD = 'Passw0rd!23'
const CONCURRENCY = Number(process.env.SEED_CONCURRENCY ?? 5)

// Fixed seed, so the same dataset comes out every run and the numbers in
// the README stay true.
let seed = 20260906
const rnd = () => {
  seed = (seed * 1103515245 + 12345) & 0x7fffffff
  return seed / 0x7fffffff
}
const pick = (a) => a[Math.floor(rnd() * a.length)]
const between = (lo, hi) => lo + Math.floor(rnd() * (hi - lo))
const day = (ago) => {
  const d = new Date()
  d.setDate(d.getDate() - ago)
  return d.toISOString().slice(0, 10)
}

async function call(path, { method = 'GET', token, body } = {}) {
  const res = await fetch(BASE + path, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  if (!res.ok) {
    const text = await res.text()
    const err = new Error(`${res.status} ${path} :: ${text.slice(0, 200)}`)
    err.status = res.status
    throw err
  }
  return res.status === 204 ? null : res.json()
}

/** Registers, or signs in if the account already exists, so re-runs work. */
async function signup(loginId, email, person, org, partyType, state, city) {
  try {
    return await call('/auth/register', {
      method: 'POST',
      body: { loginId, email, password: PASSWORD, fullName: person,
              partyType, organisationName: org, state, city },
    })
  } catch {
    return call('/auth/login', {
      method: 'POST',
      body: { identifier: loginId, password: PASSWORD },
    })
  }
}

/** Runs tasks with a bounded number in flight. */
async function pool(items, worker, limit = CONCURRENCY) {
  let index = 0
  let done = 0
  let failed = 0
  const runners = Array.from({ length: Math.min(limit, items.length) }, async () => {
    while (index < items.length) {
      const mine = index++
      try {
        await worker(items[mine], mine)
      } catch (e) {
        // A deal that cannot complete (usually stock ran out) is skipped
        // rather than aborting the seed, but the first reason is shown so
        // a systematic failure is not mistaken for scarcity.
        failed++
        if (failed === 1) console.log(`    first failure: ${e.message}`)
      }
      done++
      if (done % 20 === 0) process.stdout.write(`    ${done}/${items.length}\n`)
    }
  })
  await Promise.all(runners)
  return { done, failed }
}

const log = (m) => console.log(m)

// ---------------------------------------------------------------------

log('Registering organisations…')
const uf = await signup('urbanfurn', 'owner@urban.test', 'Anita Rao', 'Urban Furniture', 'SELLER', 'Gujarat', 'Ahmedabad')
const loft = await signup('loftliving', 'owner@loft.test', 'Kabir Shah', 'Loft Living', 'SELLER', 'Maharashtra', 'Pune')
const timber = await signup('timbertrd', 'sales@timber.test', 'Ravi Mehta', 'Timber Traders', 'VENDOR', 'Gujarat', 'Surat')
const fittings = await signup('mumbaifit', 'sales@mfit.test', 'Priya Nair', 'Mumbai Fittings', 'VENDOR', 'Maharashtra', 'Mumbai')
const weave = await signup('weavehouse', 'sales@weave.test', 'Iqbal Khan', 'Weave House', 'VENDOR', 'Gujarat', 'Rajkot')

const customers = [
  await signup('skylineco', 'ap@skyline.test', 'Dev Sharma', 'Skyline Offices', 'CUSTOMER', 'Gujarat', 'Ahmedabad'),
  await signup('casaliving', 'ap@casa.test', 'Neha Kapoor', 'Casa Living', 'CUSTOMER', 'Rajasthan', 'Jaipur'),
  await signup('novaworks', 'ap@nova.test', 'Arjun Patel', 'Nova Workspaces', 'CUSTOMER', 'Gujarat', 'Vadodara'),
  await signup('harborcafe', 'ap@harbor.test', 'Sara Dsouza', 'Harbor Cafe', 'CUSTOMER', 'Maharashtra', 'Mumbai'),
  await signup('peakhotels', 'ap@peak.test', 'Rohit Verma', 'Peak Hotels', 'CUSTOMER', 'Karnataka', 'Bengaluru'),
  await signup('greenschool', 'ap@green.test', 'Maya Iyer', 'Green Valley School', 'CUSTOMER', 'Gujarat', 'Surat'),
]

const T = (r) => r.token

log('Building catalogues…')
async function ensureProducts(token, items) {
  const existing = await call('/products?includeArchived=true', { token })
  const out = {}
  for (const item of items) {
    const found = existing.find((p) => p.name === item.name)
    out[item.name] = found ?? (await call('/products', { method: 'POST', token, body: item }))
  }
  return out
}

const timberItems = await ensureProducts(T(timber), [
  { name: 'Teak Plank 8ft', type: 'GOODS', salesPrice: '1850.00', cost: '0', taxRate: '18.00', hsnCode: '4407', category: 'Timber' },
  { name: 'Oak Board 6ft', type: 'GOODS', salesPrice: '1450.00', cost: '0', taxRate: '18.00', hsnCode: '4407', category: 'Timber' },
  { name: 'Plywood Sheet', type: 'GOODS', salesPrice: '980.00', cost: '0', taxRate: '18.00', hsnCode: '4412', category: 'Boards' },
  { name: 'Walnut Veneer', type: 'GOODS', salesPrice: '2200.00', cost: '0', taxRate: '18.00', hsnCode: '4408', category: 'Veneer' },
])
const fitItems = await ensureProducts(T(fittings), [
  { name: 'Drawer Runner Set', type: 'GOODS', salesPrice: '340.00', cost: '0', taxRate: '18.00', hsnCode: '8302', category: 'Hardware' },
  { name: 'Chair Castor Set', type: 'GOODS', salesPrice: '260.00', cost: '0', taxRate: '18.00', hsnCode: '8302', category: 'Hardware' },
  { name: 'Brass Handle', type: 'GOODS', salesPrice: '180.00', cost: '0', taxRate: '18.00', hsnCode: '8302', category: 'Hardware' },
  { name: 'Gas Lift Cylinder', type: 'GOODS', salesPrice: '620.00', cost: '0', taxRate: '18.00', hsnCode: '9401', category: 'Hardware' },
])
const weaveItems = await ensureProducts(T(weave), [
  { name: 'Upholstery Fabric', type: 'GOODS', salesPrice: '450.00', cost: '0', taxRate: '12.00', hsnCode: '5407', category: 'Fabric' },
  { name: 'Foam Cushion', type: 'GOODS', salesPrice: '380.00', cost: '0', taxRate: '18.00', hsnCode: '9404', category: 'Fabric' },
])
const ufItems = await ensureProducts(T(uf), [
  { name: 'Oak Dining Table', type: 'GOODS', salesPrice: '26000.00', cost: '0', taxRate: '18.00', hsnCode: '9403', category: 'Tables' },
  { name: 'Walnut Office Desk', type: 'GOODS', salesPrice: '19500.00', cost: '0', taxRate: '18.00', hsnCode: '9403', category: 'Tables' },
  { name: 'Ergonomic Chair', type: 'GOODS', salesPrice: '9800.00', cost: '0', taxRate: '18.00', hsnCode: '9401', category: 'Seating' },
  { name: 'Teak Bookshelf', type: 'GOODS', salesPrice: '14500.00', cost: '0', taxRate: '18.00', hsnCode: '9403', category: 'Storage' },
  { name: 'Lounge Sofa', type: 'GOODS', salesPrice: '42000.00', cost: '0', taxRate: '18.00', hsnCode: '9401', category: 'Seating' },
  { name: 'Filing Cabinet', type: 'GOODS', salesPrice: '11200.00', cost: '0', taxRate: '18.00', hsnCode: '9403', category: 'Storage' },
  { name: 'Design Consultation', type: 'SERVICE', salesPrice: '12000.00', cost: '0', taxRate: '18.00', category: 'Services' },
  { name: 'Assembly & Fitting', type: 'SERVICE', salesPrice: '3500.00', cost: '0', taxRate: '18.00', category: 'Services' },
])
const loftItems = await ensureProducts(T(loft), [
  { name: 'Studio Desk', type: 'GOODS', salesPrice: '16500.00', cost: '0', taxRate: '18.00', hsnCode: '9403', category: 'Tables' },
  { name: 'Accent Armchair', type: 'GOODS', salesPrice: '21000.00', cost: '0', taxRate: '18.00', hsnCode: '9401', category: 'Seating' },
])

// Bundles: one line to sell, but their parts come off the shelf.
const ufExisting = await call('/products', { token: T(uf) })
if (!ufExisting.find((p) => p.name === 'Home Office Set')) {
  await call('/products', { method: 'POST', token: T(uf), body: {
    name: 'Home Office Set', type: 'COMBO', salesPrice: '27500.00', taxRate: '18.00',
    category: 'Bundles', hsnCode: '9403',
    components: [
      { componentProductId: ufItems['Walnut Office Desk'].id, quantity: '1.000' },
      { componentProductId: ufItems['Ergonomic Chair'].id, quantity: '1.000' },
    ] } })
}
if (!ufExisting.find((p) => p.name === 'Dining Set for Six')) {
  await call('/products', { method: 'POST', token: T(uf), body: {
    name: 'Dining Set for Six', type: 'COMBO', salesPrice: '68000.00', taxRate: '18.00',
    category: 'Bundles', hsnCode: '9403',
    components: [
      { componentProductId: ufItems['Oak Dining Table'].id, quantity: '1.000' },
      { componentProductId: ufItems['Ergonomic Chair'].id, quantity: '6.000' },
    ] } })
}
const ufAll = await call('/products', { token: T(uf) })
const homeSet = ufAll.find((p) => p.name === 'Home Office Set')
const diningSet = ufAll.find((p) => p.name === 'Dining Set for Six')

log('Opening capital…')
for (const [token, amount] of [
  [T(uf), '2500000.00'], [T(loft), '900000.00'],
  [T(timber), '900000.00'], [T(fittings), '600000.00'], [T(weave), '450000.00'],
]) {
  try {
    await call('/operations/capital', { method: 'POST', token, body: {
      method: 'BANK', date: day(250), amount, note: 'Owner capital introduced' } })
  } catch { /* already seeded */ }
}

log('Opening stock…')
async function openingStock(token, items, spec) {
  for (const [name, { qty, cost }] of Object.entries(spec)) {
    try {
      await call('/operations/opening-stock', { method: 'POST', token, body: {
        productId: items[name].id, date: day(245), quantity: qty, unitCost: cost } })
    } catch { /* already seeded */ }
  }
}
await openingStock(T(timber), timberItems, {
  'Teak Plank 8ft': { qty: '4000.000', cost: '1250.00' },
  'Oak Board 6ft': { qty: '3500.000', cost: '980.00' },
  'Plywood Sheet': { qty: '5000.000', cost: '640.00' },
  'Walnut Veneer': { qty: '2000.000', cost: '1500.00' },
})
await openingStock(T(fittings), fitItems, {
  'Drawer Runner Set': { qty: '6000.000', cost: '215.00' },
  'Chair Castor Set': { qty: '7000.000', cost: '160.00' },
  'Brass Handle': { qty: '9000.000', cost: '110.00' },
  'Gas Lift Cylinder': { qty: '4000.000', cost: '400.00' },
})
await openingStock(T(weave), weaveItems, {
  'Upholstery Fabric': { qty: '8000.000', cost: '280.00' },
  'Foam Cushion': { qty: '6000.000', cost: '235.00' },
})
// Sellers are going concerns: they start with a floor full of finished
// goods, priced at what they cost to make.
await openingStock(T(uf), ufItems, {
  'Oak Dining Table': { qty: '260.000', cost: '15800.00' },
  'Walnut Office Desk': { qty: '400.000', cost: '11600.00' },
  'Ergonomic Chair': { qty: '1400.000', cost: '5900.00' },
  'Teak Bookshelf': { qty: '300.000', cost: '8700.00' },
  'Lounge Sofa': { qty: '150.000', cost: '25500.00' },
  'Filing Cabinet': { qty: '320.000', cost: '6700.00' },
})
await openingStock(T(loft), loftItems, {
  'Studio Desk': { qty: '180.000', cost: '10200.00' },
  'Accent Armchair': { qty: '120.000', cost: '13400.00' },
})

log('Projects and budgets…')
for (const a of [
  { code: 'SHOWROOM', name: 'Ahmedabad Showroom', type: 'DEPARTMENT' },
  { code: 'ONLINE', name: 'Online Channel', type: 'DEPARTMENT' },
  { code: 'PRJ-VILLA', name: 'Villa Fit-out', type: 'PROJECT' },
  { code: 'PRJ-HOTEL', name: 'Peak Hotels Rollout', type: 'PROJECT' },
]) {
  try { await call('/analytic-accounts', { method: 'POST', token: T(uf), body: a }) } catch {}
}
const projects = await call('/analytic-accounts', { token: T(uf) })
const existingBudgets = await call('/budgets', { token: T(uf) })
for (const b of [
  { code: 'PRJ-VILLA', name: 'Villa fit-out materials', amount: '250000.00', who: 'Anita Rao' },
  { code: 'PRJ-HOTEL', name: 'Hotel rollout materials', amount: '400000.00', who: 'Kabir Shah' },
  { code: 'SHOWROOM', name: 'Showroom running costs', amount: '1800000.00', who: 'Anita Rao' },
]) {
  if (existingBudgets.find((x) => x.name === b.name)) continue
  const acct = projects.find((p) => p.code === b.code)
  if (!acct) continue
  try {
    await call('/budgets', { method: 'POST', token: T(uf), body: {
      name: b.name, analyticAccountId: acct.id,
      periodStart: day(240), periodEnd: day(-120),
      plannedAmount: b.amount, responsible: b.who } })
  } catch {}
}

// ---------------------------------------------------------------------
// The deals.
//
// Registered counterparties go through request -> accept -> deliver ->
// invoice -> settle, because that is the path that writes both sides.
// ---------------------------------------------------------------------
async function runDeal({ buyerToken, sellerToken, sellerPartyId, lines, stopAt, daysAgo, portal }) {
  const root = portal ? '/portal/my-orders' : '/deals'
  const deal = await call(root, { method: 'POST', token: buyerToken, body: {
    sellerPartyId, dealDate: day(daysAgo), lines } })
  if (stopAt === 'DRAFT') return

  await call(`${root}/${deal.id}/send`, { method: 'POST', token: buyerToken, body: {} })
  if (stopAt === 'SENT') return

  if (stopAt === 'REJECTED') {
    await call(`/deals/${deal.id}/reject`, { method: 'POST', token: sellerToken,
      body: { reason: 'Cannot meet that lead time' } })
    return
  }

  await call(`/deals/${deal.id}/accept`, { method: 'POST', token: sellerToken, body: {} })
  if (stopAt === 'ACCEPTED') return

  await call(`/deals/${deal.id}/deliver`, { method: 'POST', token: sellerToken,
    body: { deliveredAt: day(Math.max(0, daysAgo - 3)) } })
  if (stopAt === 'DELIVERED') return

  const invoiced = await call(`/deals/${deal.id}/invoice`, { method: 'POST', token: sellerToken,
    body: { docDate: day(Math.max(0, daysAgo - 4)), dueDate: day(daysAgo - 34) } })
  if (stopAt === 'INVOICED') return

  const amount = stopAt === 'PARTIAL'
    ? (Number(invoiced.totalAmount) * 0.4).toFixed(2)
    : invoiced.totalAmount
  await call(`/deals/${deal.id}/settle`, { method: 'POST', token: sellerToken, body: {
    method: pick(['BANK', 'BANK', 'BANK', 'CASH']),
    settlementDate: day(Math.max(0, daysAgo - 12)), amount } })
}

// Weighted so every screen has something and aging fills all five buckets.
const STATES = [
  ...Array(9).fill('PAID'), ...Array(4).fill('PARTIAL'), ...Array(3).fill('INVOICED'),
  'DELIVERED', 'DELIVERED', 'ACCEPTED', 'SENT', 'REJECTED',
]

const purchaseSets = [
  { token: T(timber), party: timber.user.partyId, items: timberItems, names: Object.keys(timberItems) },
  { token: T(fittings), party: fittings.user.partyId, items: fitItems, names: Object.keys(fitItems) },
  { token: T(weave), party: weave.user.partyId, items: weaveItems, names: Object.keys(weaveItems) },
]

log(`Purchases from vendors (concurrency ${CONCURRENCY})…`)
const purchases = Array.from({ length: 62 }, () => {
  const set = pick(purchaseSets)
  const buyer = rnd() < 0.8 ? T(uf) : T(loft)
  const lines = Array.from({ length: between(1, 3) }, () => ({
    productId: set.items[pick(set.names)].id, quantity: `${between(20, 120)}.000`,
  }))
  return { buyerToken: buyer, sellerToken: set.token, sellerPartyId: set.party,
           lines, stopAt: pick(STATES), daysAgo: between(10, 235) }
})
log(`  ${JSON.stringify(await pool(purchases, runDeal))}`)

log('Sales to customers…')
const sellable = ['Oak Dining Table', 'Walnut Office Desk', 'Ergonomic Chair', 'Teak Bookshelf',
                  'Lounge Sofa', 'Filing Cabinet', 'Design Consultation', 'Assembly & Fitting']
const sales = Array.from({ length: 105 }, () => {
  const cust = pick(customers)
  const lines = Array.from({ length: between(1, 4) }, () => {
    // About one line in six is a bundle, so combos show up regularly.
    if (rnd() < 0.17) {
      return { productId: pick([homeSet, diningSet]).id, quantity: `${between(1, 3)}.000` }
    }
    return { productId: ufItems[pick(sellable)].id, quantity: `${between(1, 5)}.000` }
  })
  return { buyerToken: T(cust), sellerToken: T(uf), sellerPartyId: uf.user.partyId,
           lines, stopAt: pick(STATES), daysAgo: between(5, 230), portal: true }
})
log(`  ${JSON.stringify(await pool(sales, runDeal))}`)

log('Customers buying direct from a vendor…')
const directSets = [
  { token: T(fittings), party: fittings.user.partyId, items: fitItems, names: ['Brass Handle', 'Chair Castor Set'] },
  { token: T(weave), party: weave.user.partyId, items: weaveItems, names: ['Upholstery Fabric', 'Foam Cushion'] },
]
const vendorSales = Array.from({ length: 20 }, () => {
  const cust = pick(customers)
  const set = pick(directSets)
  return { buyerToken: T(cust), sellerToken: set.token, sellerPartyId: set.party,
           lines: [{ productId: set.items[pick(set.names)].id, quantity: `${between(10, 60)}.000` }],
           stopAt: pick(STATES), daysAgo: between(5, 220), portal: true }
})
log(`  ${JSON.stringify(await pool(vendorSales, runDeal))}`)

log('Seller buying from seller…')
const sellerToSeller = Array.from({ length: 10 }, () => ({
  buyerToken: T(loft), sellerToken: T(uf), sellerPartyId: uf.user.partyId,
  lines: [{ productId: ufItems[pick(['Ergonomic Chair', 'Filing Cabinet', 'Teak Bookshelf'])].id,
            quantity: `${between(3, 12)}.000` }],
  stopAt: pick(STATES), daysAgo: between(10, 200),
}))
log(`  ${JSON.stringify(await pool(sellerToSeller, runDeal))}`)

// ---------------------------------------------------------------------
// Counter sales — what direct entry is actually for.
// ---------------------------------------------------------------------
log('Walk-in counter sales…')
const ufContacts = await call('/contacts?includeArchived=true', { token: T(uf) })
let walkIn = ufContacts.find((c) => c.name === 'Walk-in Customers')
if (!walkIn) {
  walkIn = await call('/contacts', { method: 'POST', token: T(uf), body: {
    name: 'Walk-in Customers', type: 'CUSTOMER', relationship: 'CUSTOMER',
    state: 'Gujarat', city: 'Ahmedabad' } })
}
const counterSales = Array.from({ length: 16 }, () => ({
  productId: ufItems[pick(['Ergonomic Chair', 'Filing Cabinet', 'Teak Bookshelf', 'Walnut Office Desk'])].id,
  quantity: `${between(1, 4)}.000`,
  daysAgo: between(5, 180),
}))
log(`  ${JSON.stringify(await pool(counterSales, async (s) =>
  call('/operations/direct-sale', { method: 'POST', token: T(uf), body: {
    counterpartyPartyId: walkIn.partyId, date: day(s.daysAgo),
    lines: [{ productId: s.productId, quantity: s.quantity }] } })))}`)

// ---------------------------------------------------------------------
// Overheads. Without them the P&L is cost of sales and nothing else,
// which is not what running a business looks like.
// ---------------------------------------------------------------------
log('Operating expenses…')
const accounts = await call('/accounts', { token: T(uf) })
const opex = accounts.find((a) => a.code === '5200')
const showroom = projects.find((p) => p.code === 'SHOWROOM')

const expenses = []
for (let m = 1; m <= 8; m++) {
  for (const e of [
    { desc: 'Showroom rent', amt: '45000.00', tag: true },
    { desc: 'Salaries', amt: '128000.00', tag: false },
    { desc: 'Electricity and utilities', amt: '14500.00', tag: true },
    { desc: 'Delivery and freight', amt: '22000.00', tag: false },
  ]) {
    expenses.push({ ...e, date: day(m * 28) })
  }
}
if (opex) {
  log(`  ${JSON.stringify(await pool(expenses, async (e) =>
    call('/operations/expenses', { method: 'POST', token: T(uf), body: {
      expenseAccountId: opex.id, method: 'BANK', date: e.date, amount: e.amt,
      description: e.desc,
      ...(e.tag && showroom ? { analyticAccountId: showroom.id } : {}) } })))}`)
}

log('\nDone. Sign in with any of these (password: Passw0rd!23)\n')
for (const [id, who] of [
  ['urbanfurn', 'Urban Furniture — SELLER (Gujarat). The main demo account.'],
  ['loftliving', 'Loft Living — SELLER (Maharashtra). Buys from Urban Furniture.'],
  ['timbertrd', 'Timber Traders — VENDOR (Gujarat). Intra-state, so CGST+SGST.'],
  ['mumbaifit', 'Mumbai Fittings — VENDOR (Maharashtra). Inter-state, so IGST.'],
  ['weavehouse', 'Weave House — VENDOR (Gujarat).'],
  ['skylineco', 'Skyline Offices — CUSTOMER portal.'],
  ['casaliving', 'Casa Living — CUSTOMER portal (Rajasthan, so IGST).'],
  ['novaworks', 'Nova Workspaces — CUSTOMER portal.'],
  ['harborcafe', 'Harbor Cafe — CUSTOMER portal.'],
  ['peakhotels', 'Peak Hotels — CUSTOMER portal.'],
  ['greenschool', 'Green Valley School — CUSTOMER portal.'],
]) {
  console.log(`  ${id.padEnd(12)} ${who}`)
}
