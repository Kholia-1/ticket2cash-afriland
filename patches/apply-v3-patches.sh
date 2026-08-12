#!/usr/bin/env bash
# ===================================================================
# V3 UX cleanup patches (bash version, uses Python for reliable UTF-8)
# Idempotent. Run from repo root: bash patches/apply-v3-patches.sh
# ===================================================================
set -euo pipefail
GRN='\033[0;32m'; YEL='\033[1;33m'; RED='\033[0;31m'; NC='\033[0m'
ok()   { echo -e "${GRN}[OK]${NC}   $*"; }
warn() { echo -e "${YEL}[!]${NC}    $*"; }
err()  { echo -e "${RED}[FAIL]${NC} $*"; exit 1; }

IDX=src/main/resources/static/index.html
[[ -f "$IDX" ]] || err "$IDX not found — run from repo root"

STAMP=$(date +%Y%m%d-%H%M%S)
cp "$IDX" "$IDX.bak-$STAMP"
ok "Backup: $IDX.bak-$STAMP"

if grep -q "function renderLoyalty()" "$IDX"; then
  warn "V3 already applied — skipping"
  exit 0
fi

python3 - "$IDX" <<'PY'
import sys
p = sys.argv[1]
with open(p) as f: c = f.read()

def sub(old, new, label):
    global c
    if old in c:
        c = c.replace(old, new)
        print(f"[OK]   {label}")
    else:
        print(f"[!]    {label} anchor not found")

# 4a KPI top border
sub('.kpi::before{content:"";position:absolute;top:0;left:0;right:0;height:3px;background:linear-gradient(90deg,var(--brand),#f2545b)}',
    '.kpi::before{content:none}',
    "#4a red gradient top border removed")

# 1a NAV integration
sub('group:"g_integration",items:["integration","webhooks",',
    'group:"g_integration",items:["webhooks",',
    "#1a Integration sidebar entry removed")

# 1b ROUTES integration
sub('webhooks:renderWebhooks,integration:renderIntegration,',
    'webhooks:renderWebhooks,',
    "#1b integration route removed")

# 2a loyalty route
sub('loyalty:()=>{location.href="/loyalty.html";},',
    'loyalty:renderLoyalty,',
    "#2a loyalty route now calls renderLoyalty")

# 2b renderLoyalty injection
fn = '''async function renderLoyalty(){
  const theme = document.documentElement.getAttribute("data-theme") || "light";
  document.getElementById("content").innerHTML =
    `<div class="page" style="padding:0"><iframe src="/loyalty.html?theme=${theme}"
       style="width:100%;height:calc(100vh - 84px);border:none;border-radius:12px;background:var(--surface);display:block"></iframe></div>`;
}

'''
sub("function kpiCard(k){return", fn + "function kpiCard(k){return",
    "#2b renderLoyalty function injected")

# 3 Products manage restricted to PARTNER
sub('const canManage=ME.role!=="LECTEUR";',
    'const canManage=ME.role==="PARTNER";',
    "#3 Products management restricted to PARTNER")

# 4b Products filter cards → natural chip
old_cards = '''    <div class="kpi-grid" style="grid-template-columns:repeat(2,minmax(0,320px))">
      <div class="kpi"><div class="kpi-label">${t("kpi_prodfilter")}</div><div class="kpi-val" id="p_count">—</div></div>
      <div class="kpi"><div class="kpi-label">${t("kpi_merchant")}</div><div class="kpi-val" id="p_merch_name" style="font-size:19px">—</div></div>
    </div>
'''
new_chip = '''    <div style="display:flex;gap:14px;align-items:center;margin-bottom:14px;padding:10px 14px;background:var(--surface-2);border:1px solid var(--border);border-radius:10px;font-size:13px;color:var(--text-2)">
      <span>${t("kpi_prodfilter")}:</span><strong id="p_count" style="color:var(--text)">—</strong>
      <span style="width:1px;height:14px;background:var(--border)"></span>
      <span>${t("kpi_merchant")}:</span><strong id="p_merch_name" style="color:var(--text)">—</strong>
    </div>
'''
sub(old_cards, new_chip, "#4b Products filter cards replaced with natural chip")

# 1c API Reference in Webhooks
api_ref = '''    <details class="panel" style="margin-bottom:16px" open><summary style="padding:14px 18px;font-weight:600;cursor:pointer;list-style:none"><h3 style="display:inline;font-size:14px">📚 API Reference</h3></summary><div class="panel-body">
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
'''
wh_anchor = '<div class="grid-2">\n      <div class="panel"><div class="panel-head"><h3>${t("wh_recent")}</h3>'
sub(wh_anchor, api_ref + wh_anchor, "#1c API Reference section added to Webhooks")

with open(p, "w") as f: f.write(c)
PY

echo
ok "V3 UX cleanup applied. Roll back with $IDX.bak-$STAMP"
