import * as THREE from 'three';
import * as G from './game.js';
import { RARITY, SPECIES, SPECIES_BY_ID, MUTATIONS } from './game.js';
import { fishArt } from './fishart.js';
import { icon } from './icons.js';
import * as UI from './ui.js';

/* =====================================================================
   Fishing: cast from the dock, wait for a bite, hook it, then win the catch
   bar (bottom half of the screen). Hold = zone moves right, release = it falls left.
   ===================================================================== */

const $ = (id) => document.getElementById(id);
const rand = (a, b) => a + Math.random() * (b - a);
const clamp = (x, a, b) => Math.max(a, Math.min(b, x));

function shoreR(th) { return 8.2 + 1.6 * Math.sin(2 * th + 0.6) + 0.9 * Math.sin(3 * th + 2.0) + 0.5 * Math.sin(5 * th + 1.0); }

export function initFishing(ctx) {
  const { scene, camera, player, addRipple, hAt, WATER_Y, audio, getPreset, getWeather } = ctx;
  const save = G.state;

  /* ------------------- the rod, held in front of the first-person camera ----------- */
  const rodHold = new THREE.Group();
  rodHold.position.set(0.36, -0.4, -0.5);
  camera.add(rodHold);
  const rod = new THREE.Group();
  rod.quaternion.setFromUnitVectors(new THREE.Vector3(0, 1, 0), new THREE.Vector3(-0.16, 0.55, -1).normalize());
  rod.scale.set(0.55, 0.72, 0.55);
  rodHold.add(rod);
  const lam = (c, e) => new THREE.MeshLambertMaterial({ color: c, ...(e || {}) });
  const shaftMat = lam(0x9a7a44);
  const shaft = new THREE.Mesh(new THREE.CylinderGeometry(0.011, 0.028, 3.2, 8).translate(0, 1.6, 0), shaftMat);
  const grip = new THREE.Mesh(new THREE.CylinderGeometry(0.034, 0.034, 0.5, 10).translate(0, 0.25, 0), lam(0xc9a36a));
  const reel = new THREE.Mesh(new THREE.CylinderGeometry(0.06, 0.06, 0.05, 14).rotateZ(Math.PI / 2).translate(0.06, 0.58, 0), lam(0xb8b8c0));
  rod.add(shaft, grip, reel);
  const tip = new THREE.Vector3();
  const tipLocal = new THREE.Vector3(0, 3.2, 0);

  /* ------------------------------ bobber & line -------------------------- */
  const topMat = lam(0xff4630, { emissive: 0xff2a10, emissiveIntensity: 0.6 });
  const botMat = lam(0xffffff);
  const bobber = new THREE.Group();
  bobber.add(new THREE.Mesh(new THREE.SphereGeometry(0.085, 14, 8, 0, Math.PI * 2, 0, Math.PI / 2), topMat),
    new THREE.Mesh(new THREE.SphereGeometry(0.085, 14, 8, 0, Math.PI * 2, Math.PI / 2, Math.PI / 2), botMat),
    new THREE.Mesh(new THREE.CylinderGeometry(0.008, 0.008, 0.16, 6).translate(0, 0.16, 0), topMat));
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

  // the fish that leaps out of the water when you land a catch
  const fishMesh = new THREE.Group();
  const fBodyMat = lam(0xffffff), fTailMat = lam(0xffffff);
  const fBody = new THREE.Mesh(new THREE.SphereGeometry(0.5, 14, 10).scale(1.0, 0.36, 0.3), fBodyMat);
  const fTail = new THREE.Mesh(new THREE.ConeGeometry(0.2, 0.42, 3).rotateZ(Math.PI / 2).translate(-0.62, 0, 0), fTailMat);
  const fFin = new THREE.Mesh(new THREE.ConeGeometry(0.1, 0.24, 3).translate(0, 0.2, 0), fTailMat);
  const fEye = new THREE.Mesh(new THREE.SphereGeometry(0.04, 6, 6).translate(0.4, 0.06, 0.12), lam(0x10161a));
  const fEye2 = fEye.clone(); fEye2.position.z = -0.24;
  fishMesh.add(fBody, fTail, fFin, fEye, fEye2);
  fishMesh.visible = false; scene.add(fishMesh);

  function applyGear() {
    const r = G.rod(), fl = G.floatDef();
    shaftMat.color.set(r.color);
    shaftMat.emissive.set(r.glow ? r.color : 0x000000); shaftMat.emissiveIntensity = r.glow ? 0.55 : 0;
    topMat.color.set(fl.c1); topMat.emissive.set(fl.c1); topMat.emissiveIntensity = 0.5 + (fl.glow || 0);
    botMat.color.set(fl.c2); botMat.emissive.set(fl.glow ? fl.c2 : 0x000000); botMat.emissiveIntensity = fl.glow ? 0.25 * fl.glow : 0;
  }
  applyGear();

  /* ---------------------------------- UI --------------------------------- */
  const el = {
    status: $('status'), mini: $('mini'), arrow: $('arrow'), note: $('miniNote'),
    coach: $('coach'), coachTitle: $('coachTitle'), coachBody: $('coachBody'), dots: $('cdots'), skip: $('coachSkip'), next: $('coachNext'),
    track: $('track'), zone: $('zone'), fish: $('fishIcon'), fishFi: null, fill: $('progFill'), card: $('card'), cast: $('cast'),
  };
  const tut = { active: false, step: 0, retry: false };
  let statusTimer = 0;
  function say(text, sticky = false) {
    if (tut.active) return;
    el.status.textContent = text; el.status.classList.add('show');
    clearTimeout(statusTimer);
    if (!sticky) statusTimer = setTimeout(() => el.status.classList.remove('show'), 2600);
  }

  const TUT = [
    ['Cast your line', 'Walk with WASD (or the joystick) and look at the pond. Press Space, click, or tap Cast to throw your line.'],
    ['Wait for it', 'Watch the bobber. When it dips, a fish has bitten.'],
    ['It bit!', 'Press Space, or tap, right now to hook it.'],
    ['Reel it in', 'Tap and hold anywhere to move the white bar right. Let go and it slides back left.'],
    ['Follow the fish', 'Keep the white bar over the fish marker to fill the progress bar. Do not let it run dry.'],
    ['Nice catch!', 'Fish go into your bag. Sell them at the shop (B) for coins, then buy better rods and bait. The journal (J) tracks every species.'],
  ];
  function tutShow(step) {
    tut.step = step;
    el.coach.hidden = false;
    el.coachTitle.textContent = TUT[step][0]; el.coachBody.textContent = TUT[step][1];
    el.dots.innerHTML = TUT.map((_, i) => `<i class="${i === step ? 'on' : i < step ? 'done' : ''}"></i>`).join('');
    el.cast.classList.toggle('hl', step === 0);
    el.track.classList.toggle('hl', step === 3 || step === 4);
    el.next.hidden = step !== 5; el.skip.hidden = step === 5;
    el.coach.classList.toggle('low', step === 5);
  }
  function tutStart() { if (f.state !== 'idle') return; tut.active = true; tut.retry = false; el.status.classList.remove('show'); tutShow(0); }
  function tutEnd() {
    tut.active = false; el.coach.hidden = true; el.cast.classList.remove('hl'); el.track.classList.remove('hl');
    save.tutorial = true; G.commit(); say('Press Space or click the water to cast');
  }
  el.skip.addEventListener('click', tutEnd);
  el.next.addEventListener('click', tutEnd);
  $('help').addEventListener('click', () => { if (f.state === 'idle') tutStart(); else say('Finish this cast first, then tap the question mark again'); });

  /* ------------------------------ game state ----------------------------- */
  const f = { state: 'idle', t: 0, wait: 0, bite: 0, target: new THREE.Vector3(), from: new THREE.Vector3(), sp: null, ripT: 0, cam: null, fishView: false, bait: null, land: null };
  const m = { c: 0.5, vel: 0, fp: 0.5, ftarget: 0.5, ftimer: 0, prog: 0.3, zw: 0.25, hold: false, face: 1, dart: 0, inT: 0, tot: 0 };
  const bob = new THREE.Vector3();

  const label = { idle: 'Cast', cast: '...', wait: 'Reel in', bite: 'HOOK IT!', mini: 'Hold', reel: '...', land: '...' };
  function setState(s) {
    f.state = s; f.t = 0;
    el.cast.innerHTML = `${icon('rod', 18)}<span>${label[s] || 'Cast'}</span>`;
    el.cast.classList.toggle('hot', s === 'bite');
    document.body.dataset.fish = s;
  }
  setState('idle');

  function pickSpecies() {
    const p = getPreset(), rain = getWeather(), b = f.bait;
    const luck = G.luck(b);
    const pool = SPECIES.map((s) => {
      let w = s.weight * Math.pow(1 + luck, s.rarity * 0.7);
      if (s.hint) {
        let k = 1;
        if (s.hint === 'night') k = p === 'night' ? 8 : 0.2;
        else if (s.hint === 'dusk') k = p === 'twilight' ? 7 : p === 'golden' ? 3 : 0.3;
        else if (s.hint === 'rain') k = rain > 0.5 ? 8 : 0.15;
        else if (s.hint === 'day') k = p === 'midday' || p === 'golden' ? 4 : 0.3;
        if (b && b.boost === 'time' && (s.hint === 'night' || s.hint === 'dusk')) k *= 3;
        w *= k;
      }
      return [s, w];
    });
    const tot = pool.reduce((a, [, w]) => a + w, 0);
    let r = Math.random() * tot;
    for (const [s, w] of pool) { if ((r -= w) <= 0) return s; }
    return SPECIES[0];
  }

  function chooseTarget(aim) {
    const eye = player.eye(), fw = player.forward();
    const ok = (v) => hAt(v.x, v.z) < WATER_Y - 0.45;
    const inRange = (v) => { const d = Math.hypot(v.x - eye.x, v.z - eye.z); return d > 2 && d < 17; };
    if (aim && ok(aim) && inRange(aim)) return aim.clone().setY(WATER_Y);
    if (fw.y < -0.02) { const v = eye.clone().addScaledVector(fw, (WATER_Y - eye.y) / fw.y); if (ok(v) && inRange(v)) return v.setY(WATER_Y); }
    const h = new THREE.Vector3(fw.x, 0, fw.z).normalize();
    for (const d of [7, 5, 9, 4, 11, 13]) { const v = eye.clone().addScaledVector(h, d); if (ok(v)) return v.setY(WATER_Y); }
    return null;
  }

  /* -------------------------------- actions ------------------------------ */
  function cast(aim) {
    if (f.state !== 'idle') return false;
    const tgt = chooseTarget(aim);
    if (!tgt) { say('Walk closer and look at deeper water to cast'); if (tut.active) { el.coachTitle.textContent = 'Find the water'; el.coachBody.textContent = 'Walk to the pond (WASD or the joystick), look at the water, then press Space or click.'; } return false; }
    if (tut.active && tut.step === 0) tutShow(1);
    el.card.classList.remove('show');
    f.bait = tut.active ? null : G.bait();
    if (f.bait) { G.useBait(); }
    save.stats.casts += 1; G.commit();
    f.target = tgt;
    f.from.copy(tip);
    bobber.visible = line.visible = true;
    setState('cast');
    say(f.bait ? `Casting with ${f.bait.name}` : 'Casting...');
    audio.sfx('cast');
    return true;
  }
  function reelIn(msg, sfx) {
    if (msg) say(msg);
    if (sfx) audio.sfx(sfx);
    el.mini.hidden = true; player.freeze(false); m.hold = false;
    f.from.copy(bob);
    setState('reel');
  }
  function hook() {
    f.sp = f.sp || pickSpecies();
    if (tut.active) { f.sp = SPECIES_BY_ID.minnow; tutShow(3); }
    const s = f.sp, r = G.rod();
    m.zw = clamp(0.4 - s.diff * 0.16 + r.ctrl + (tut.active ? 0.1 : 0), 0.2, 0.64);
    m.c = 0.5; m.vel = 0; m.fp = 0.5; m.ftarget = 0.5; m.ftimer = 0; m.prog = 0.32; m.hold = false; m.inT = 0; m.tot = 0;
    el.zone.style.width = (m.zw * 100) + '%';
    el.fish.innerHTML = `<div class="fi">${fishArt({ ...s, c: ['#aab1ba', '#8a929c', '#7d8791'], pat: 'plain' })}</div><b class="mk"></b>`;
    el.fishFi = el.fish.querySelector('.fi');
    el.note.textContent = `+${Math.round(r.prog * 100)}% Progress Speed`;
    el.arrow.textContent = '←';
    el.mini.hidden = false; el.status.classList.remove('show');
    player.freeze(true);
    addRipple(f.target.x, f.target.z, 1.2);
    audio.sfx('hook');
    setState('mini');
  }
  function press(aim) {
    if (UI.isOpen()) return;
    if (f.state === 'idle') cast(aim || null);
    else if (f.state === 'wait') reelIn('Too early. Wait for the bobber to dip.', 'miss');
    else if (f.state === 'bite') hook();
    else if (f.state === 'mini') m.hold = true;
  }
  function release() { m.hold = false; }

  function rollMutation(luck) {
    const r = Math.random(), k = 1 + luck * 0.4;
    let a = MUTATIONS.albino.p * k, s = MUTATIONS.shiny.p * k, g = MUTATIONS.giant.p * k;
    if (r < a) return 'albino';
    if (r < a + s) return 'shiny';
    if (r < a + s + g) return 'giant';
    return 'normal';
  }

  function finish(win) {
    const s = f.sp;
    if (!win) { f.sp = null; if (tut.active) tut.retry = true; reelIn('It slipped away...', 'miss'); return; }
    const luck = G.luck(f.bait), mut = tut.active ? 'normal' : rollMutation(luck), M = MUTATIONS[mut];
    let kg = s.w[0] + Math.pow(Math.random(), 1.6) * (s.w[1] - s.w[0]);
    kg = +(kg * M.kg).toFixed(2);
    const mid = (s.w[0] + s.w[1]) / 2;
    const perfect = m.tot > 0 && m.inT / m.tot >= 0.85;
    let value = rand(s.val[0], s.val[1]) * (0.85 + 0.3 * kg / mid) * M.val * (perfect ? 1.25 : 1);
    value = Math.round(value);
    const res = G.recordCatch(s, kg, mut, value);
    const xp = Math.round(value * 0.4 + 6 + s.rarity * 14 + (res.fresh ? 25 : 0));
    const lv = G.addXp(xp);
    const r = RARITY[s.rarity];
    const tags = [r.name, res.fresh ? 'New species' : '', M.label, perfect ? 'Perfect catch' : ''].filter(Boolean).join(' · ');
    el.card.style.setProperty('--rc', M.color || r.color);
    el.card.innerHTML = `<div class="cicon">${fishArt(s, { mut })}</div>
      <div class="ctext"><span class="rar">${tags}</span><b>${s.name}</b>
      <span class="meta">${kg.toFixed(2)} kg · worth ${value} coins · ${res.stored ? 'in your bag' : `bag full, sold for ${res.sold}`}</span></div>`;
    f.pending = { s, mut, lv, rarity: s.rarity };
    audio.sfx(s.rarity >= 3 ? 'legend' : 'win');
    // the fish leaps toward the dock
    fBodyMat.color.set(s.c[0]); fTailMat.color.set(s.c[2] || s.c[1]);
    fishMesh.scale.setScalar(0.5 + Math.min(1.5, Math.pow(kg, 0.33) * 0.55));
    f.land = { from: bob.clone() };
    addRipple(bob.x, bob.z, 1.6);
    el.mini.hidden = true; player.freeze(false); m.hold = false;
    f.sp = null;
    fishMesh.visible = true;
    setState('land');
  }
  function showCard() {
    const p = f.pending; f.pending = null;
    el.card.classList.add('show');
    clearTimeout(showCard.t); showCard.t = setTimeout(() => el.card.classList.remove('show'), 4600);
    if (p && p.lv && p.lv.length) { UI.toast(`${icon('star', 18)} Angler level ${p.lv[p.lv.length - 1]}`, 'level'); audio.sfx('level'); }
    if (tut.active) tutShow(5); else say('Press Space to cast again');
  }

  /* -------------------------------- update ------------------------------- */
  const catchPt = new THREE.Vector3();
  function update(dt, t) {
    // rod animation: swing back and forward on a cast, dip while a fish pulls
    let rx = 0;
    if (f.state === 'cast') { const k = f.t; rx = k < 0.22 ? 0.75 * (k / 0.22) : k < 0.5 ? 0.75 - 1.15 * ((k - 0.22) / 0.28) : -0.4 * Math.max(0, 1 - (k - 0.5) / 0.4); }
    else if (f.state === 'mini') rx = -0.22 + Math.sin(t * 14) * 0.03;
    else if (f.state === 'bite') rx = -0.08 + Math.sin(t * 30) * 0.03;
    else rx = Math.sin(t * 1.3) * 0.015 + (player.moving ? Math.sin(t * 9) * 0.02 : 0);
    rodHold.rotation.x += (rx - rodHold.rotation.x) * Math.min(1, dt * 16);
    camera.updateMatrixWorld();
    rod.updateMatrixWorld();
    tip.copy(tipLocal).applyMatrix4(rod.matrixWorld);

    f.t += dt;
    let slack = 0.5;
    switch (f.state) {
      case 'cast': {
        const k = Math.min(1, f.t / 0.85), e = k * k * (3 - 2 * k);
        bob.lerpVectors(f.from, f.target, e); bob.y += Math.sin(Math.PI * k) * 2.4;
        slack = 0.1;
        if (k >= 1) {
          addRipple(f.target.x, f.target.z, 1.1); audio.sfx('splash');
          const r = G.rod(), b = f.bait;
          f.wait = tut.active ? rand(1.3, 2.2) : rand(2.4, 6.5) * (1 - r.speed) * (b ? b.wait : 1) * (1 - 0.3 * getWeather());
          f.sp = null; f.ripT = 1.2;
          setState('wait'); say('Wait for the bobber to dip...', true);
        }
        break;
      }
      case 'wait': {
        bob.copy(f.target); bob.y = WATER_Y + 0.03 + Math.sin(t * 2.1) * 0.012;
        f.ripT -= dt; if (f.ripT < 0) { f.ripT = rand(1.6, 3.2); addRipple(f.target.x + rand(-0.2, 0.2), f.target.z + rand(-0.2, 0.2), 0.25); }
        if (f.t > f.wait) {
          f.sp = tut.active ? SPECIES_BY_ID.minnow : pickSpecies();
          f.bite = tut.active ? 999 : 1.35; if (tut.active) tutShow(2);
          setState('bite'); say('Bite! Press Space now!', true); audio.sfx('bite'); addRipple(f.target.x, f.target.z, 1.4);
        }
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
        m.vel += (m.hold ? 3.1 : -2.5) * dt;
        m.vel *= Math.exp(-3.0 * dt);
        m.c += m.vel * dt;
        const lo = m.zw / 2, hi = 1 - m.zw / 2;
        if (m.c < lo) { m.c = lo; m.vel = Math.abs(m.vel) * 0.25; }
        if (m.c > hi) { m.c = hi; m.vel = -Math.abs(m.vel) * 0.25; }
        m.ftimer -= dt;
        if (m.ftimer <= 0) {
          m.ftimer = rand(0.45, 1.35) / (0.5 + s.diff);
          const step = rand(0.12, 0.45 + s.diff * 0.4) * (Math.random() < 0.5 ? -1 : 1);
          m.ftarget = clamp(m.fp + step, 0.04, 0.96);
          m.dart = Math.random() < s.diff * 0.25 + s.dart * 0.3 ? 1 : 0;
        }
        if (tut.active && tut.step === 3 && f.t > 4.5) tutShow(4);
        const sp = (1.7 + s.diff * 3.4 + (m.dart ? 3 : 0)) * (tut.active ? 0.7 : 1);
        const prev = m.fp;
        m.fp += (m.ftarget - m.fp) * Math.min(1, dt * sp);
        if (Math.abs(m.fp - prev) > 1e-4) m.face = m.fp > prev ? -1 : 1;
        const inside = Math.abs(m.fp - m.c) < m.zw / 2 + 0.01;
        m.tot += dt; if (inside) m.inT += dt;
        const gain = 0.26 * (1 + G.rod().prog) * (tut.active ? 1.5 : 1);
        m.prog += (inside ? gain : -(0.18 + s.diff * 0.25) * (tut.active ? 0.35 : 1)) * dt;
        m.prog = clamp(m.prog, 0, 1);
        el.zone.style.left = ((m.c - m.zw / 2) * 100) + '%';
        el.fish.style.left = (m.fp * 100) + '%';
        if (el.fishFi) el.fishFi.style.transform = `scaleX(${m.face})`;
        el.arrow.textContent = m.hold ? '→' : '←';
        el.fill.style.width = (m.prog * 100) + '%';
        el.zone.classList.toggle('on', inside);
        bob.copy(f.target); bob.x += Math.sin(t * 9) * 0.12; bob.z += Math.cos(t * 7.3) * 0.12; bob.y = WATER_Y + 0.02 + Math.abs(Math.sin(t * 11)) * 0.05;
        f.ripT -= dt; if (f.ripT < 0) { f.ripT = 0.4; addRipple(bob.x, bob.z, 0.55); }
        slack = 0.02;
        if (m.prog >= 1) finish(true);
        else if (m.prog <= 0) finish(false);
        break;
      }
      case 'land': {
        const k = Math.min(1, f.t / 0.95);
        catchPt.copy(player.eye()).addScaledVector(player.forward(), 1.4).add(new THREE.Vector3(0, -0.35, 0));
        fishMesh.position.lerpVectors(f.land.from, catchPt, k); fishMesh.position.y += Math.sin(Math.PI * k) * 1.9;
        fishMesh.rotation.set(0, Math.atan2(-(catchPt.z - f.land.from.z), catchPt.x - f.land.from.x), Math.sin(k * 14) * 0.5);
        bob.copy(fishMesh.position); slack = 0.1;
        if (k >= 1) { fishMesh.visible = false; bobber.visible = line.visible = false; setState('idle'); showCard(); }
        break;
      }
      case 'reel': {
        const k = Math.min(1, f.t / 0.55);
        bob.lerpVectors(f.from, tip, k * k); bob.y += Math.sin(Math.PI * k) * 0.8;
        slack = 0.2 * (1 - k);
        if (k >= 1) {
          bobber.visible = line.visible = false; setState('idle');
          if (tut.active && tut.step < 5) {
            tutShow(0);
            if (tut.retry) { el.coachTitle.textContent = 'Try again'; el.coachBody.textContent = 'It happens. Cast again, this fish is easy to follow.'; tut.retry = false; }
          }
        }
        break;
      }
      default: break;
    }
    const pull = f.state === 'mini' ? 0.07 + Math.sin(t * 14) * 0.012 : f.state === 'bite' ? Math.sin(t * 30) * 0.02 : 0;
    shaft.rotation.z = -pull * 1.4;
    if (bobber.visible) {
      if (f.state !== 'land') bobber.position.copy(bob); else bobber.position.set(0, -50, 0);
      updateLine(tip, bob, slack);
    }
  }

  /* --------------------------------- input ------------------------------- */
  addEventListener('keydown', (e) => {
    if (e.code === 'Space' && !e.repeat) {
      if (UI.isOpen()) return;
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

  setTimeout(() => { if (!save.tutorial) tutStart(); else say('Press Space or click the water to cast'); }, 6500);

  return {
    update, applyGear, replayTutorial: tutStart, rod: rodHold, cast: (aim) => press(aim),
    onWaterClick(hit) {
      if (f.state === 'idle') { cast(hit); return true; }
      return f.state !== 'wait';
    },
    busy: () => f.state === 'bite' || f.state === 'mini' || f.state === 'land',
  };
}
