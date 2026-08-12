#!/usr/bin/env bash
# ===================================================================
# Apply the loyalty-engine patches to pom.xml and static/index.html.
# Idempotent: safe to re-run — checks for existing markers.
# Run from the repo root: bash patches/apply-patches.sh
# ===================================================================
set -euo pipefail

RED='\033[0;31m'; GRN='\033[0;32m'; YEL='\033[1;33m'; NC='\033[0m'
ok()   { echo -e "${GRN}✔${NC} $*"; }
warn() { echo -e "${YEL}!${NC} $*"; }
err()  { echo -e "${RED}✘${NC} $*"; exit 1; }

POM=pom.xml
IDX=src/main/resources/static/index.html
PROPS=src/main/resources/application.properties

[[ -f "$POM" ]] || err "Run this from the repo root (pom.xml not found)"
[[ -f "$IDX" ]] || err "index.html not found at $IDX"

# --- backup ---
STAMP=$(date +%Y%m%d-%H%M%S)
cp "$POM" "$POM.bak-$STAMP"
cp "$IDX" "$IDX.bak-$STAMP"
[[ -f "$PROPS" ]] && cp "$PROPS" "$PROPS.bak-$STAMP"
ok "Backups created with suffix .bak-$STAMP"

# ---------------------------------------------------------------- pom.xml
if grep -q "poi-ooxml" "$POM"; then
  warn "pom.xml already contains poi-ooxml — skipping"
else
  python3 - <<'PY'
import re
with open("pom.xml") as f: content = f.read()
dep = """        <dependency>
            <groupId>org.apache.poi</groupId>
            <artifactId>poi</artifactId>
            <version>5.2.5</version>
        </dependency>
        <dependency>
            <groupId>org.apache.poi</groupId>
            <artifactId>poi-ooxml</artifactId>
            <version>5.2.5</version>
        </dependency>
"""
# Insert just before the closing </dependencies>
content = re.sub(r'(\s*)</dependencies>', "\n" + dep + r'\1</dependencies>', content, count=1)
with open("pom.xml","w") as f: f.write(content)
PY
  ok "pom.xml: Apache POI dependencies added"
fi

# ---------------------------------------------------------------- application.properties
if [[ -f "$PROPS" ]]; then
  if grep -q "spring.servlet.multipart.max-file-size" "$PROPS"; then
    warn "application.properties already has multipart size — skipping"
  else
    cat >> "$PROPS" << 'EOF'

# Loyalty batch uploads
spring.servlet.multipart.max-file-size=20MB
spring.servlet.multipart.max-request-size=20MB
EOF
    ok "application.properties: multipart limits raised to 20MB"
  fi
fi

# ---------------------------------------------------------------- index.html
if grep -q "loyalty:renderLoyalty\|loyalty:()=>" "$IDX"; then
  warn "index.html already has a loyalty route — skipping HTML patch"
else
  python3 - <<'PY'
import re
path = "src/main/resources/static/index.html"
with open(path) as f: content = f.read()

# 1) Icon — insert loyalty icon just before subPartners in the IC = { ... } object
icon_line = ' loyalty:\'<path d="M20 12V8H6a2 2 0 0 1-2-2c0-1.1.9-2 2-2h12v4"/><path d="M4 6v12c0 1.1.9 2 2 2h14v-4"/><path d="M18 12a2 2 0 0 0 0 4h4v-4Z"/>\','
new, n = re.subn(r'(\n subPartners:)', "\n" + icon_line + r"\1", content, count=1)
assert n == 1, "Could not locate IC.subPartners marker"
content = new

# 2) NAV.ADMIN g_ops — insert "loyalty" at the start of items list
new, n = re.subn(
    r'(group:"g_ops",items:\[)"merchants"',
    r'\1"loyalty","merchants"',
    content, count=1)
assert n == 1, "Could not locate NAV.ADMIN g_ops marker"
content = new

# 3) ROUTES — add loyalty route
new, n = re.subn(
    r'(const ROUTES=\{\n)',
    r'\1 loyalty:()=>{location.href="/loyalty.html";},\n',
    content, count=1)
assert n == 1, "Could not locate ROUTES marker"
content = new

# 4) i18n — add "loyalty" key to both FR and EN blocks.
# Each block has a `merchants:"..."` entry. The FR block appears first, EN second.
# Do it in two steps using distinct labels so the second match doesn't hit the
# already-patched first line.
FR_MERCH = 'merchants:"Commerçants",'
EN_MERCH = 'merchants:"Merchants",'
if FR_MERCH in content:
    content = content.replace(FR_MERCH, FR_MERCH + 'loyalty:"Fidélité Afriland",', 1)
if EN_MERCH in content:
    content = content.replace(EN_MERCH, EN_MERCH + 'loyalty:"Afriland Loyalty",', 1)

with open(path,"w") as f: f.write(content)
PY
  ok "index.html: nav entry, route, icon and i18n labels inserted"
fi

echo
ok "All patches applied. To roll back, restore the .bak-$STAMP files."
