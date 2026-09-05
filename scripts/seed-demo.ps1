# =====================================================================
# Demo dataset, created entirely through the public REST API.
#
# Nothing is inserted directly into the database, so every figure you see
# afterwards was produced by the real posting engine — the same code path
# a user exercises by clicking through the app.
#
# Usage:  pwsh -File scripts/seed-demo.ps1
# =====================================================================

$ErrorActionPreference = "Stop"
$base = if ($env:API_BASE) { $env:API_BASE } else { "http://localhost:8080/api" }
$pw = "Passw0rd!23"
$today = (Get-Date).ToString("yyyy-MM-dd")

function Register($loginId, $email, $person, $org, $type, $state, $city) {
    $body = @{ loginId = $loginId; email = $email; password = $pw; fullName = $person
               partyType = $type; organisationName = $org; state = $state; city = $city }
    try {
        return Invoke-RestMethod -Uri "$base/auth/register" -Method Post `
            -ContentType "application/json" -Body ($body | ConvertTo-Json)
    } catch {
        # Already seeded: sign in instead so the script is re-runnable.
        return Invoke-RestMethod -Uri "$base/auth/login" -Method Post `
            -ContentType "application/json" `
            -Body (@{ identifier = $loginId; password = $pw } | ConvertTo-Json)
    }
}
function Hdr($r) { @{ Authorization = "Bearer $($r.token)" } }
function Post-A($h, $p, $b) {
    Invoke-RestMethod -Uri "$base$p" -Method Post -Headers $h -ContentType "application/json" `
        -Body ($b | ConvertTo-Json -Depth 10)
}

Write-Host "Registering organisations..." -ForegroundColor Cyan
$seller   = Register "urbanfurn" "owner@urban.test"  "Anita Rao"    "Urban Furniture"  "SELLER"   "Gujarat"     "Ahmedabad"
$timber   = Register "timbertrd" "sales@timber.test" "Ravi Mehta"   "Timber Traders"   "VENDOR"   "Gujarat"     "Surat"
$fittings = Register "mumbaifit" "sales@mfit.test"   "Priya Nair"   "Mumbai Fittings"  "VENDOR"   "Maharashtra" "Mumbai"
$cust1    = Register "skylineco" "ap@skyline.test"   "Dev Sharma"   "Skyline Offices"  "CUSTOMER" "Gujarat"     "Ahmedabad"
$cust2    = Register "casaliving" "ap@casa.test"     "Neha Kapoor"  "Casa Living"      "CUSTOMER" "Rajasthan"   "Jaipur"

$hSeller = Hdr $seller; $hTimber = Hdr $timber; $hFit = Hdr $fittings
$hCust1  = Hdr $cust1;  $hCust2  = Hdr $cust2

Write-Host "Adding catalogue and projects..." -ForegroundColor Cyan
foreach ($p in @(
    @{name="Oak Dining Table";  type="GOODS"; salesPrice="25000.00"; cost="15000.00"; taxRate="18.00"; hsnCode="9403"; category="Tables"},
    @{name="Ergonomic Chair";   type="GOODS"; salesPrice="9500.00";  cost="5500.00";  taxRate="18.00"; hsnCode="9401"; category="Seating"},
    @{name="Walnut Desk";       type="GOODS"; salesPrice="18500.00"; cost="11000.00"; taxRate="18.00"; hsnCode="9403"; category="Tables"},
    @{name="Design Consultation"; type="SERVICE"; salesPrice="12000.00"; cost="0.00"; taxRate="18.00"; category="Services"}
)) { try { Post-A $hSeller "/products" $p | Out-Null } catch {} }

foreach ($a in @(
    @{code="SHOWROOM"; name="Ahmedabad Showroom"; type="DEPARTMENT"},
    @{code="PRJ-VILLA"; name="Villa Fit-out"; type="PROJECT"}
)) { try { Post-A $hSeller "/analytic-accounts" $a | Out-Null } catch {} }

try {
    $projects = Invoke-RestMethod -Uri "$base/analytic-accounts" -Headers $hSeller
    $villa = $projects | Where-Object { $_.code -eq "PRJ-VILLA" } | Select-Object -First 1
    if ($villa) {
        Post-A $hSeller "/budgets" @{ name="Villa fit-out materials"; analyticAccountId=$villa.id
            periodStart=(Get-Date).AddMonths(-3).ToString("yyyy-MM-dd")
            periodEnd=(Get-Date).AddMonths(3).ToString("yyyy-MM-dd")
            plannedAmount="200000.00"; responsible="Anita Rao" } | Out-Null
    }
} catch {}

# ---------------------------------------------------------------------
# A deal, driven all the way through by whichever side is entitled to
# act at each step.
# ---------------------------------------------------------------------
function Trade($buyerH, $sellerH, $sellerPartyId, $lines, $stopAt, [switch]$BuyerIsCustomer) {
    # Customers act through /api/portal — the server bars a USER account
    # from every back-office path, which is the point of the split.
    $buyerRoot = if ($BuyerIsCustomer) { "/portal/my-orders" } else { "/deals" }

    $deal = Post-A $buyerH $buyerRoot @{ sellerPartyId=$sellerPartyId; dealDate=$today; lines=$lines }
    if ($stopAt -eq "DRAFT") { return $deal }

    Post-A $buyerH "$buyerRoot/$($deal.id)/send" @{} | Out-Null
    if ($stopAt -eq "SENT") { return $deal }

    Post-A $sellerH "/deals/$($deal.id)/accept" @{} | Out-Null
    if ($stopAt -eq "ACCEPTED") { return $deal }

    Post-A $sellerH "/deals/$($deal.id)/deliver" @{} | Out-Null
    if ($stopAt -eq "DELIVERED") { return $deal }

    $r = Post-A $sellerH "/deals/$($deal.id)/invoice" @{}
    if ($stopAt -eq "INVOICED") { return $r }

    if ($stopAt -eq "PARTIAL") {
        $half = [Math]::Round([decimal]$r.totalAmount / 2, 2)
        Post-A $sellerH "/deals/$($deal.id)/settle" @{ method="BANK"; settlementDate=$today; amount="$half" } | Out-Null
    } else {
        Post-A $sellerH "/deals/$($deal.id)/settle" @{ method="BANK"; settlementDate=$today; amount=$r.totalAmount } | Out-Null
    }
    return $r
}

Write-Host "Purchases: Urban Furniture buying from its vendors (mirrored)..." -ForegroundColor Cyan
Trade $hSeller $hTimber $timber.user.partyId @(
    @{description="Teak planks 8ft"; hsnCode="4407"; quantity="40.000"; unitPrice="1800.00"; taxRate="18.00"}) "PAID" | Out-Null
Trade $hSeller $hTimber $timber.user.partyId @(
    @{description="Oak veneer sheets"; hsnCode="4408"; quantity="60.000"; unitPrice="950.00"; taxRate="18.00"}) "PARTIAL" | Out-Null
Trade $hSeller $hFit $fittings.user.partyId @(
    @{description="Chair castors (set)"; hsnCode="8302"; quantity="120.000"; unitPrice="240.00"; taxRate="18.00"}) "INVOICED" | Out-Null
Trade $hSeller $hFit $fittings.user.partyId @(
    @{description="Drawer runners"; hsnCode="8302"; quantity="80.000"; unitPrice="310.00"; taxRate="18.00"}) "ACCEPTED" | Out-Null

Write-Host "Sales: customers buying from Urban Furniture..." -ForegroundColor Cyan
Trade $hCust1 $hSeller $seller.user.partyId @(
    @{description="Walnut Office Desk"; hsnCode="9403"; quantity="6.000"; unitPrice="18500.00"; taxRate="18.00"}
    @{description="Ergonomic Chair";    hsnCode="9401"; quantity="12.000"; unitPrice="9500.00"; taxRate="18.00"}) "PAID" -BuyerIsCustomer | Out-Null
Trade $hCust1 $hSeller $seller.user.partyId @(
    @{description="Design Consultation"; quantity="1.000"; unitPrice="12000.00"; taxRate="18.00"}) "PARTIAL" -BuyerIsCustomer | Out-Null
Trade $hCust2 $hSeller $seller.user.partyId @(
    @{description="Oak Dining Table"; hsnCode="9403"; quantity="3.000"; unitPrice="25000.00"; taxRate="18.00"}) "INVOICED" -BuyerIsCustomer | Out-Null
Trade $hCust2 $hSeller $seller.user.partyId @(
    @{description="Teak Bookshelf"; hsnCode="9403"; quantity="4.000"; unitPrice="14000.00"; taxRate="18.00"}) "SENT" -BuyerIsCustomer | Out-Null

Write-Host "Direct: a customer buying straight from a vendor..." -ForegroundColor Cyan
Trade $hCust2 $hFit $fittings.user.partyId @(
    @{description="Brass cabinet handles"; hsnCode="8302"; quantity="50.000"; unitPrice="420.00"; taxRate="18.00"}) "PAID" -BuyerIsCustomer | Out-Null

Write-Host "`nDone. Sign in with any of these (password: $pw)" -ForegroundColor Green
@(
    @{ Login="urbanfurn";  Who="Urban Furniture (SELLER, admin)" },
    @{ Login="timbertrd";  Who="Timber Traders (VENDOR, admin)" },
    @{ Login="mumbaifit";  Who="Mumbai Fittings (VENDOR, admin)" },
    @{ Login="skylineco";  Who="Skyline Offices (CUSTOMER portal)" },
    @{ Login="casaliving"; Who="Casa Living (CUSTOMER portal)" }
) | ForEach-Object { "  {0,-12} {1}" -f $_.Login, $_.Who }
