import { icon, rodArt, floatArt, baitArt, bagArt, areaArt } from './icons.js';
import { fishArt } from './fishart.js';
import * as G from './game.js';
import { RARITY, SPECIES, RODS, BAITS, FLOATS, BAGS, MUTATIONS, AREAS } from './game.js';

const $ = (id) => document.getElementById(id);
const fmt = (n) => Math.round(n).toLocaleString('en-US');
let audio = null, hooks = {};

/* --------------------------------- toasts -------------------------------- */
export function toast(html, kind = '') {
  const el = document.createElement('div');
  el.className = 'toast ' + kind; el.innerHTML = html;
  $('toasts').appendChild(el);
  requestAnimationFrame(() => el.classList.add('in'));
  setTimeout(() => { el.classList.remove('in'); setTimeout(() => el.remove(), 400); }, 3600);
  while ($('toasts').children.length > 4) $('toasts').firstChild.remove();
}

/* ---------------------------------- HUD ---------------------------------- */
function renderHud() {
  const lp = G.levelProgress(), b = G.bait();
  $('hud').innerHTML = `
    <div class="chip" title="Coins">${icon('coin', 18)}<b>${fmt(G.state.coins)}</b></div>
    <div class="chip lv" title="Angler level">Lv <b>${lp.lv}</b><i class="xp"><u style="width:${Math.round(lp.frac * 100)}%"></u></i></div>
    <button class="chip" data-open="shop:sell" title="Your bag">${icon('bag', 18)}<b>${G.state.bag.length}</b>/${G.bagCap()}</button>
    <button class="chip" data-open="shop:bait" title="Equipped bait">${icon('worm', 18)}<span>${b ? b.name : 'No bait'}</span>${b ? `<b>${G.state.baits[b.id]}</b>` : ''}</button>`;
}

/* --------------------------------- modal --------------------------------- */
const TABS = {
  shop: [['sell', 'Sell'], ['rods', 'Rods'], ['bait', 'Bait'], ['floats', 'Floats'], ['bag', 'Bag']],
  journal: [['pond', 'Still Water'], ['maple', 'Maple Hollow']],
};
let cur = { panel: null, tab: null };

function stat(label, v, max = 0.4) { return `<div class="stat"><span>${label}</span><i><u style="width:${Math.min(100, v / max * 100)}%"></u></i></div>`; }

function renderSell() {
  const bag = G.state.bag, total = bag.reduce((a, f) => a + f.val, 0);
  if (!bag.length) return `<p class="empty">Your bag is empty. Cast a line, catch something, then come back here to sell it.</p>`;
  const rows = bag.map((f, i) => {
    const sp = G.SPECIES_BY_ID[f.id], m = MUTATIONS[f.mut];
    return `<li class="row">
      <div class="fa">${fishArt(sp, { mut: f.mut })}</div>
      <div class="rt"><b>${sp.name}</b><span class="meta" style="color:${RARITY[sp.rarity].color}"><span class="${RARITY[sp.rarity].cls || ''}">${RARITY[sp.rarity].name}</span>${m.label ? ` · <em style="color:${m.color}">${m.label}</em>` : ''} · ${f.kg.toFixed(2)} kg</span></div>
      <button class="buy" data-sell="${i}">${icon('coin', 16)} ${fmt(f.val)}</button></li>`;
  }).join('');
  return `<div class="sellbar"><span>${bag.length} fish in your bag · worth <b>${fmt(total)}</b></span><button class="primary" data-sellall>Sell everything</button></div><ul class="rows">${rows}</ul>`;
}
function rodCard(r) {
  const own = G.state.rods.includes(r.id), eq = G.state.rod === r.id, lock = G.level() < r.lvl, here = (r.area || 'pond') === (G.state.area || 'pond');
  const btn = eq ? `<button disabled>Equipped</button>` : own ? `<button data-eqrod="${r.id}">Equip</button>`
    : !here ? `<button disabled>${icon('map', 15)} Sold at ${AREAS[r.area || 'pond'].name}</button>`
    : lock ? `<button disabled>${icon('lock', 15)} Level ${r.lvl}</button>`
    : `<button class="buy" data-buyrod="${r.id}" ${G.state.coins < r.cost ? 'disabled' : ''}>${icon('coin', 16)} ${fmt(r.cost)}</button>`;
  return `<div class="card ${eq ? 'eq' : ''}"><div class="art">${rodArt(r.color, r.glow)}</div><b>${r.name}</b><span class="meta">${r.note}</span>
    <div class="stats">${stat('Control', r.ctrl, 0.25)}${stat('Speed', r.speed, 0.46)}${stat('Luck', r.luck, 1.6)}${stat('Power', r.prog, 0.66)}</div>${btn}</div>`;
}
function renderRods() {
  const here = (r) => (r.area || 'pond') === (G.state.area || 'pond');
  return `<div class="cards">${RODS.filter(here).concat(RODS.filter((r) => !here(r))).map(rodCard).join('')}</div>`;
}
function renderTravel() {
  const lv = G.level();
  return `<div class="cards two-col">${Object.values(AREAS).map((A) => {
    const locked = lv < A.lvl, cur = (G.state.area || 'pond') === A.id;
    const n = SPECIES.filter((s) => s.area === A.id && s.rarity < 6).length, got = SPECIES.filter((s) => s.area === A.id && G.state.caught[s.id]).length;
    const btn = cur ? '<button disabled>You are here</button>' : locked ? `<button disabled>${icon('lock', 15)} Reach level ${A.lvl}</button>` : `<button class="primary" data-travel="${A.id}">${icon('map', 16)} Travel</button>`;
    return `<div class="card ${cur ? 'eq' : ''}"><div class="art wide">${areaArt(A.id)}</div><b>${A.name}</b>
      <span class="meta">${A.id === 'maple' ? 'An autumn lake under a waterfall. New fish and the strongest rods.' : 'The quiet pond where it all began.'}</span>
      <span class="own">${got} of ${n} species caught</span>${btn}</div>`;
  }).join('')}</div><p class="foot">You can also walk to a waystone and press E, or press T anywhere.</p>`;
}
function renderBait() {
  return `<div class="cards">${BAITS.map((b) => {
    const n = G.state.baits[b.id] || 0, eq = G.state.bait === b.id && n > 0;
    return `<div class="card ${eq ? 'eq' : ''}"><div class="art">${baitArt(b.id)}</div><b>${b.name}</b><span class="meta">${b.note}</span>
      <span class="own">${n} in stock</span>
      <div class="two"><button class="buy" data-buybait="${b.id}" ${G.state.coins < b.cost ? 'disabled' : ''}>${icon('coin', 16)} ${b.cost} · ${b.pack} pack</button>
      <button data-eqbait="${b.id}" ${n ? '' : 'disabled'}>${eq ? 'In use' : 'Use'}</button></div></div>`;
  }).join('')}</div><p class="foot">One bait is used each cast. Equip the one you want before casting.</p>`;
}
function renderFloats() {
  return `<div class="cards small">${FLOATS.map((f) => {
    const own = G.state.floats.includes(f.id), eq = G.state.float === f.id;
    const btn = eq ? `<button disabled>Equipped</button>` : own ? `<button data-eqfloat="${f.id}">Equip</button>` : `<button class="buy" data-buyfloat="${f.id}" ${G.state.coins < f.cost ? 'disabled' : ''}>${icon('coin', 16)} ${f.cost}</button>`;
    return `<div class="card ${eq ? 'eq' : ''}"><div class="art">${floatArt(f.c1, f.c2, !!f.glow)}</div><b>${f.name}</b>${btn}</div>`;
  }).join('')}</div>`;
}
function renderBag() {
  const n = BAGS[G.state.bagLv + 1];
  return `<div class="cards one"><div class="card"><div class="art">${bagArt(G.state.bagLv)}</div><b>Bag size ${G.bagCap()}</b>
    <span class="meta">${G.state.bag.length} of ${G.bagCap()} used. When the bag is full, new catches sell on the spot at 60%.</span>
    ${n ? `<button class="buy" data-bagup ${G.state.coins < n.cost ? 'disabled' : ''}>${icon('coin', 16)} ${fmt(n.cost)} · up to ${n.cap}</button>` : '<button disabled>Largest bag</button>'}</div></div>`;
}
function renderJournal(tab) {
  const found = SPECIES.filter((s) => G.state.caught[s.id]).length, st = G.state.stats;
  const list = SPECIES.filter((s) => s.area === tab);
  const cards = list.map((s) => {
    const c = G.state.caught[s.id], r = RARITY[s.rarity];
    const muts = c ? Object.entries(c.mut).map(([k, v]) => `<em style="color:${MUTATIONS[k].color}">${MUTATIONS[k].label} ×${v}</em>`).join(' ') : '';
    return `<li class="fcard ${c ? '' : 'unk'}" style="--rc:${r.color}"><div class="ficon">${fishArt(s, { unknown: !c })}</div>
      <b>${c ? s.name : '???'}</b><span class="rar ${r.cls || ''}">${r.name}</span>
      <span class="meta">${c ? `Caught ${c.n} · best ${c.best.toFixed(2)} kg` : (s.hint === 'secret' ? `Appears after 100 catches (${Math.min(100, st.fish)}/100)` : s.hint === 'night' ? 'Bites at night' : s.hint === 'dusk' ? 'Bites at dusk' : s.hint === 'rain' ? 'Bites in the rain' : s.hint === 'day' ? 'Bites by day' : 'Not caught yet')}</span>${muts ? `<span class="meta">${muts}</span>` : ''}</li>`;
  }).join('');
  return `<div class="jstats"><div><b>${found}</b><span>of ${SPECIES.length} species</span></div><div><b>${fmt(st.fish)}</b><span>fish caught</span></div><div><b>${fmt(st.casts)}</b><span>casts</span></div><div><b>${fmt(st.earned)}</b><span>coins earned</span></div></div><ul class="jgrid">${cards}</ul>`;
}
function seg(name, opts, val) { return `<div class="seg" data-seg="${name}">${opts.map(([v, l]) => `<button data-v="${v}" class="${v === val ? 'on' : ''}">${l}</button>`).join('')}</div>`; }
function renderSettings() {
  return `<div class="set"><div class="sr"><div><b>Graphics quality</b><span class="meta">Auto adjusts to keep the game smooth. Pick Low if it stutters.</span></div>${seg('quality', [['auto', 'Auto'], ['low', 'Low'], ['med', 'Medium'], ['high', 'High']], G.state.quality)}</div>
    <div class="sr"><div><b>Weather</b><span class="meta">Rain brings stormy fish and a calmer light.</span></div>${seg('weather', [['auto', 'Auto'], ['clear', 'Clear'], ['rain', 'Rain']], G.state.weather)}</div>
    <div class="sr"><div><b>Length of a day</b><span class="meta">Day and night pass on their own. Pause from the clock in the toolbar.</span></div>${seg('dayLen', [['6', '6 min'], ['12', '12 min'], ['24', '24 min'], ['48', '48 min']], String(G.state.dayLen))}</div>
    <div class="sr"><div><b>Look sensitivity</b><span class="meta">How fast the view turns with the mouse or finger.</span></div><input id="sens" type="range" min="0.3" max="2.5" step="0.05" value="${G.state.sens || 1}" aria-label="Look sensitivity"></div>
    <div class="sr"><div><b>Volume</b><span class="meta">Applies when sound is on.</span></div><input id="vol" type="range" min="0" max="1" step="0.05" value="${G.state.volume}" aria-label="Volume"></div>
    <div class="sr"><div><b>Tutorial</b><span class="meta">Walk through casting and the catch bar again.</span></div><button data-replay>Replay</button></div>
    <div class="sr"><div><b>Erase progress</b><span class="meta">Removes coins, fish, gear and your journal.</span></div><button class="danger" data-reset>Erase</button></div></div>`;
}

function render() {
  if (!cur.panel) return;
  const title = { shop: AREAS[G.state.area || 'pond'].shop, journal: 'Journal', settings: 'Settings', travel: 'Travel' }[cur.panel];
  const tabs = (TABS[cur.panel] || []).map(([id, l]) => `<button class="tab ${cur.tab === id ? 'on' : ''}" data-tab="${id}">${l}</button>`).join('');
  let body = '';
  if (cur.panel === 'shop') body = { sell: renderSell, rods: renderRods, bait: renderBait, floats: renderFloats, bag: renderBag }[cur.tab]();
  else if (cur.panel === 'journal') body = renderJournal(cur.tab);
  else if (cur.panel === 'travel') body = renderTravel();
  else body = renderSettings();
  const head = cur.panel === 'shop' ? `<span class="wallet">${icon('coin', 18)} <b>${fmt(G.state.coins)}</b> <i>Lv ${G.level()}</i></span>` : '';
  $('mbox').innerHTML = `<header><h2>${title}</h2>${head}<button class="x" data-close aria-label="Close">${icon('close', 20)}</button></header>
    ${tabs ? `<nav class="tabs">${tabs}</nav>` : ''}<div class="mbody">${body}</div>`;
}
export function open(panel, tab) {
  cur = { panel, tab: tab || (panel === 'journal' ? (G.state.area || 'pond') : TABS[panel] ? TABS[panel][0][0] : null) };
  $('modal').hidden = false; render(); hooks.onOpen && hooks.onOpen(panel);
}
export function close() { cur = { panel: null, tab: null }; $('modal').hidden = true; hooks.onClose && hooks.onClose(); }
export const isOpen = () => !$('modal').hidden;

function onClick(e) {
  const t = e.target.closest('button, [data-open]'); if (!t) return;
  const d = t.dataset, sfx = (k) => audio && audio.sfx(k);
  if (d.close !== undefined) return close();
  if (d.tab) { cur.tab = d.tab; sfx('click'); return render(); }
  if (d.sell !== undefined) { const v = G.sellFish(+d.sell); if (v) { sfx('coin'); toast(`Sold for ${fmt(v)} coins`, 'good'); } return; }
  if (d.sellall !== undefined) { const v = G.sellAll(); if (v) { sfx('coin'); toast(`Sold everything for ${fmt(v)} coins`, 'good'); } return; }
  if (d.buyrod) { const r = RODS.find((x) => x.id === d.buyrod); if (G.buyRod(r)) { sfx('buy'); toast(`${r.name} bought and equipped`, 'good'); hooks.onGear && hooks.onGear(); } return; }
  if (d.eqrod) { G.equipRod(d.eqrod); sfx('click'); hooks.onGear && hooks.onGear(); return; }
  if (d.buybait) { const b = BAITS.find((x) => x.id === d.buybait); if (G.buyBait(b)) { sfx('buy'); toast(`${b.pack} × ${b.name}`, 'good'); } return; }
  if (d.eqbait) { G.equipBait(d.eqbait); sfx('click'); return; }
  if (d.buyfloat) { const f = FLOATS.find((x) => x.id === d.buyfloat); if (G.buyFloat(f)) { sfx('buy'); toast(`${f.name} float equipped`, 'good'); hooks.onGear && hooks.onGear(); } return; }
  if (d.eqfloat) { G.equipFloat(d.eqfloat); sfx('click'); hooks.onGear && hooks.onGear(); return; }
  if (d.bagup !== undefined) { if (G.upgradeBag()) { sfx('buy'); toast(`Bag upgraded to ${G.bagCap()} slots`, 'good'); } return; }
  if (d.travel) { close(); hooks.onTravel && hooks.onTravel(d.travel); return; }
  if (d.replay !== undefined) { close(); hooks.onReplay && hooks.onReplay(); return; }
  if (d.reset !== undefined) { if (t.dataset.sure) { G.resetAll(); close(); toast('Progress erased'); location.reload(); } else { t.dataset.sure = '1'; t.textContent = 'Tap again to confirm'; } return; }
  if (d.v && t.parentElement.dataset.seg) {
    const key = t.parentElement.dataset.seg; G.state[key] = d.v; G.commit(); hooks.onSetting && hooks.onSetting(key, d.v); render(); return;
  }
  if (d.open) { const [p, tb] = d.open.split(':'); open(p, tb); }
}

export function initUI(opts) {
  audio = opts.audio; hooks = opts.hooks || {};
  document.querySelectorAll('[data-i]').forEach((el) => { el.innerHTML = icon(el.dataset.i, +el.dataset.s || 20); });
  document.addEventListener('click', onClick);
  $('modal').addEventListener('pointerdown', (e) => { if (e.target === $('modal')) close(); });
  $('modal').addEventListener('input', (e) => { if (e.target.id === 'vol') { G.state.volume = +e.target.value; G.saveQuiet(); audio.setVolume(G.state.volume); }
    if (e.target.id === 'sens') { G.state.sens = +e.target.value; G.saveQuiet(); } });
  G.onChange(() => { renderHud(); if (!$('modal').hidden) render(); });
  renderHud();
  addEventListener('keydown', (e) => {
    if (e.key === 'Escape' && isOpen()) { close(); return; }
    if (e.target && /INPUT|TEXTAREA/.test(e.target.tagName)) return;
    if (e.key === 'j' || e.key === 'J') isOpen() && cur.panel === 'journal' ? close() : open('journal');
    if (e.key === 'b' || e.key === 'B') isOpen() && cur.panel === 'shop' ? close() : open('shop');
  });
}
