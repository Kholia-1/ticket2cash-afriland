async function renderBusinessDashboard(){
  const c=document.getElementById("content");
  c.innerHTML=`<div class="page">${head(t("businessDashboard"),t("bd_sub"),refreshBtn())}
    <div class="kpi-grid" id="bdKpis">${kpiSkeletons(4)}</div>
    <div class="grid-2">
      <div class="panel">
        <div class="panel-head">
          <h3>${t("bd_top_clients")}</h3>
          <div class="seg" id="bdTopSeg" style="display:flex;gap:2px;background:var(--surface-2);padding:3px;border-radius:8px">
            <button class="seg-btn active" data-t="ALL" onclick="bdSwitchTop('ALL')" style="padding:5px 10px;font-size:12px;font-weight:600;border-radius:6px;background:var(--surface);color:var(--brand)">${t("bd_all")}</button>
            <button class="seg-btn" data-t="COMPANY" onclick="bdSwitchTop('COMPANY')" style="padding:5px 10px;font-size:12px;font-weight:600;border-radius:6px;color:var(--text-2)">${t("bd_companies")}</button>
            <button class="seg-btn" data-t="INDIVIDUAL" onclick="bdSwitchTop('INDIVIDUAL')" style="padding:5px 10px;font-size:12px;font-weight:600;border-radius:6px;color:var(--text-2)">${t("bd_individuals")}</button>
          </div>
        </div>
        <div class="panel-body" id="bdTopClients">${skLines(6)}</div>
      </div>
      <div class="panel">
        <div class="panel-head"><h3>${t("claim_dist")}</h3></div>
        <div class="panel-body" id="bdClaimChart">${skLines(4)}</div>
      </div>
    </div></div>`;
  try{
    const [biz, loy] = await Promise.all([
      api("/api/business-dashboard/summary"),
      api("/api/loyalty/stats").catch(()=>({}))
    ]);
    const K = [
      {icon:"payments",label:t("kpi_cashback"),val:money(biz.totalCashbackPaid||0),sub:`${num(biz.totalPayments||0)} ${t("payments_lc")}`},
      {icon:"merchants",label:t("kpi_merchants"),val:num(biz.activeMerchants||0),sub:`${num(biz.activeCampaigns||0)} ${t("bd_campaigns_active")}`},
      {icon:"loyalty",label:t("bd_loyalty_clients"),val:`${num(loy.individuals||0)} + ${num(loy.companies||0)}`,sub:t("bd_indiv_comp")},
      {icon:"payments",label:t("bd_loyalty_paid"),val:money(loy.totalPaidCashback||0),sub:`${num(loy.creditedBatches||0)} ${t("bd_batches_credited")}`}
    ];
    document.getElementById("bdKpis").innerHTML = K.map(kpiCard).join(""); animateCounters();

    const dist=[{k:t("leg_submitted"),v:biz.submittedClaims||0,c:"#1553B8"},{k:t("leg_approved"),v:biz.approvedClaims||0,c:"#127A45"},
      {k:t("leg_paid"),v:biz.paidClaims||0,c:"#0E9F6E"},{k:t("leg_rejected"),v:biz.rejectedClaims||0,c:"#B42318"}];
    document.getElementById("bdClaimChart").innerHTML=donut(dist);

    await bdLoadTopClients("ALL");
  }catch(e){toast(t("err_dash"),"err");console.error(e);}
}

async function bdLoadTopClients(type){
  const el = document.getElementById("bdTopClients");
  if(!el) return;
  el.innerHTML = skLines(6);
  try{
    const rows = await api(`/api/loyalty/clients/top?type=${encodeURIComponent(type)}&limit=10`);
    if(!rows || rows.length === 0){
      el.innerHTML = emptyMini(t("bd_no_clients"));
      return;
    }
    el.innerHTML = `<div class="table-scroll"><table style="min-width:auto"><thead>
      <tr><th>${t("bd_rank")}</th><th>${t("bd_name")}</th><th>${t("bd_type")}</th>
          <th style="text-align:right">${t("bd_volume")}</th>
          <th style="text-align:right">${t("bd_cashback")}</th>
          <th></th></tr></thead>
      <tbody>${rows.map((r,i)=>`<tr>
        <td class="strong">${i+1}</td>
        <td><strong>${esc(r.fullName||r.accountNumber)}</strong>
            <br><span class="mono" style="font-size:11px;color:var(--muted)">${esc(r.accountNumber)}</span></td>
        <td>${bdTypeBadge(r.entityType)}</td>
        <td style="text-align:right" class="mono strong">${money(r.lifetimeVolume||0)}</td>
        <td style="text-align:right" class="mono strong" style="color:var(--brand)">${money(r.lifetimeCashback||0)}</td>
        <td style="text-align:right">
          <button class="btn btn-ghost" style="padding:4px 10px;font-size:11px" onclick="bdOpenClient(${r.id})">${t("bd_view")}</button>
          <button class="btn btn-primary" style="padding:4px 10px;font-size:11px;margin-left:4px" onclick="bdReward('${esc(r.accountNumber)}')">${t("bd_reward")}</button>
        </td></tr>`).join("")}</tbody></table></div>`;
  }catch(e){
    el.innerHTML = emptyMini(t("err_load"));
    console.error(e);
  }
}

function bdSwitchTop(type){
  document.querySelectorAll("#bdTopSeg .seg-btn").forEach(b=>{
    const active = b.dataset.t === type;
    b.classList.toggle("active", active);
    b.style.background = active ? "var(--surface)" : "transparent";
    b.style.color = active ? "var(--brand)" : "var(--text-2)";
  });
  bdLoadTopClients(type);
}

function bdTypeBadge(t){
  const et = (t||"INDIVIDUAL").toUpperCase();
  if(et === "COMPANY") return `<span class="badge" style="background:var(--info-bg);color:var(--info);font-size:10px;padding:2px 7px;border-radius:12px;font-weight:600">🏢 ENTREPRISE</span>`;
  return `<span class="badge" style="background:var(--ok-bg);color:var(--ok);font-size:10px;padding:2px 7px;border-radius:12px;font-weight:600">👤 PARTICULIER</span>`;
}

async function bdOpenClient(id){
  try{
    const clients = await api("/api/loyalty/clients");
    const c = clients.find(x => x.id === id);
    if(!c){ toast("Client introuvable","err"); return; }
    openModal(`${c.fullName||c.accountNumber}`, `
      <div style="display:grid;grid-template-columns:1fr 1fr;gap:14px 24px;font-size:13px">
        <div><div style="font-size:11px;color:var(--muted);text-transform:uppercase;letter-spacing:.5px">Compte</div>
             <div class="mono strong">${esc(c.accountNumber)}</div></div>
        <div><div style="font-size:11px;color:var(--muted);text-transform:uppercase;letter-spacing:.5px">Type</div>
             <div>${bdTypeBadge(c.entityType)}</div></div>
        <div><div style="font-size:11px;color:var(--muted);text-transform:uppercase;letter-spacing:.5px">Tier</div>
             <div class="strong">${esc(c.tier||"CLASSIC")}</div></div>
        <div><div style="font-size:11px;color:var(--muted);text-transform:uppercase;letter-spacing:.5px">Ville</div>
             <div>${esc(c.city||"—")}</div></div>
        <div><div style="font-size:11px;color:var(--muted);text-transform:uppercase;letter-spacing:.5px">Téléphone</div>
             <div class="mono">${esc(c.phone||"—")}</div></div>
        <div><div style="font-size:11px;color:var(--muted);text-transform:uppercase;letter-spacing:.5px">Email</div>
             <div>${esc(c.email||"—")}</div></div>
        <div><div style="font-size:11px;color:var(--muted);text-transform:uppercase;letter-spacing:.5px">Volume total</div>
             <div class="strong mono">${money(c.lifetimeVolume||0)}</div></div>
        <div><div style="font-size:11px;color:var(--muted);text-transform:uppercase;letter-spacing:.5px">Cashback reçu</div>
             <div class="strong mono" style="color:var(--brand)">${money(c.lifetimeCashback||0)}</div></div>
      </div>
      <div style="display:flex;gap:8px;justify-content:flex-end;margin-top:20px;padding-top:16px;border-top:1px solid var(--border)">
        <button class="btn btn-ghost" onclick="closeModal()">Fermer</button>
        <button class="btn btn-primary" onclick="closeModal();bdReward('${esc(c.accountNumber)}')">Récompenser</button>
      </div>`);
  }catch(e){ toast(t("err_load"),"err"); }
}

function bdReward(accountNumber){
  // Send them to the loyalty page with the account pre-filtered so they can add a rule / upload
  location.href = "/loyalty.html#account=" + encodeURIComponent(accountNumber);
}
