<#
  Seeds a realistic demo dataset for Urban Furniture.

  Requires the backend to be running on http://localhost:8080 with the Flyway
  migrations applied (chart of accounts, journals and demo logins).

  Everything below goes through the public REST API, so every figure you see in
  the reports was produced by the same double-entry engine a user would drive
  from the UI - nothing is inserted directly into the database.

  Usage:  pwsh -File scripts/seed-demo.ps1 [-BaseUrl http://localhost:8080]
#>
param(
    [string]$BaseUrl = 'http://localhost:8080',
    [string]$Email = 'admin@urbanfurniture.test',
    [string]$Password = 'Admin@123'
)

$ErrorActionPreference = 'Stop'

function Post($path, $body) {
    Invoke-RestMethod -Uri "$BaseUrl$path" -Method Post -Headers $script:H `
        -ContentType 'application/json' -Body ($body | ConvertTo-Json -Depth 6)
}

Write-Host 'Signing in...' -ForegroundColor Cyan
$login = Invoke-RestMethod -Uri "$BaseUrl/api/auth/login" -Method Post -ContentType 'application/json' `
    -Body (@{ email = $Email; password = $Password } | ConvertTo-Json)
$script:H = @{ Authorization = "Bearer $($login.token)" }

# ---------------------------------------------------------------- contacts
Write-Host 'Creating contacts...' -ForegroundColor Cyan
$vendors = @(
    @{ name = 'Teak Timber Supplies'; type = 'VENDOR'; email = 'sales@teaktimber.test'; mobile = '+91 79000 11111'; city = 'Ahmedabad'; state = 'Gujarat'; gstin = '24AAACT1111A1Z1' },
    @{ name = 'Nova Foam & Fabric';   type = 'VENDOR'; email = 'orders@novafoam.test';  mobile = '+91 22000 22222'; city = 'Mumbai';    state = 'Maharashtra'; gstin = '27AAACN2222B1Z2' }
) | ForEach-Object { Post '/api/contacts' $_ }

$customers = @(
    @{ name = 'Nirvana Interiors';   type = 'CUSTOMER'; email = 'buy@nirvana.test';    mobile = '+91 26100 33333'; city = 'Surat';     state = 'Gujarat' },
    @{ name = 'Skyline Offices Ltd'; type = 'CUSTOMER'; email = 'admin@skyline.test';  mobile = '+91 80000 44444'; city = 'Bengaluru'; state = 'Karnataka' },
    @{ name = 'Casa Living Studio';  type = 'BOTH';     email = 'hello@casaliving.test'; mobile = '+91 11000 55555'; city = 'Delhi';   state = 'Delhi' }
) | ForEach-Object { Post '/api/contacts' $_ }

# ---------------------------------------------------------------- products
Write-Host 'Creating products...' -ForegroundColor Cyan
$products = @(
    @{ name = 'Oak Dining Table';      type = 'GOODS';   salesPrice = 25000; cost = 15000; category = 'Tables';    hsnCode = '9403' },
    @{ name = 'Walnut Office Desk';    type = 'GOODS';   salesPrice = 18500; cost = 11000; category = 'Desks';     hsnCode = '9403' },
    @{ name = 'Ergonomic Task Chair';  type = 'GOODS';   salesPrice = 9500;  cost = 5800;  category = 'Seating';   hsnCode = '9401' },
    @{ name = 'Teak Bookshelf';        type = 'GOODS';   salesPrice = 14000; cost = 8200;  category = 'Storage';   hsnCode = '9403' },
    @{ name = 'Interior Design Consultation'; type = 'SERVICE'; salesPrice = 12000; cost = 0; category = 'Services' }
) | ForEach-Object { Post '/api/products' $_ }

# ------------------------------------------------------- opening capital
Write-Host 'Recording opening capital...' -ForegroundColor Cyan
Post '/api/capital/contributions' @{ method = 'BANK'; date = '2026-06-01'; amount = 500000; note = 'Opening capital introduced by owner' } | Out-Null
Post '/api/capital/contributions' @{ method = 'CASH'; date = '2026-06-01'; amount = 50000;  note = 'Opening cash float' } | Out-Null

# ---------------------------------------------------------------- purchases
Write-Host 'Running purchase flows...' -ForegroundColor Cyan

function Invoke-PurchaseFlow($vendorId, $orderDate, $billDate, $lines, $payment) {
    $po = Post '/api/purchase-orders' @{ contactId = $vendorId; orderDate = $orderDate; lines = $lines }
    Post "/api/purchase-orders/$($po.id)/confirm" @{} | Out-Null
    $bill = Post "/api/purchase-orders/$($po.id)/create-bill" @{ documentDate = $billDate; dueDate = (Get-Date $billDate).AddDays(30).ToString('yyyy-MM-dd') }
    $bill = Post "/api/bills/$($bill.id)/post" @{}
    if ($payment) {
        Post "/api/bills/$($bill.id)/payments" $payment | Out-Null
    }
    Write-Host "   $($po.orderNo) -> $($bill.documentNo)  $($bill.totalAmount)" -ForegroundColor DarkGray
    return $bill
}

# Fully paid
Invoke-PurchaseFlow $vendors[0].id '2026-06-05' '2026-06-08' `
    @(@{ productId = $products[0].id; quantity = 10; unitPrice = 15000 },
      @{ productId = $products[3].id; quantity = 8;  unitPrice = 8200 }) `
    @{ method = 'BANK'; paymentDate = '2026-06-20'; amount = 215600; reference = 'NEFT-88421' } | Out-Null

# Partially paid
Invoke-PurchaseFlow $vendors[1].id '2026-07-02' '2026-07-05' `
    @(@{ productId = $products[2].id; quantity = 20; unitPrice = 5800 }) `
    @{ method = 'BANK'; paymentDate = '2026-07-25'; amount = 60000; reference = 'NEFT-90233' } | Out-Null

# Posted, unpaid (shows up as a payable)
Invoke-PurchaseFlow $vendors[0].id '2026-08-10' '2026-08-12' `
    @(@{ productId = $products[1].id; quantity = 6; unitPrice = 11000 }) `
    $null | Out-Null

# ---------------------------------------------------------------- sales
Write-Host 'Running sales flows...' -ForegroundColor Cyan

function Invoke-SalesFlow($customerId, $orderDate, $invoiceDate, $lines, $payments) {
    $so = Post '/api/sales-orders' @{ contactId = $customerId; orderDate = $orderDate; lines = $lines }
    Post "/api/sales-orders/$($so.id)/confirm" @{} | Out-Null
    $inv = Post "/api/sales-orders/$($so.id)/create-invoice" @{ documentDate = $invoiceDate; dueDate = (Get-Date $invoiceDate).AddDays(30).ToString('yyyy-MM-dd') }
    $inv = Post "/api/invoices/$($inv.id)/post" @{}
    foreach ($p in $payments) {
        Post "/api/invoices/$($inv.id)/payments" $p | Out-Null
    }
    Write-Host "   $($so.orderNo) -> $($inv.documentNo)  $($inv.totalAmount)" -ForegroundColor DarkGray
    return $inv
}

# Fully paid, split across cash and bank
Invoke-SalesFlow $customers[0].id '2026-06-15' '2026-06-18' `
    @(@{ productId = $products[0].id; quantity = 4; unitPrice = 25000 },
      @{ productId = $products[2].id; quantity = 6; unitPrice = 9500 }) `
    @(@{ method = 'CASH'; paymentDate = '2026-06-25'; amount = 57000; reference = 'Cash receipt 001' },
      @{ method = 'BANK'; paymentDate = '2026-07-01'; amount = 100000; reference = 'IMPS-55120' }) | Out-Null

# Fully paid
Invoke-SalesFlow $customers[1].id '2026-07-10' '2026-07-12' `
    @(@{ productId = $products[1].id; quantity = 12; unitPrice = 18500 },
      @{ productId = $products[4].id; quantity = 1;  unitPrice = 12000 }) `
    @(@{ method = 'BANK'; paymentDate = '2026-07-30'; amount = 234000; reference = 'RTGS-77310' }) | Out-Null

# Partially paid (receivable)
Invoke-SalesFlow $customers[2].id '2026-08-05' '2026-08-08' `
    @(@{ productId = $products[3].id; quantity = 10; unitPrice = 14000 }) `
    @(@{ method = 'BANK'; paymentDate = '2026-08-20'; amount = 80000; reference = 'IMPS-61190' }) | Out-Null

# Posted, unpaid (receivable)
Invoke-SalesFlow $customers[1].id '2026-08-22' '2026-08-25' `
    @(@{ productId = $products[2].id; quantity = 15; unitPrice = 9500 }) `
    @() | Out-Null

# A draft order left open, so the demo shows every stage of the workflow
$draft = Post '/api/sales-orders' @{
    contactId = $customers[0].id
    orderDate = '2026-09-01'
    notes     = 'Awaiting customer confirmation on finish and delivery date'
    lines     = @(@{ productId = $products[0].id; quantity = 2; unitPrice = 25000 })
}
Write-Host "   $($draft.orderNo) left as a draft" -ForegroundColor DarkGray

# ---------------------------------------------------------------- summary
Write-Host ''
$dash = Invoke-RestMethod -Uri "$BaseUrl/api/reports/dashboard" -Headers $script:H
$bs = Invoke-RestMethod -Uri "$BaseUrl/api/reports/balance-sheet" -Headers $script:H

Write-Host 'Demo data ready.' -ForegroundColor Green
Write-Host ("  Sales        {0,14:N2}" -f [decimal]$dash.totalSales)
Write-Host ("  Purchases    {0,14:N2}" -f [decimal]$dash.totalPurchases)
Write-Host ("  Cash + bank  {0,14:N2}" -f [decimal]$dash.cashAndBank)
Write-Host ("  Receivable   {0,14:N2}" -f [decimal]$dash.accountsReceivable)
Write-Host ("  Payable      {0,14:N2}" -f [decimal]$dash.accountsPayable)
Write-Host ("  Net profit   {0,14:N2}" -f [decimal]$dash.netProfit)
Write-Host ("  Total assets {0,14:N2}" -f [decimal]$bs.totalAssets)
Write-Host ("  Balanced     {0}" -f $bs.balanced) -ForegroundColor $(if ($bs.balanced) { 'Green' } else { 'Red' })
