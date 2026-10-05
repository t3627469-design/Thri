import * as THREE from 'three';

/* =====================================================================
   Fishing game: cast from the dock, wait for a bite, hook it, then play the
   catch-bar minigame (bottom half of the screen). Hold = zone moves right,
   release = it falls back left. Keep the zone over the fish to fill the bar.
   ===================================================================== */

const $ = (id) => document.getElementById(id);
const rand = (a, b) => a + Math.random() * (b - a);
const clamp = (x, a, b) => Math.max(a, Math.min(b, x));

const SPECIES = [
  { id: 'minnow', name: 'Pond Minnow', rarity: 0, w: [0.05, 0.25], val: [4, 8], diff: 0.18, c: ['#c9d3d8', '#8fa3ad'], weight: 40 },
  { id: 'bluegill', name: 'Bluegill', rarity: 0, w: [0.2, 0.7], val: [6, 12], diff: 0.28, c: ['#4f8fbf', '#f0a23a'], weight: 32 },
  { id: 'perch', name: 'Striped Perch', rarity: 0, w: [0.3, 1.1], val: [8, 14], diff: 0.34, c: ['#7ba33c', '#d9772b'], weight: 26 },
  { id: 'goldcarp', name: 'Golden Carp', rarity: 1, w: [0.8, 3.2], val: [24, 40], diff: 0.45, c: ['#f2b632', '#f7d56b'], weight: 12 },
  { id: 'trout', name: 'Rainbow Trout', rarity: 1, w: [0.6, 2.4], val: [22, 36], diff: 0.5, c: ['#d98ba6', '#6fc2c9'], weight: 10 },
  { id: 'koi', name: 'Pond Koi', rarity: 2, w: [1.5, 5], val: [70, 110], diff: 0.62, c: ['#ff6a2a', '#fff3e6'], weight: 5 },
  { id: 'moonpike', name: 'Moonlit Pike', rarity: 2, w: [2, 6], val: [90, 130], diff: 0.68, c: ['#9fb7ff', '#e8efff'], weight: 2.5, hint: 'night' },
  { id: 'embercarp', name: 'Ember Carp', rarity: 3, w: [2, 5.5], val: [180, 240], diff: 0.78, c: ['#ff4a1c', '#ffb347'], weight: 1.2, hint: 'dusk' },
  { id: 'jadedragon', name: 'Jade Dragonfish', rarity: 4, w: [6, 14], val: [500, 700], diff: 0.92, c: ['#2fd6a1', '#f4e29a'], weight: 0.35 },
];
const RARITY = [['Common', '#b8c4c9'], ['Uncommon', '#7fd36b'], ['Rare', '#58a6ff'], ['Epic', '#b987ff'], ['Legendary', '#ffc857']];
const ROD_COST = [0, 60, 150, 350, 800];   // cost to reach level index+1
const MAX_ROD = 5;
const SAVE_KEY = 'stillwater.fishing.v1';

export function fishSVG(c1, c2, unknown = false) {
  const a = unknown ? '#0b1210' : c1, b = unknown ? '#0b1210' : c2;
  return `<svg viewBox="0 0 64 32" aria-hidden="true"><path d="M2 16C14 2 38 2 50 16C38 30 14 30 2 16Z" fill="${a}"/><path d="M48 16L62 5L58 16L62 27Z" fill="${b}"/>${unknown ? '' : `<circle cx="13" cy="13" r="2.2" fill="#10161a"/><path d="M26 9C30 14 30 18 26 23" stroke="${b}" stroke-opacity=".55" fill="none" stroke-width="2"/>`}</svg>`;
}

function shoreR(th) {
  return 8.2 + 1.6 * Math.sin(2 * th + 0.6) + 0.9 * Math.sin(3 * th + 2.0) + 0.5 * Math.sin(5 * th + 1.0);
}

export function initFishing(ctx) {
  const { scene, camera, controls, addRipple, hAt, WATER_Y, audio, getPreset } = ctx;

  /* ------------------------------ save data ------------------------------ */
  let save = { coins: 0, rod: 1, caught: {} };
  try { const s = JSON.parse(localStorage.getItem(SAVE_KEY) || 'null'); if (s && typeof s === 'object') save = { ...save, ...s }; } catch (e) { /* storage unavailable */ }
  const persist = () => { try { localStorage.setItem(SAVE_KEY, JSON.stringify(save)); } catch (e) { /* ignore */ } };

  /* --------------------------- dock / rod placement ---------------------- */
  const TH = -Math.PI / 2 - 0.35;               // same angle as the dock in build_pond.py
  const R = shoreR(TH);
  const sBx = R * Math.cos(TH), sBy = R * Math.sin(TH);
  const uBx = -Math.cos(TH), uBy = -Math.sin(TH);
  const stand = new THREE.Vector3(sBx + uBx * 4.7, 0.22, -(sBy + uBy * 4.7));   // blender (x,y) -> three (x,-y)
  const fwd = new THREE.Vector3(uBx, 0, -uBy).normalize();
  const side = new THREE.Vector3(-fwd.z, 0, fwd.x);

  const rod = new THREE.Group();
  rod.position.copy(stand).add(new THREE.Vector3(0, 0.25, 0)).addScaledVector(side, 0.35);
  const rodDir = fwd.clone().multiplyScalar(Math.cos(0.75)).add(new THREE.Vector3(0, Math.sin(0.75), 0)).normalize();
  rod.quaternion.setFromUnitVectors(new THREE.Vector3(0, 1, 0), rodDir);
  const mat = (c, r = 0.6) => new THREE.MeshStandardMaterial({ color: c, roughness: r });
  const shaft = new THREE.Mesh(new THREE.CylinderGeometry(0.011, 0.028, 3.2, 8).translate(0, 1.6, 0), mat(0x3a2a1c, 0.5));
  const grip = new THREE.Mesh(new THREE.CylinderGeometry(0.034, 0.034, 0.5, 10).translate(0, 0.25, 0), mat(0xc9a36a, 0.9));
  const reel = new THREE.Mesh(new THREE.CylinderGeometry(0.06, 0.06, 0.05, 14).rotateZ(Math.PI / 2).translate(0.06, 0.58, 0), mat(0xb8b8c0, 0.3));
  rod.add(shaft, grip, reel);
  rod.traverse((o) => { if (o.isMesh) o.castShadow = true; });
  scene.add(rod);
  const tip = new THREE.Vector3();
  const tipLocal = new THREE.Vector3(0, 3.2, 0);

  // bait box on the deck, just for charm
  const box = new THREE.Mesh(new THREE.BoxGeometry(0.4, 0.22, 0.28), mat(0x5b7a8c, 0.7));
  box.position.copy(stand).addScaledVector(side, -0.7).add(new THREE.Vector3(0, 0.22, 0)); box.rotation.y = 0.4; box.castShadow = true;
  scene.add(box);

  /* ------------------------------ bobber & line -------------------------- */
  const bobber = new THREE.Group();
  const bTop = new THREE.Mesh(new THREE.SphereGeometry(0.085, 16, 10, 0, Math.PI * 2, 0, Math.PI / 2), new THREE.MeshStandardMaterial({ color: 0xff4630, emissive: 0xff2a10, emissiveIntensity: 0.6, roughness: 0.4 }));
  const bBot = new THREE.Mesh(new THREE.SphereGeometry(0.085, 16, 10, 0, Math.PI * 2, Math.PI / 2, Math.PI / 2), mat(0xffffff, 0.4));
  const bAnt = new THREE.Mesh(new THREE.CylinderGeometry(0.008, 0.008, 0.16, 6).translate(0, 0.16, 0), mat(0xff4630, 0.4));
  bobber.add(bTop, bBot, bAnt);
  bobber.visible = false;
  scene.add(bobber);

  const LINE_N = 28;
  const lineGeo = new THREE.BufferGeometry().setFromPoints(Array.from({ length: LINE_N }, () => new THREE.Vector3()));
  const line = new THREE.Line(lineGeo, new THREE.LineBasicMaterial({ color: 0xffffff, transparent: true, opacity: 0.8 }));
  line.frustumCulled = false; line.visible = false;
  scene.add(line);

  function updateLine(a, b, slack) {
    const p = lineGeo.attributes.position;
    for (let i = 0; i < LINE_N; i++) {
      const k = i / (LINE_N - 1);
      p.setXYZ(i, a.x + (b.x - a.x) * k, a.y + (b.y - a.y) * k - Math.sin(Math.PI * k) * slack, a.z + (b.z - a.z) * k);
    }
    p.needsUpdate = true;
  }

  /* ---------------------------------- UI --------------------------------- */
  const el = {
    status: $('status'), mini: $('mini'), miniName: $('miniName'), miniHint: $('miniHint'),
    track: $('track'), zone: $('zone'), fish: $('fishIcon'), fill: $('progFill'),
    card: $('card'), journal: $('journal'), cast: $('cast'), coins: $('coins'), rodLv: $('rodLv'),
    grid: $('jgrid'), upgrade: $('upgrade'), jcoins: $('jcoins'),
  };
  let statusTimer = 0;
  function say(text, sticky = false) {
    el.status.textContent = text; el.status.classList.add('show');
    clearTimeout(statusTimer);
    if (!sticky) statusTimer = setTimeout(() => el.status.classList.remove('show'), 2600);
  }
  function hud() { el.coins.textContent = save.coins; el.rodLv.textContent = save.rod; }
  const speciesById = Object.fromEntries(SPECIES.map((s) => [s.id, s]));

  function renderJournal() {
    el.jcoins.textContent = save.coins;
    const total = SPECIES.length, found = SPECIES.filter((s) => save.caught[s.id]).length;
    $('jfound').textContent = `${found} / ${total} species`;
    el.grid.innerHTML = SPECIES.map((s) => {
      const c = save.caught[s.id], [rn, rc] = RARITY[s.rarity];
      return `<li class="fcard${c ? '' : ' unk'}" style="--rc:${rc}">
        <div class="ficon">${fishSVG(s.c[0], s.c[1], !c)}</div>
        <b>${c ? s.name : '???'}</b>
        <span class="rar">${rn}</span>
        <span class="meta">${c ? `×${c.n} · best ${c.best.toFixed(2)} kg` : (s.hint === 'night' ? 'Bites at night' : s.hint === 'dusk' ? 'Bites at dusk' : 'Not caught yet')}</span>
      </li>`;
    }).join('');
    if (save.rod >= MAX_ROD) { el.upgrade.textContent = 'Rod fully upgraded'; el.upgrade.disabled = true; }
    else { const cost = ROD_COST[save.rod]; el.upgrade.textContent = `Upgrade rod to level ${save.rod + 1} · ${cost} coins`; el.upgrade.disabled = save.coins < cost; }
  }
  function toggleJournal(force) {
    const open = force ?? el.journal.hidden;
    el.journal.hidden = !open;
    if (open) renderJournal();
  }
  $('journalBtn').addEventListener('click', () => toggleJournal());
  $('jclose').addEventListener('click', () => toggleJournal(false));
  el.upgrade.addEventListener('click', () => {
    if (save.rod >= MAX_ROD || save.coins < ROD_COST[save.rod]) return;
    save.coins -= ROD_COST[save.rod]; save.rod++; persist(); hud(); renderJournal(); audio.sfx('up');
  });

  /* ------------------------------ game state ----------------------------- */
  const f = { state: 'idle', t: 0, wait: 0, bite: 0, target: new THREE.Vector3(), from: new THREE.Vector3(), sp: null, ripT: 0, cam: null, fishView: false };
  const m = { c: 0.5, vel: 0, fp: 0.5, ftarget: 0.5, ftimer: 0, prog: 0.3, zw: 0.25, hold: false, face: 1, dart: 0 };
  const bob = new THREE.Vector3();

  const btnLabel = { idle: '🎣 Cast', cast: '…', wait: 'Reel in', bite: 'HOOK IT!', mini: 'Hold', reel: '…' };
  function setState(s) {
    f.state = s; f.t = 0;
    el.cast.textContent = btnLabel[s] || '🎣 Cast';
    el.cast.classList.toggle('hot', s === 'bite');
    document.body.dataset.fish = s;
  }

  function pickSpecies() {
    const p = getPreset();
    const pool = SPECIES.map((s) => {
      let w = s.weight;
      if (s.hint === 'night' && p === 'night') w *= 8;
      else if (s.hint === 'night') w *= 0.2;
      if (s.hint === 'dusk' && (p === 'twilight' || p === 'golden')) w *= p === 'twilight' ? 7 : 3;
      else if (s.hint === 'dusk') w *= 0.3;
      return [s, w * (1 + (save.rod - 1) * (s.rarity >= 2 ? 0.12 : 0))];
    });
    const tot = pool.reduce((a, [, w]) => a + w, 0);
    let r = Math.random() * tot;
    for (const [s, w] of pool) { if ((r -= w) <= 0) return s; }
    return SPECIES[0];
  }

  function chooseTarget(aim) {
    const ok = (v) => hAt(v.x, v.z) < WATER_Y - 0.45 && Math.hypot(v.x, v.z) < 6.2;
    if (aim) { const d = aim.distanceTo(stand); if (ok(aim) && d > 2.2) return aim.clone().setY(WATER_Y); }
    for (let i = 0; i < 40; i++) {
      const v = stand.clone().addScaledVector(fwd, rand(4.2, 9)).addScaledVector(side, rand(-3.4, 3.4)); v.y = WATER_Y;
      if (ok(v)) return v;
    }
    return new THREE.Vector3(0, WATER_Y, 0);
  }

  function enterFishView() {
    if (f.fishView) return;
    f.fishView = true;
    controls.autoRotate = false; $('orbit').classList.remove('on');
    f.cam = { t: 0, p0: camera.position.clone(), t0: controls.target.clone(), p1: stand.clone().addScaledVector(fwd, -2.7).setY(2.15), t1: stand.clone().addScaledVector(fwd, 6.5).setY(-0.1) };
  }
  function resetCamera() {
    f.fishView = false;
    f.cam = { t: 0, p0: camera.position.clone(), t0: controls.target.clone(), p1: new THREE.Vector3(-9, 5.2, 17.5), t1: new THREE.Vector3(0, 0.9, 0) };
  }

  /* -------------------------------- actions ------------------------------ */
  function cast(aim) {
    if (f.state !== 'idle') return;
    enterFishView();
    el.card.classList.remove('show');
    f.target = chooseTarget(aim);
    f.from.copy(tip);
    bobber.visible = line.visible = true;
    setState('cast');
    say('Casting…');
    audio.sfx('cast');
  }
  function reelIn(msg, sfx) {
    if (msg) say(msg);
    if (sfx) audio.sfx(sfx);
    el.mini.hidden = true; controls.enabled = true; m.hold = false;
    f.from.copy(bob);
    setState('reel');
  }
  function hook() {
    f.sp = f.sp || pickSpecies();
    const s = f.sp;
    m.zw = clamp(0.27 - s.diff * 0.11 + (save.rod - 1) * 0.032, 0.11, 0.42);
    m.c = 0.5; m.vel = 0; m.fp = 0.5; m.ftarget = 0.5; m.ftimer = 0; m.prog = 0.32; m.hold = false;
    el.zone.style.width = (m.zw * 100) + '%';
    el.fish.innerHTML = fishSVG(s.c[0], s.c[1]);
    el.miniName.textContent = 'Hooked something…';
    el.miniName.style.removeProperty('color');
    el.mini.hidden = false; el.status.classList.remove('show');
    controls.enabled = false;
    addRipple(f.target.x, f.target.z, 1.2);
    audio.sfx('hook');
    setState('mini');
  }
  function press() {
    if (f.state === 'idle') cast(null);
    else if (f.state === 'wait') reelIn('Too early. Wait for the bobber to dip.', 'miss');
    else if (f.state === 'bite') hook();
    else if (f.state === 'mini') m.hold = true;
  }
  function release() { m.hold = false; }

  function finish(win) {
    const s = f.sp;
    if (!win) { f.sp = null; reelIn('It slipped away…', 'miss'); return; }
    const kg = +(s.w[0] + Math.pow(Math.random(), 1.6) * (s.w[1] - s.w[0])).toFixed(2);
    const mid = (s.w[0] + s.w[1]) / 2;
    const value = Math.round(rand(s.val[0], s.val[1]) * (0.85 + 0.3 * kg / mid));
    const rec = save.caught[s.id];
    const isNew = !rec;
    save.caught[s.id] = { n: (rec ? rec.n : 0) + 1, best: Math.max(rec ? rec.best : 0, kg) };
    save.coins += value; persist(); hud();
    const [rn, rc] = RARITY[s.rarity];
    el.card.style.setProperty('--rc', rc);
    el.card.innerHTML = `<div class="cicon">${fishSVG(s.c[0], s.c[1])}</div>
      <div class="ctext"><span class="rar">${rn}${isNew ? ' · New species' : ''}</span><b>${s.name}</b><span class="meta">${kg.toFixed(2)} kg · +${value} coins</span></div>`;
    el.card.classList.add('show');
    clearTimeout(finish.t); finish.t = setTimeout(() => el.card.classList.remove('show'), 4200);
    audio.sfx(s.rarity >= 3 ? 'legend' : 'win');
    f.sp = null;
    reelIn('', null);
    say('Press Space to cast again');
  }

  /* -------------------------------- update ------------------------------- */
  function update(dt, t) {
    // camera glide into the fishing view
    if (f.cam) {
      f.cam.t = Math.min(1, f.cam.t + dt / 1.8);
      const e = 1 - Math.pow(1 - f.cam.t, 3);
      camera.position.lerpVectors(f.cam.p0, f.cam.p1, e);
      controls.target.lerpVectors(f.cam.t0, f.cam.t1, e);
      if (f.cam.t >= 1) f.cam = null;
    }
    // rod tip in world space
    rod.updateMatrixWorld();
    tip.copy(tipLocal).applyMatrix4(rod.matrixWorld);

    f.t += dt;
    let slack = 0.5;
    switch (f.state) {
      case 'idle':
        break;
      case 'cast': {
        const k = Math.min(1, f.t / 0.85), e = k * k * (3 - 2 * k);
        bob.lerpVectors(f.from, f.target, e); bob.y += Math.sin(Math.PI * k) * 2.4;
        slack = 0.1;
        if (k >= 1) {
          addRipple(f.target.x, f.target.z, 1.1); audio.sfx('splash');
          f.wait = rand(2.4, 6.5) * (1 - (save.rod - 1) * 0.1); f.sp = null; f.ripT = 1.2;
          setState('wait'); say('Wait for the bobber to dip…', true);
        }
        break;
      }
      case 'wait': {
        bob.copy(f.target); bob.y = WATER_Y + 0.03 + Math.sin(t * 2.1) * 0.012;
        f.ripT -= dt; if (f.ripT < 0) { f.ripT = rand(1.6, 3.2); addRipple(f.target.x + rand(-0.2, 0.2), f.target.z + rand(-0.2, 0.2), 0.25); }
        if (f.t > f.wait) { f.sp = pickSpecies(); f.bite = 1.35; setState('bite'); say('Bite! Press Space now!', true); audio.sfx('bite'); addRipple(f.target.x, f.target.z, 1.4); }
        break;
      }
      case 'bite': {
        bob.copy(f.target); bob.y = WATER_Y - 0.06 + Math.sin(t * 38) * 0.03;
        slack = 0.15;
        f.ripT -= dt; if (f.ripT < 0) { f.ripT = 0.25; addRipple(f.target.x, f.target.z, 0.5); }
        f.bite -= dt;
        if (f.bite <= 0) { f.sp = null; reelIn('Too slow, it got away.', 'miss'); }
        break;
      }
      case 'mini': {
        const s = f.sp;
        // player zone physics: hold = push right, release = fall back left
        m.vel += (m.hold ? 3.1 : -2.5) * dt;
        m.vel *= Math.exp(-3.0 * dt);
        m.c += m.vel * dt;
        const lo = m.zw / 2, hi = 1 - m.zw / 2;
        if (m.c < lo) { m.c = lo; m.vel = Math.abs(m.vel) * 0.25; }
        if (m.c > hi) { m.c = hi; m.vel = -Math.abs(m.vel) * 0.25; }
        // fish wanders, faster and twitchier when rarer
        m.ftimer -= dt;
        if (m.ftimer <= 0) {
          m.ftimer = rand(0.45, 1.35) / (0.5 + s.diff);
          const step = rand(0.12, 0.45 + s.diff * 0.4) * (Math.random() < 0.5 ? -1 : 1);
          m.ftarget = clamp(m.fp + step, 0.04, 0.96);
          m.dart = Math.random() < s.diff * 0.35 ? 1 : 0;
        }
        const sp = 1.7 + s.diff * 3.4 + (m.dart ? 3 : 0);
        const prev = m.fp;
        m.fp += (m.ftarget - m.fp) * Math.min(1, dt * sp);
        if (Math.abs(m.fp - prev) > 1e-4) m.face = m.fp > prev ? -1 : 1;
        // progress
        const inside = Math.abs(m.fp - m.c) < m.zw / 2 + 0.01;
        const gain = 0.3 * (1 + (save.rod - 1) * 0.07);
        m.prog += (inside ? gain : -(0.16 + s.diff * 0.2)) * dt;
        m.prog = clamp(m.prog, 0, 1);
        // draw
        el.zone.style.left = ((m.c - m.zw / 2) * 100) + '%';
        el.fish.style.left = (m.fp * 100) + '%';
        el.fish.style.transform = `translateX(-50%) scaleX(${m.face})`;
        el.fill.style.width = (m.prog * 100) + '%';
        el.zone.classList.toggle('on', inside);
        el.mini.classList.toggle('good', inside);
        // bobber thrashes
        bob.copy(f.target); bob.x += Math.sin(t * 9) * 0.12; bob.z += Math.cos(t * 7.3) * 0.12; bob.y = WATER_Y + 0.02 + Math.abs(Math.sin(t * 11)) * 0.05;
        f.ripT -= dt; if (f.ripT < 0) { f.ripT = 0.4; addRipple(bob.x, bob.z, 0.55); }
        slack = 0.02;
        if (m.prog >= 1) finish(true);
        else if (m.prog <= 0) finish(false);
        break;
      }
      case 'reel': {
        const k = Math.min(1, f.t / 0.55);
        bob.lerpVectors(f.from, tip, k * k); bob.y += Math.sin(Math.PI * k) * 0.8;
        slack = 0.2 * (1 - k);
        if (k >= 1) { bobber.visible = line.visible = false; setState('idle'); }
        break;
      }
    }
    // rod flex while a fish pulls
    const pull = f.state === 'mini' ? 0.07 + Math.sin(t * 14) * 0.012 : f.state === 'bite' ? Math.sin(t * 30) * 0.02 : 0;
    shaft.rotation.z = -pull * 1.4;
    if (bobber.visible) {
      bobber.position.copy(bob);
      updateLine(tip, bob, slack);
    }
  }

  /* --------------------------------- input ------------------------------- */
  addEventListener('keydown', (e) => {
    if (e.key === 'j' || e.key === 'J') { toggleJournal(); return; }
    if (e.key === 'Escape') { toggleJournal(false); return; }
    if (e.code === 'Space' && !e.repeat) {
      if (!el.journal.hidden) return;
      e.preventDefault(); if (document.activeElement && document.activeElement.blur) document.activeElement.blur();
      press();
    }
  });
  addEventListener('keyup', (e) => { if (e.code === 'Space') release(); });

  const canvas = $('c');
  canvas.addEventListener('pointerdown', () => { if (f.state === 'bite') hook(); else if (f.state === 'mini') m.hold = true; });
  addEventListener('pointerup', release);
  addEventListener('pointercancel', release);
  el.mini.addEventListener('pointerdown', (e) => { e.preventDefault(); m.hold = true; });
  el.cast.addEventListener('pointerdown', (e) => { e.preventDefault(); press(); });
  el.cast.addEventListener('keydown', (e) => { if (e.code === 'Space' || e.key === 'Enter') e.preventDefault(); });

  hud();
  setTimeout(() => say('Press Space or click the water to cast', false), 6500);

  return {
    update,
    resetCamera,
    // true when the click was used by the game (so the caller skips its own ripple)
    onWaterClick(hit) {
      if (f.state === 'idle') { cast(hit); return true; }
      return f.state !== 'wait';
    },
    busy: () => f.state === 'bite' || f.state === 'mini',
  };
}
