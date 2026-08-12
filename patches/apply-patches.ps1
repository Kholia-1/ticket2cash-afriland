# ===================================================================
# PowerShell equivalent of apply-patches.sh
# Run from the repo root: .\patches\apply-patches.ps1
# Idempotent: safe to re-run.
# ===================================================================

$ErrorActionPreference = "Stop"

function Ok($msg)   { Write-Host "[OK]   $msg" -ForegroundColor Green }
function Warn($msg) { Write-Host "[!]    $msg" -ForegroundColor Yellow }
function Err($msg)  { Write-Host "[FAIL] $msg" -ForegroundColor Red; exit 1 }

$POM   = "pom.xml"
$IDX   = "src\main\resources\static\index.html"
$PROPS = "src\main\resources\application.properties"

if (-not (Test-Path $POM)) { Err "Run this from the repo root (pom.xml not found)" }
if (-not (Test-Path $IDX)) { Err "index.html not found at $IDX" }

$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
Copy-Item $POM "$POM.bak-$stamp"
Copy-Item $IDX "$IDX.bak-$stamp"
if (Test-Path $PROPS) { Copy-Item $PROPS "$PROPS.bak-$stamp" }
Ok "Backups created with suffix .bak-$stamp"

# ---------- pom.xml ----------
$pomContent = Get-Content $POM -Raw
if ($pomContent -match "poi-ooxml") {
    Warn "pom.xml already contains poi-ooxml - skipping"
} else {
    $dep = @"
        <dependency>
            <groupId>org.apache.poi</groupId>
            <artifactId>poi</artifactId>
            <version>5.2.5</version>
        </dependency>
        <dependency>
            <groupId>org.apache.poi</groupId>
            <artifactId>poi-ooxml</artifactId>
            <version>5.2.5</version>
        </dependency>
"@
    $pomContent = $pomContent -replace '(\s*)</dependencies>', "`n$dep`n`$1</dependencies>"
    Set-Content -Path $POM -Value $pomContent -NoNewline
    Ok "pom.xml: Apache POI dependencies added"
}

# ---------- application.properties ----------
if (Test-Path $PROPS) {
    $propsContent = Get-Content $PROPS -Raw
    if ($propsContent -match "spring\.servlet\.multipart\.max-file-size") {
        Warn "application.properties already has multipart size - skipping"
    } else {
        Add-Content -Path $PROPS -Value "`n# Loyalty batch uploads`nspring.servlet.multipart.max-file-size=20MB`nspring.servlet.multipart.max-request-size=20MB"
        Ok "application.properties: multipart limits raised to 20MB"
    }
}

# ---------- index.html ----------
$idxContent = Get-Content $IDX -Raw
if ($idxContent -match 'loyalty:\(\)=>' -or $idxContent -match 'loyalty:renderLoyalty') {
    Warn "index.html already has a loyalty route - skipping HTML patch"
} else {
    # 1) Icon - insert just before subPartners in IC = { ... }
    $iconLine = " loyalty:'<path d=`"M20 12V8H6a2 2 0 0 1-2-2c0-1.1.9-2 2-2h12v4`"/><path d=`"M4 6v12c0 1.1.9 2 2 2h14v-4`"/><path d=`"M18 12a2 2 0 0 0 0 4h4v-4Z`"/>',"
    if ($idxContent -notmatch '\n subPartners:') { Err "Could not locate IC.subPartners marker" }
    $idxContent = $idxContent -replace '(\n subPartners:)', "`n$iconLine`$1"

    # 2) NAV.ADMIN g_ops - insert "loyalty" at the start of items list
    if ($idxContent -notmatch 'group:"g_ops",items:\["merchants"') { Err "Could not locate NAV.ADMIN g_ops marker" }
    $idxContent = $idxContent -replace 'group:"g_ops",items:\["merchants"', 'group:"g_ops",items:["loyalty","merchants"'

    # 3) ROUTES - add loyalty route
    if ($idxContent -notmatch 'const ROUTES=\{') { Err "Could not locate ROUTES marker" }
    $idxContent = $idxContent -replace '(const ROUTES=\{\r?\n)', "`$1 loyalty:()=>{location.href=`"/loyalty.html`";},`n"

    # 4) i18n labels
    $frMerch = 'merchants:"Commerçants",'
    $enMerch = 'merchants:"Merchants",'
    if ($idxContent.Contains($frMerch)) {
        $idxContent = [regex]::Replace($idxContent, [regex]::Escape($frMerch), $frMerch + 'loyalty:"Fidélité Afriland",', 1)
    }
    if ($idxContent.Contains($enMerch)) {
        $idxContent = [regex]::Replace($idxContent, [regex]::Escape($enMerch), $enMerch + 'loyalty:"Afriland Loyalty",', 1)
    }

    Set-Content -Path $IDX -Value $idxContent -NoNewline
    Ok "index.html: nav entry, route, icon and i18n labels inserted"
}

Write-Host ""
Ok "All patches applied. To roll back, restore the .bak-$stamp files."
