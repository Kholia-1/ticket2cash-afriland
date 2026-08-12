# ===================================================================
# Loyalty engine V2 patches (v2 script, PS 5.1 compatible)
#  1. Split renderDashboard vs renderBusinessDashboard (removes duplicate)
#  2. Wire ROUTES to the new function
#  3. Add i18n keys for the new labels
# Uses .NET File I/O to avoid Set-Content encoding quirks on PS 5.1.
# Uses ASCII-only anchors to avoid UTF-8 comparison issues.
# Idempotent. Run from repo root: .\patches\apply-v2-patches.ps1
# ===================================================================

$ErrorActionPreference = "Stop"

function Ok($msg)   { Write-Host "[OK]   $msg" -ForegroundColor Green }
function Warn($msg) { Write-Host "[!]    $msg" -ForegroundColor Yellow }
function Err($msg)  { Write-Host "[FAIL] $msg" -ForegroundColor Red; exit 1 }

$idxPath = "src\main\resources\static\index.html"
$FRAG = "patches\render-business-dashboard.js"

if (-not (Test-Path $idxPath))  { Err "$idxPath not found. Run from repo root." }
if (-not (Test-Path $FRAG)) { Err "$FRAG not found next to this script." }

$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
Copy-Item $idxPath "$idxPath.bak-$stamp"
Ok "Backup: $idxPath.bak-$stamp"

# Read via .NET (guarantees UTF-8 no-BOM)
$utf8 = New-Object System.Text.UTF8Encoding($false)
$idx = [System.IO.File]::ReadAllText((Resolve-Path $idxPath), $utf8)

# Skip if already applied
if ($idx -match "renderBusinessDashboard") {
    Warn "renderBusinessDashboard already present - skipping"
    exit 0
}

# ---------- 1. Rewire ROUTES ----------
$old = 'dashboard:renderDashboard,businessDashboard:renderDashboard,security:renderSecurity,'
$new = 'dashboard:renderDashboard,businessDashboard:renderBusinessDashboard,security:renderSecurity,'
if (-not $idx.Contains($old)) { Err "Could not find the ROUTES line" }
$idx = $idx.Replace($old, $new)
Ok "ROUTES: businessDashboard now points to renderBusinessDashboard"

# ---------- 2. Inject the new function ----------
$fragment = [System.IO.File]::ReadAllText((Resolve-Path $FRAG), $utf8)
$anchor = "function kpiCard(k){return"
if (-not $idx.Contains($anchor)) { Err "Could not find kpiCard anchor" }
$idx = $idx.Replace($anchor, $fragment + "`n" + $anchor)
Ok "renderBusinessDashboard function injected"

# ---------- 3. i18n keys ----------
# Use ASCII-only anchors: 'products:"Produits"' (FR) and 'products:"Products"' (EN)
# — both single-occurrence, no accented chars.
$frKeys = @{
    "bd_sub" = "Vue financiere et clients a fort volume"
    "bd_top_clients" = "Top clients Afriland"
    "bd_all" = "Tous"; "bd_companies" = "Entreprises"; "bd_individuals" = "Particuliers"
    "bd_view" = "Voir"; "bd_reward" = "Recompenser"
    "bd_no_clients" = "Aucun client enrole. Uploadez un batch dans Fidelite pour commencer."
    "bd_rank" = "#"; "bd_name" = "Nom"; "bd_type" = "Type"
    "bd_volume" = "Volume"; "bd_cashback" = "Cashback"
    "bd_loyalty_clients" = "Clients fidelite"
    "bd_loyalty_paid" = "Cashback fidelite verse"
    "bd_indiv_comp" = "Particuliers + Entreprises"
    "bd_campaigns_active" = "campagnes actives"; "bd_batches_credited" = "batches credites"
}
$enKeys = @{
    "bd_sub" = "Financial overview and high-volume clients"
    "bd_top_clients" = "Top Afriland clients"
    "bd_all" = "All"; "bd_companies" = "Companies"; "bd_individuals" = "Individuals"
    "bd_view" = "View"; "bd_reward" = "Reward"
    "bd_no_clients" = "No enrolled clients yet. Upload a batch in Loyalty to start."
    "bd_rank" = "#"; "bd_name" = "Name"; "bd_type" = "Type"
    "bd_volume" = "Volume"; "bd_cashback" = "Cashback"
    "bd_loyalty_clients" = "Loyalty clients"; "bd_loyalty_paid" = "Loyalty cashback paid"
    "bd_indiv_comp" = "Individuals + Companies"
    "bd_campaigns_active" = "active campaigns"; "bd_batches_credited" = "credited batches"
}

function BuildKeyBlock($map) {
    $parts = @()
    foreach ($k in $map.Keys) { $parts += "$k`:`"$($map[$k])`"" }
    return ($parts -join ",") + ","
}

# FR block anchor (ASCII-safe)
$frAnchor = 'products:"Produits",'
if ($idx.Contains($frAnchor)) {
    $idx = $idx.Replace($frAnchor, (BuildKeyBlock $frKeys) + $frAnchor)
    Ok "FR i18n keys inserted"
} else {
    Warn "FR anchor 'products:Produits' not found - i18n FR skipped (fallback will use EN)"
}

# EN block anchor (ASCII-safe)
$enAnchor = 'products:"Products",'
if ($idx.Contains($enAnchor)) {
    $idx = $idx.Replace($enAnchor, (BuildKeyBlock $enKeys) + $enAnchor)
    Ok "EN i18n keys inserted"
} else {
    Warn "EN anchor 'products:Products' not found - i18n EN skipped"
}

# Write via .NET (UTF-8 no-BOM, guaranteed)
[System.IO.File]::WriteAllText((Resolve-Path $idxPath), $idx, $utf8)
Ok "index.html: dashboard dedupe complete"
Write-Host ""
Ok "All v2 patches applied. Roll back with $idxPath.bak-$stamp"
