#!/usr/bin/env bash
# ===================================================================
# Loyalty engine V2 patches (bash version — for Git Bash / WSL / server).
# Same behavior as apply-v2-patches.ps1. Idempotent.
# Run from repo root: bash patches/apply-v2-patches.sh
# ===================================================================
set -euo pipefail
GRN='\033[0;32m'; YEL='\033[1;33m'; RED='\033[0;31m'; NC='\033[0m'
ok()   { echo -e "${GRN}[OK]${NC}   $*"; }
warn() { echo -e "${YEL}[!]${NC}    $*"; }
err()  { echo -e "${RED}[FAIL]${NC} $*"; exit 1; }

IDX=src/main/resources/static/index.html
FRAG=patches/render-business-dashboard.js

[[ -f "$IDX" ]]  || err "$IDX not found — run from repo root"
[[ -f "$FRAG" ]] || err "$FRAG not found next to this script"

STAMP=$(date +%Y%m%d-%H%M%S)
cp "$IDX" "$IDX.bak-$STAMP"
ok "Backup: $IDX.bak-$STAMP"

if grep -q "renderBusinessDashboard" "$IDX"; then
  warn "Already applied — skipping"
  exit 0
fi

python3 - "$IDX" "$FRAG" <<'PY'
import sys
idx_path, frag_path = sys.argv[1], sys.argv[2]
with open(idx_path) as f: idx = f.read()
with open(frag_path) as f: frag = f.read()

# 1) ROUTES
old = 'dashboard:renderDashboard,businessDashboard:renderDashboard,security:renderSecurity,'
new = 'dashboard:renderDashboard,businessDashboard:renderBusinessDashboard,security:renderSecurity,'
assert old in idx, "ROUTES line not found"
idx = idx.replace(old, new)

# 2) Inject function before kpiCard
anchor = "function kpiCard(k){return"
assert anchor in idx, "kpiCard anchor not found"
idx = idx.replace(anchor, frag + "\n" + anchor)

# 3) i18n
fr_keys = {
  "bd_sub":"Vue financière et clients à fort volume",
  "bd_top_clients":"Top clients Afriland",
  "bd_all":"Tous","bd_companies":"Entreprises","bd_individuals":"Particuliers",
  "bd_view":"Voir","bd_reward":"Récompenser",
  "bd_no_clients":"Aucun client enrôlé. Uploadez un batch dans Fidélité pour commencer.",
  "bd_rank":"#","bd_name":"Nom","bd_type":"Type","bd_volume":"Volume","bd_cashback":"Cashback",
  "bd_loyalty_clients":"Clients fidélité","bd_loyalty_paid":"Cashback fidélité versé",
  "bd_indiv_comp":"Particuliers + Entreprises",
  "bd_campaigns_active":"campagnes actives","bd_batches_credited":"batches crédités",
}
en_keys = {
  "bd_sub":"Financial overview and high-volume clients",
  "bd_top_clients":"Top Afriland clients",
  "bd_all":"All","bd_companies":"Companies","bd_individuals":"Individuals",
  "bd_view":"View","bd_reward":"Reward",
  "bd_no_clients":"No enrolled clients yet. Upload a batch in Loyalty to start.",
  "bd_rank":"#","bd_name":"Name","bd_type":"Type","bd_volume":"Volume","bd_cashback":"Cashback",
  "bd_loyalty_clients":"Loyalty clients","bd_loyalty_paid":"Loyalty cashback paid",
  "bd_indiv_comp":"Individuals + Companies",
  "bd_campaigns_active":"active campaigns","bd_batches_credited":"credited batches",
}
def block(m): return ",".join(f'{k}:"{v}"' for k,v in m.items()) + ","

fr_anchor = 'loyalty:"Fidélité Afriland",'
en_anchor = 'loyalty:"Afriland Loyalty",'
if fr_anchor in idx:
    idx = idx.replace(fr_anchor, fr_anchor + block(fr_keys))
if en_anchor in idx:
    idx = idx.replace(en_anchor, en_anchor + block(en_keys))

with open(idx_path, "w") as f: f.write(idx)
print("done")
PY

ok "index.html: dashboard dedupe complete"
echo
ok "All v2 patches applied. Roll back with $IDX.bak-$STAMP"
