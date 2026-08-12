# ===================================================================
# V3 UX cleanup patches (PS 5.1 compatible, .NET File I/O)
#  1. Remove Integration sidebar entry; add API Reference to Webhooks page
#  2. Loyalty tab renders in-portal (iframe embed) instead of full nav-away
#  3. Products page becomes read-only for ADMIN (merchants own products)
#  4. Remove red-brown top gradient from all KPI cards
#     + replace Products filter KPI cards with a natural inline chip
# Idempotent. Run from repo root: .\patches\apply-v3-patches.ps1
# ===================================================================

$ErrorActionPreference = "Stop"

function Ok($msg)   { Write-Host "[OK]   $msg" -ForegroundColor Green }
function Warn($msg) { Write-Host "[!]    $msg" -ForegroundColor Yellow }
function Err($msg)  { Write-Host "[FAIL] $msg" -ForegroundColor Red; exit 1 }

$idxPath = "src\main\resources\static\index.html"
if (-not (Test-Path $idxPath)) { Err "$idxPath not found. Run from repo root." }

$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
Copy-Item $idxPath "$idxPath.bak-$stamp"
Ok "Backup: $idxPath.bak-$stamp"

$utf8 = New-Object System.Text.UTF8Encoding($false)
$content = [System.IO.File]::ReadAllText((Resolve-Path $idxPath), $utf8)

if ($content -match "function renderLoyalty\(\)") {
    Warn "V3 already applied (renderLoyalty exists) - skipping"
    exit 0
}

# ---------- #4a. Kill the red top border on KPI cards ----------
$oldCss = '.kpi::before{content:"";position:absolute;top:0;left:0;right:0;height:3px;background:linear-gradient(90deg,var(--brand),#f2545b)}'
if ($content.Contains($oldCss)) {
    $content = $content.Replace($oldCss, '.kpi::before{content:none}')
    Ok "#4a - removed red gradient top border on KPI cards"
} else {
    Warn "#4a - CSS anchor not found (already patched?)"
}

# ---------- #1a. Remove Integration from sidebar ----------
$oldNav = 'group:"g_integration",items:["integration","webhooks",'
$newNav = 'group:"g_integration",items:["webhooks",'
if ($content.Contains($oldNav)) {
    $content = $content.Replace($oldNav, $newNav)
    Ok "#1a - Integration sidebar entry removed"
} else {
    Warn "#1a - NAV anchor not found (already patched?)"
}

# ---------- #1b. Remove integration from ROUTES ----------
$oldRoute = 'webhooks:renderWebhooks,integration:renderIntegration,'
$newRoute = 'webhooks:renderWebhooks,'
if ($content.Contains($oldRoute)) {
    $content = $content.Replace($oldRoute, $newRoute)
    Ok "#1b - integration ROUTES entry removed"
}

# ---------- #2. Loyalty renders in-portal via iframe ----------
$oldLoyalty = 'loyalty:()=>{location.href="/loyalty.html";},'
$newLoyalty = 'loyalty:renderLoyalty,'
if ($content.Contains($oldLoyalty)) {
    $content = $content.Replace($oldLoyalty, $newLoyalty)
    Ok "#2a - loyalty ROUTES entry now calls renderLoyalty"
} else {
    Warn "#2a - loyalty route already patched or missing"
}

# ---------- #2b. Inject renderLoyalty function ----------
$loyaltyFn = @'
async function renderLoyalty(){
  const theme = document.documentElement.getAttribute("data-theme") || "light";
  document.getElementById("content").innerHTML =
    `<div class="page" style="padding:0"><iframe src="/loyalty.html?theme=${theme}"
       style="width:100%;height:calc(100vh - 84px);border:none;border-radius:12px;background:var(--surface);display:block"></iframe></div>`;
}

'@
$loyaltyAnchor = "function kpiCard(k){return"
if ($content.Contains($loyaltyAnchor)) {
    $content = $content.Replace($loyaltyAnchor, $loyaltyFn + $loyaltyAnchor)
    Ok "#2b - renderLoyalty function injected"
} else {
    Err "Could not find kpiCard anchor for renderLoyalty injection"
}

# ---------- #3. Products: PARTNER manages, ADMIN views ----------
$oldRole = 'const canManage=ME.role!=="LECTEUR";'
$newRole = 'const canManage=ME.role==="PARTNER";'
if ($content.Contains($oldRole)) {
    $content = $content.Replace($oldRole, $newRole)
    Ok "#3 - Products management restricted to PARTNER role"
} else {
    Warn "#3 - canManage anchor not found (already patched?)"
}

# ---------- #4b. Replace Products filter KPI cards with natural chip ----------
$oldCards = @'
    <div class="kpi-grid" style="grid-template-columns:repeat(2,minmax(0,320px))">
      <div class="kpi"><div class="kpi-label">${t("kpi_prodfilter")}</div><div class="kpi-val" id="p_count">—</div></div>
      <div class="kpi"><div class="kpi-label">${t("kpi_merchant")}</div><div class="kpi-val" id="p_merch_name" style="font-size:19px">—</div></div>
    </div>
'@
$newChip = @'
    <div style="display:flex;gap:14px;align-items:center;margin-bottom:14px;padding:10px 14px;background:var(--surface-2);border:1px solid var(--border);border-radius:10px;font-size:13px;color:var(--text-2)">
      <span>${t("kpi_prodfilter")}:</span><strong id="p_count" style="color:var(--text)">—</strong>
      <span style="width:1px;height:14px;background:var(--border)"></span>
      <span>${t("kpi_merchant")}:</span><strong id="p_merch_name" style="color:var(--text)">—</strong>
    </div>
'@
if ($content.Contains($oldCards)) {
    $content = $content.Replace($oldCards, $newChip)
    Ok "#4b - Products filter cards replaced with natural inline chip"
} else {
    Warn "#4b - Products filter cards anchor not found (already patched?)"
}

# ---------- #1c. Enhance Webhooks page with API Reference section ----------
$apiRefSection = @'
    <details class="panel" style="margin-bottom:16px" open><summary style="padding:14px 18px;font-weight:600;cursor:pointer;list-style:none"><h3 style="display:inline;font-size:14px">📚 API Reference</h3></summary><div class="panel-body">
      <div class="table-wrap"><div class="table-scroll"><table><thead><tr><th style="width:80px">Méthode</th><th>Endpoint</th><th>Description</th></tr></thead>
      <tbody>
        <tr><td><span class="badge b-ok">POST</span></td><td><span class="mono">/api/auth/login</span></td><td style="color:var(--text-2)">Connexion (session)</td></tr>
        <tr><td><span class="badge b-info">GET</span></td><td><span class="mono">/api/merchants</span></td><td style="color:var(--text-2)">Commerçants</td></tr>
        <tr><td><span class="badge b-info">GET</span></td><td><span class="mono">/api/products</span></td><td style="color:var(--text-2)">Catalogue produits</td></tr>
        <tr><td><span class="badge b-info">GET</span></td><td><span class="mono">/api/campaigns</span></td><td style="color:var(--text-2)">Campagnes</td></tr>
        <tr><td><span class="badge b-info">GET</span></td><td><span class="mono">/api/claims</span></td><td style="color:var(--text-2)">Réclamations</td></tr>
        <tr><td><span class="badge b-info">GET</span></td><td><span class="mono">/api/cashback/payments</span></td><td style="color:var(--text-2)">Paiements cashback</td></tr>
        <tr><td><span class="badge b-info">GET</span></td><td><span class="mono">/api/loyalty/stats</span></td><td style="color:var(--text-2)">Statistiques fidélité</td></tr>
        <tr><td><span class="badge b-info">GET</span></td><td><span class="mono">/api/loyalty/clients/top</span></td><td style="color:var(--text-2)">Top clients fidélité</td></tr>
        <tr><td><span class="badge b-ok">POST</span></td><td><span class="mono">/api/webhook/transaction</span></td><td style="color:var(--text-2)">Webhook transaction POS</td></tr>
        <tr><td><span class="badge b-ok">POST</span></td><td><span class="mono">/api/mobile/ocr</span></td><td style="color:var(--text-2)">OCR ticket (mobile)</td></tr>
      </tbody></table></div></div>
    </div></details>
'@
# Inject before the "recent webhooks / unmatched" grid-2 at the bottom of renderWebhooks
$whAnchor = '<div class="grid-2">
      <div class="panel"><div class="panel-head"><h3>${t("wh_recent")}</h3>'
if ($content.Contains($whAnchor)) {
    $content = $content.Replace($whAnchor, $apiRefSection + $whAnchor)
    Ok "#1c - API Reference section added to Webhooks page"
} else {
    Warn "#1c - Webhooks anchor not found"
}

# Write via .NET (UTF-8 no-BOM, guaranteed)
[System.IO.File]::WriteAllText((Resolve-Path $idxPath), $content, $utf8)

Write-Host ""
Ok "V3 UX cleanup applied. Roll back with $idxPath.bak-$stamp"
