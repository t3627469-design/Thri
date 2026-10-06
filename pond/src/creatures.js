import * as THREE from 'three';

/* Wildlife. Every animal steers toward goals and around obstacles (dock, boat, shore, props, the player)
   instead of following fixed curves. */

const rand = (a, b) => a + Math.random() * (b - a);
const TAU = Math.PI * 2;
const angDiff = (a, b) => { let d = (b - a) % TAU; if (d > Math.PI) d -= TAU; if (d < -Math.PI) d += TAU; return d; };

export function initCreatures(ctx) {
  const { root, scene, camera, addRipple, hAt, WATER_Y, audio, getPlayer, colliders = [] } = ctx;
  const by = (n) => root.getObjectByName(n);
  const mat = new THREE.MeshLambertMaterial({ vertexColors: true, side: THREE.DoubleSide });
  root.traverse((o) => { if (o.isMesh && /^(Duck|Koi|Bfly|Frog|Dfly|Heron|Rabbit)/.test(o.name)) { o.material = mat; o.castShadow = /^(Duck|Heron|Rabbit)/.test(o.name); } });
  const U_T = { value: 0 };

  /* ------------------------------ world queries --------------------------- */
  const th = -Math.PI / 2 - 0.35;
  const R = 8.2 + 1.6 * Math.sin(2 * th + 0.6) + 0.9 * Math.sin(3 * th + 2.0) + 0.5 * Math.sin(5 * th + 1.0);
  const S0 = new THREE.Vector2(R * Math.cos(th), -R * Math.sin(th)), DU = new THREE.Vector2(-Math.cos(th), Math.sin(th)).normalize(), DS = new THREE.Vector2(-DU.y, DU.x);
  const boat = by('Boat'); const boatY = boat ? boat.position.y : 0;
  const nearDock = (x, z, m) => { const rx = x - S0.x, rz = z - S0.y, s = rx * DU.x + rz * DU.y, l = rx * DS.x + rz * DS.y; return s > -2 && s < 5.4 + m && Math.abs(l) < 0.75 + m; };
  const nearBoat = (x, z, m) => boat && Math.hypot(x - boat.position.x, z - boat.position.z) < 1.55 + m;
  const water = (x, z, depth, m = 0.35) => hAt(x, z) < WATER_Y - depth && !nearDock(x, z, m) && !nearBoat(x, z, m);
  const land = (x, z) => {
    if (hAt(x, z) < WATER_Y + 0.08 || Math.hypot(x, z) > 33) return false;
    for (const c of colliders) { const dx = x - c[0], dz = z - c[1]; if (dx * dx + dz * dz < (c[2] + 0.2) ** 2) return false; }
    return true;
  };
  function pick(test, rMin, rMax, tries = 60) {
    for (let i = 0; i < tries; i++) { const a = Math.random() * TAU, r = rand(rMin, rMax), x = Math.cos(a) * r, z = Math.sin(a) * r; if (test(x, z)) return new THREE.Vector2(x, z); }
    return null;
  }
  const player = () => { const p = getPlayer && getPlayer(); return p ? p.pos : null; };

  /* A small steering agent on the XZ plane. */
  function makeAgent(x, z, ok, speed, turn = 1.6) {
    return { p: new THREE.Vector2(x, z), h: Math.random() * TAU, speed, turn, ok, target: null, v: 0 };
  }
  function steer(a, dt, desiredSpeed) {
    if (!a.target) return;
    const dx = a.target.x - a.p.x, dz = a.target.y - a.p.y;
    let want = Math.atan2(dx, dz);
    // feel ahead with three whiskers; turn toward the clearest one
    const look = 0.9 + a.v * 1.5;
    const probe = (off) => { const h = a.h + off; return a.ok(a.p.x + Math.sin(h) * look, a.p.y + Math.cos(h) * look); };
    if (!probe(0)) { want = probe(0.7) ? a.h + 0.9 : probe(-0.7) ? a.h - 0.9 : a.h + Math.PI; desiredSpeed *= 0.4; }
    a.h += Math.max(-a.turn * dt, Math.min(a.turn * dt, angDiff(a.h, want)));
    a.v += (desiredSpeed - a.v) * Math.min(1, dt * 2);
    const nx = a.p.x + Math.sin(a.h) * a.v * dt, nz = a.p.y + Math.cos(a.h) * a.v * dt;
    if (a.ok(nx, nz)) a.p.set(nx, nz); else { a.v *= 0.3; a.h += a.turn * dt * 2; }
  }
  const atTarget = (a, d = 0.4) => !a.target || a.p.distanceTo(a.target) < d;

  /* --------------------------------- ducks -------------------------------- */
  const duckOk = (x, z) => water(x, z, 0.12, 0.45);
  const D = { drake: by('Duck_Drake'), hen: by('Duck_Hen'), chicks: [0, 1, 2, 3].map((i) => by('Duck_Chick' + i)).filter(Boolean) };
  [D.drake, D.hen, ...D.chicks].forEach((o) => { if (o) o.rotation.order = 'YXZ'; });
  const start = pick((x, z) => water(x, z, 0.5, 1), 1, 5) || new THREE.Vector2(0, 0);
  const hen = makeAgent(start.x, start.y, duckOk, 0.32, 1.4);
  const drakeA = makeAgent(start.x + 1, start.y + 0.6, duckOk, 0.36, 1.5);
  const trail = [hen.p.clone()];
  const chicks = D.chicks.map((o, i) => ({ o, p: hen.p.clone().add(new THREE.Vector2(0, -0.4 * (i + 1))), h: 0 }));
  const dab = new Map([[D.hen, { t: rand(8, 20), on: 0 }], [D.drake, { t: rand(6, 16), on: 0 }]]);
  let wake = 0;
  function duckBrain(a, o, dt, t, follow) {
    const pp = player();
    let spd = a.speed;
    if (pp && Math.hypot(pp.x - a.p.x, pp.z - a.p.y) < 3.4) {
      const away = new THREE.Vector2(a.p.x - pp.x, a.p.y - pp.z).normalize().multiplyScalar(3.5).add(a.p);
      if (duckOk(away.x, away.y)) a.target = away; spd = 0.75;
    } else if (atTarget(a, 0.5)) {
      a.target = follow && Math.random() < 0.6 ? follow.clone().add(new THREE.Vector2(rand(-1, 1), rand(-1, 1))) : pick((x, z) => water(x, z, 0.4, 0.8), 0.5, 6.5);
      if (a.target && !duckOk(a.target.x, a.target.y)) a.target = null;
    }
    const d = dab.get(o);
    d.t -= dt;
    if (d.t <= 0 && !d.on && spd < 0.5) { d.on = 2.6; d.t = rand(10, 24); addRipple(a.p.x, a.p.y, 0.6); }
    let pitch = 0;
    if (d.on > 0) { d.on -= dt; const k = Math.min(1, Math.min(2.6 - d.on, d.on) * 4); pitch = 1.25 * k; spd = 0.02; }
    steer(a, dt, spd);
    o.position.set(a.p.x, WATER_Y + Math.sin(t * 1.7 + a.h) * 0.01, a.p.y);
    o.rotation.set(pitch, a.h, Math.sin(t * 1.3) * 0.02);
  }
  function ducksUpdate(dt, t) {
    if (!D.hen) return;
    duckBrain(hen, D.hen, dt, t, null);
    duckBrain(drakeA, D.drake, dt, t, hen.p);
    if (trail[trail.length - 1].distanceTo(hen.p) > 0.06) { trail.push(hen.p.clone()); if (trail.length > 160) trail.shift(); }
    // ducklings: follow the hen's trail, each a little further back
    chicks.forEach((c, i) => {
      let need = 0.5 + i * 0.36, k = trail.length - 1;
      while (k > 0 && need > 0) { need -= trail[k].distanceTo(trail[k - 1]); k--; }
      const tgt = trail[Math.max(0, k)];
      const dx = tgt.x - c.p.x, dz = tgt.y - c.p.y, dist = Math.hypot(dx, dz);
      if (dist > 0.02) { c.h += angDiff(c.h, Math.atan2(dx, dz)) * Math.min(1, dt * 5); c.p.x += dx * Math.min(1, dt * 2.2); c.p.y += dz * Math.min(1, dt * 2.2); }
      c.o.position.set(c.p.x, WATER_Y + Math.abs(Math.sin(t * 3 + i)) * 0.012, c.p.y);
      c.o.rotation.set(0, c.h, Math.sin(t * 2.4 + i) * 0.04);
    });
    wake -= dt;
    if (wake < 0) { wake = 0.9; if (hen.v > 0.1) addRipple(hen.p.x, hen.p.y, 0.4); if (drakeA.v > 0.1) addRipple(drakeA.p.x, drakeA.p.y, 0.4); }
  }

  /* ---------------------------------- koi --------------------------------- */
  const koiMat = mat.clone();
  koiMat.onBeforeCompile = (sh) => {
    sh.uniforms.uT = U_T;
    sh.vertexShader = sh.vertexShader.replace('#include <common>', '#include <common>\nuniform float uT;')
      .replace('#include <begin_vertex>', `#include <begin_vertex>
        float ph = modelMatrix[3].x * 3.1 + modelMatrix[3].z * 1.7;
        float tailK = pow(clamp((0.12 - position.z) / 0.62, 0.0, 1.0), 1.3);
        transformed.x += sin(uT * 7.0 - position.z * 9.0 + ph) * 0.055 * tailK;`);
  };
  koiMat.customProgramCacheKey = () => 'koi-wave';
  const koiOk = (x, z) => hAt(x, z) < WATER_Y - 0.75;
  const koi = [];
  for (let i = 0; i < 7; i++) {
    const o = by('Koi_' + i); if (!o) continue;
    o.material = koiMat;
    const s = pick((x, z) => koiOk(x, z), 0, 5) || new THREE.Vector2(0, 0);
    koi.push({ o, a: makeAgent(s.x, s.y, koiOk, rand(0.22, 0.4), 1.2), depth: rand(0.18, 0.34), rise: rand(12, 40), leap: rand(30, 90), jump: null });
  }
  function koiUpdate(dt, t) {
    koi.forEach((k) => {
      if (atTarget(k.a, 0.5)) k.a.target = pick(koiOk, 0, 6.5);
      k.rise -= dt; k.leap -= dt;
      if (k.jump) {
        k.jump.t += dt / 0.95;
        const q = Math.min(1, k.jump.t);
        k.o.position.set(k.a.p.x + Math.sin(k.a.h) * q * 1.1, WATER_Y + Math.sin(Math.PI * q) * 0.7 - 0.05, k.a.p.y + Math.cos(k.a.h) * q * 1.1);
        k.o.rotation.set(-Math.cos(Math.PI * q) * 0.9, k.a.h, 0, 'YXZ');
        if (q >= 1) { addRipple(k.o.position.x, k.o.position.z, 1.4); k.a.p.set(k.o.position.x, k.o.position.z); k.jump = null; if (!koiOk(k.a.p.x, k.a.p.y)) k.a.p.set(0, 0); }
        return;
      }
      if (k.leap <= 0 && koiOk(k.a.p.x + Math.sin(k.a.h) * 1.2, k.a.p.y + Math.cos(k.a.h) * 1.2)) {
        k.leap = rand(45, 120); k.jump = { t: 0 }; addRipple(k.a.p.x, k.a.p.y, 1.2);
        if (audio.on) audio.pluck(880, 0.04);
        return;
      }
      steer(k.a, dt, k.a.speed);
      let y = WATER_Y - k.depth;
      if (k.rise < 0) { const r = -k.rise; y = WATER_Y - k.depth * Math.max(0.25, 1 - Math.sin(Math.min(1, r / 2.5) * Math.PI)); if (r > 0.9 && r < 0.95) addRipple(k.a.p.x, k.a.p.y, 0.5); if (r > 2.5) k.rise = rand(15, 45); }
      k.o.position.set(k.a.p.x, y, k.a.p.y);
      k.o.rotation.set(0, k.a.h + Math.sin(t * 7 + k.a.h) * 0.05, 0);
    });
  }

  /* ------------------------------ butterflies ---------------------------- */
  const bodySrc = by('BflyBody'), wingNames = ['monarch', 'morpho', 'brimstone', 'peacock'];
  if (bodySrc) bodySrc.visible = false;
  const bflies = [];
  for (let i = 0; i < 8 && bodySrc; i++) {
    const ws = by('BflyWing_' + wingNames[i % 4]); if (!ws) continue;
    ws.visible = false;
    const g = new THREE.Group();
    const body = new THREE.Mesh(bodySrc.geometry, mat);
    const wl = new THREE.Mesh(ws.geometry, mat), wr = new THREE.Mesh(ws.geometry, mat);
    wr.scale.x = -1;
    g.add(body, wl, wr); g.scale.setScalar(1.35);
    scene.add(g);
    const s = pick(land, 6, 24) || new THREE.Vector2(10, 10);
    bflies.push({ g, wl, wr, p: new THREE.Vector3(s.x, hAt(s.x, s.y) + 1, s.y), tgt: null, perch: 0, ph: Math.random() * 9, fl: rand(14, 20), h: 0 });
  }
  function bflyUpdate(dt, t, show) {
    bflies.forEach((b) => {
      b.g.visible = show;
      if (!show) return;
      if (b.perch > 0) {
        b.perch -= dt;
        const f = 0.15 + (Math.sin(t * 1.6 + b.ph) * 0.5 + 0.5) * 1.1;
        b.wl.rotation.z = f; b.wr.rotation.z = -f;
        const pp = player();
        if (pp && pp.distanceTo(b.p) < 1.8) b.perch = 0;
        return;
      }
      if (!b.tgt || b.p.distanceTo(b.tgt) < 0.25) {
        if (b.tgt && Math.random() < 0.45) { b.perch = rand(3, 8); b.tgt = null; return; }
        const s = pick(land, 5, 26) || new THREE.Vector2(0, 8);
        const near = Math.random() < 0.7 ? new THREE.Vector2(b.p.x + rand(-5, 5), b.p.z + rand(-5, 5)) : s;
        const q = land(near.x, near.y) ? near : s;
        b.tgt = new THREE.Vector3(q.x, hAt(q.x, q.y) + 0.48, q.y);
      }
      const dir = b.tgt.clone().sub(b.p); const dist = dir.length(); dir.normalize();
      const wob = new THREE.Vector3(Math.sin(t * 3.3 + b.ph), Math.sin(t * 4.7 + b.ph) * 0.8, Math.cos(t * 2.9 + b.ph));
      b.p.addScaledVector(dir, Math.min(dist, 1.1 * dt)).addScaledVector(wob, 0.5 * dt);
      const floor = Math.max(hAt(b.p.x, b.p.z), WATER_Y) + 0.35;
      if (b.p.y < floor) b.p.y = floor;
      b.h += angDiff(b.h, Math.atan2(dir.x, dir.z)) * Math.min(1, dt * 4);
      b.g.position.copy(b.p).y += Math.sin(t * b.fl * 0.5 + b.ph) * 0.03;
      b.g.rotation.set(0, b.h, 0);
      const f = 0.2 + Math.sin(t * b.fl + b.ph) * 1.0;
      b.wl.rotation.z = f; b.wr.rotation.z = -f;
    });
  }

  /* --------------------------------- frogs -------------------------------- */
  let pads = [];
  try { pads = JSON.parse((by('PondMeta') && by('PondMeta').userData.pads) || '[]').map(([x, y, r]) => ({ x, z: -y, r })); } catch (e) { /* none */ }
  const frogSrc = by('Frog'); if (frogSrc) frogSrc.visible = false;
  const bigPads = pads.filter((p) => p.r > 0.34);
  const frogs = [];
  for (let i = 0; i < 4 && frogSrc && bigPads.length > 4; i++) {
    const g = new THREE.Group(), m = new THREE.Mesh(frogSrc.geometry, mat); m.castShadow = true; g.add(m);
    g.scale.setScalar(1.05);
    let pad; do { pad = bigPads[(Math.random() * bigPads.length) | 0]; } while (frogs.some((f) => f.pad === pad));
    g.position.set(pad.x, WATER_Y + 0.045, pad.z); g.rotation.y = Math.random() * TAU;
    scene.add(g);
    frogs.push({ g, m, pad, next: rand(1, 6), hop: null, ph: Math.random() * 6 });
  }
  function frogUpdate(f, dt, t) {
    const pp = player();
    if (!f.hop && pp && Math.hypot(pp.x - f.g.position.x, pp.z - f.g.position.z) < 2.2) f.next = Math.min(f.next, 0.05);
    if (f.hop) {
      f.hop.t += dt / 0.7;
      const k = Math.min(1, f.hop.t);
      f.g.position.set(f.hop.a.x + (f.hop.b.x - f.hop.a.x) * k, WATER_Y + 0.045 + Math.sin(Math.PI * k) * 0.38, f.hop.a.z + (f.hop.b.z - f.hop.a.z) * k);
      f.m.scale.set(1, 1 + Math.sin(Math.PI * k) * 0.25, 1 - Math.sin(Math.PI * k) * 0.1);
      if (k >= 1) {
        f.pad = f.hop2; f.hop = null; f.m.scale.set(1, 1, 1);
        addRipple(f.g.position.x, f.g.position.z, 0.45);
        if (audio.on && Math.random() < 0.5 && camera.position.distanceTo(f.g.position) < 20) audio.croak();
        f.next = rand(3, 9);
      }
      return;
    }
    f.m.scale.y = 1 + Math.sin(t * 2.4 + f.ph) * 0.035;
    f.next -= dt;
    if (f.next <= 0) {
      const near = bigPads.filter((p) => p !== f.pad && Math.hypot(p.x - f.pad.x, p.z - f.pad.z) < 3.2 && !frogs.some((o) => o !== f && o.pad === p));
      if (!near.length) { f.next = rand(2, 5); return; }
      const dst = near[(Math.random() * near.length) | 0];
      f.hop = { t: 0, a: new THREE.Vector3(f.g.position.x, 0, f.g.position.z), b: new THREE.Vector3(dst.x, 0, dst.z) };
      f.hop2 = dst;
      f.g.rotation.y = Math.atan2(dst.x - f.hop.a.x, dst.z - f.hop.a.z);
    }
  }

  /* ------------------------------- dragonflies ---------------------------- */
  const dBody = by('DflyBody'), dWing = by('DflyWing');
  if (dBody) dBody.visible = false; if (dWing) dWing.visible = false;
  const wingMat = new THREE.MeshBasicMaterial({ color: 0xdcefff, transparent: true, opacity: 0.45, side: THREE.DoubleSide, depthWrite: false });
  const dflies = [];
  for (let i = 0; i < 4 && dBody && dWing; i++) {
    const g = new THREE.Group();
    g.add(new THREE.Mesh(dBody.geometry, mat));
    const wings = [];
    for (const [sx, z] of [[1, 0.012], [-1, 0.012], [1, -0.022], [-1, -0.022]]) { const w = new THREE.Mesh(dWing.geometry, wingMat); w.scale.x = sx; w.position.z = z; g.add(w); wings.push([w, sx]); }
    g.scale.setScalar(1.6);
    const s = pick((x, z) => hAt(x, z) < WATER_Y, 2, 7) || new THREE.Vector2(0, 0);
    const pos = new THREE.Vector3(s.x, WATER_Y + 1, s.y);
    g.position.copy(pos); scene.add(g);
    dflies.push({ g, wings, pos, wp: pos.clone(), mode: 'hover', t: rand(0.5, 2), ph: Math.random() * 9, yaw: 0 });
  }
  function dflyUpdate(d, dt, t, show) {
    d.g.visible = show;
    if (!show) return;
    d.t -= dt;
    if (d.mode === 'hover') {
      d.pos.x += Math.sin(t * 3.1 + d.ph) * 0.003; d.pos.y += Math.sin(t * 5.3 + d.ph) * 0.002;
      if (d.t <= 0) { const s = pick((x, z) => hAt(x, z) < WATER_Y + 0.2, 1.5, 9.5) || new THREE.Vector2(0, 0); d.mode = 'dart'; d.wp.set(s.x, WATER_Y + rand(0.45, 1.6), s.y); d.t = 4; }
    } else {
      const dir = d.wp.clone().sub(d.pos), dist = dir.length();
      if (dist < 0.15 || d.t <= 0) { d.mode = 'hover'; d.t = rand(1, 3.5); }
      else { dir.normalize().multiplyScalar(Math.min(dist * 3, 5.2) * dt); d.pos.add(dir); d.yaw = Math.atan2(dir.x, dir.z); }
    }
    d.g.position.copy(d.pos);
    d.g.rotation.y += angDiff(d.g.rotation.y, d.yaw) * Math.min(1, dt * 8);
    const fl = Math.sin(t * 70 + d.ph) * 0.55;
    d.wings.forEach(([w, sx], i) => { w.rotation.z = sx * (i > 1 ? -fl : fl); });
  }

  /* --------------------------------- heron -------------------------------- */
  const hb = by('HeronBody'), hl = by('HeronLegL'), hr = by('HeronLegR');
  let heron = null;
  const shallow = (x, z) => { const h = hAt(x, z); return h < WATER_Y - 0.04 && h > WATER_Y - 0.32 && !nearDock(x, z, 1.2) && !nearBoat(x, z, 1); };
  if (hb && hl && hr) {
    [hb, hl, hr].forEach((o) => (o.visible = false));
    const g = new THREE.Group(), body = new THREE.Mesh(hb.geometry, mat), lL = new THREE.Mesh(hl.geometry, mat), lR = new THREE.Mesh(hr.geometry, mat);
    body.position.y = 0.62; lL.position.set(-0.05, 0.62, 0); lR.position.set(0.05, 0.62, 0);
    [body, lL, lR].forEach((m) => { m.castShadow = true; g.add(m); });
    g.scale.setScalar(1.25);
    scene.add(g);
    const s = pick(shallow, 5, 11) || new THREE.Vector2(6, 0);
    heron = { g, body, lL, lR, a: makeAgent(s.x, s.y, shallow, 0.35, 1.3), mode: 'stand', t: rand(4, 9), walk: 0 };
    heron.a.target = null;
  }
  function heronUpdate(dt, t) {
    if (!heron) return;
    const H = heron, pp = player();
    const scared = pp && Math.hypot(pp.x - H.a.p.x, pp.z - H.a.p.y) < 4.5;
    H.t -= dt;
    if (scared && H.mode !== 'walk') { H.mode = 'walk'; H.a.target = pick((x, z) => shallow(x, z) && (!pp || Math.hypot(pp.x - x, pp.z - z) > 8), 4, 12); H.t = 12; }
    if (H.mode === 'stand') {
      H.body.rotation.x = Math.sin(t * 0.7) * 0.03;
      if (H.t <= 0) {
        if (Math.random() < 0.4) { H.mode = 'strike'; H.t = 0.9; }
        else { H.mode = 'walk'; H.a.target = pick((x, z) => shallow(x, z) && Math.hypot(x - H.a.p.x, z - H.a.p.y) < 4, 4, 12); H.t = 10; }
      }
    } else if (H.mode === 'strike') {
      const k = 1 - H.t / 0.9, s = k < 0.3 ? k / 0.3 : k < 0.55 ? 1 : 1 - (k - 0.55) / 0.45;
      H.body.rotation.x = s * 0.95;
      if (k > 0.3 && !H.splashed) { H.splashed = true; addRipple(H.a.p.x + Math.sin(H.a.h) * 0.6, H.a.p.y + Math.cos(H.a.h) * 0.6, 0.7); }
      if (H.t <= 0) { H.mode = 'stand'; H.t = rand(5, 12); H.splashed = false; H.body.rotation.x = 0; }
    } else {
      steer(H.a, dt, scared ? 0.75 : 0.32);
      H.walk += dt * (scared ? 6 : 3.2) * Math.min(1, H.a.v * 3);
      H.lL.rotation.x = Math.sin(H.walk) * 0.45; H.lR.rotation.x = -Math.sin(H.walk) * 0.45;
      H.body.rotation.x = 0.12;
      if (atTarget(H.a, 0.3) || H.t <= 0 || !H.a.target) { H.mode = 'stand'; H.t = rand(5, 12); H.lL.rotation.x = H.lR.rotation.x = 0; H.body.rotation.x = 0; }
    }
    H.g.position.set(H.a.p.x, hAt(H.a.p.x, H.a.p.y) + Math.abs(Math.sin(H.walk)) * 0.02, H.a.p.y);
    H.g.rotation.y = H.a.h;
  }

  /* -------------------------------- rabbits ------------------------------- */
  const rabSrc = by('Rabbit'); if (rabSrc) rabSrc.visible = false;
  const rabbits = [];
  for (let i = 0; i < 5 && rabSrc; i++) {
    const s = pick(land, 11, 28); if (!s) continue;
    const g = new THREE.Group(), m = new THREE.Mesh(rabSrc.geometry, mat); m.castShadow = true; g.add(m); g.scale.setScalar(1.1);
    scene.add(g);
    rabbits.push({ g, m, p: s.clone(), h: Math.random() * TAU, mode: 'idle', t: rand(1, 5), hop: null, hops: 0, flee: false });
  }
  function rabbitUpdate(r, dt, t) {
    const pp = player();
    const close = pp && Math.hypot(pp.x - r.p.x, pp.z - r.p.y) < 6;
    if (close && !r.flee) { r.flee = true; r.hops = 6; r.h = Math.atan2(r.p.x - pp.x, r.p.y - pp.z) + rand(-0.4, 0.4); r.mode = 'hop'; }
    if (r.hop) {
      r.hop.t += dt / (r.flee ? 0.26 : 0.34);
      const k = Math.min(1, r.hop.t);
      r.p.lerpVectors(r.hop.a, r.hop.b, k);
      r.g.position.set(r.p.x, hAt(r.p.x, r.p.y) + Math.sin(Math.PI * k) * (r.flee ? 0.26 : 0.14), r.p.y);
      r.m.rotation.x = Math.sin(Math.PI * 2 * k) * 0.25;
      if (k >= 1) { r.hop = null; r.hops--; if (r.hops <= 0) { r.mode = 'idle'; r.t = rand(2, 7); r.flee = false; } }
    } else if (r.mode === 'hop') {
      const len = r.flee ? 0.9 : 0.45;
      let nx = r.p.x + Math.sin(r.h) * len, nz = r.p.y + Math.cos(r.h) * len;
      if (!land(nx, nz)) { r.h += rand(1.2, 2.4); nx = r.p.x + Math.sin(r.h) * len; nz = r.p.y + Math.cos(r.h) * len; if (!land(nx, nz)) { r.mode = 'idle'; r.t = 1; return; } }
      r.hop = { t: 0, a: r.p.clone(), b: new THREE.Vector2(nx, nz) };
    } else {
      r.t -= dt;
      r.m.rotation.x = Math.max(0, Math.sin(t * 5 + r.h)) * 0.18;     // nibbling
      if (r.t <= 0) { r.mode = 'hop'; r.hops = (rand(2, 6) | 0); r.h += rand(-1.4, 1.4); }
      r.g.position.set(r.p.x, hAt(r.p.x, r.p.y), r.p.y);
    }
    r.g.rotation.y = r.h;
  }

  /* --------------------------------- birds -------------------------------- */
  const birdMat = new THREE.MeshBasicMaterial({ color: 0x1b2028, side: THREE.DoubleSide });
  const triGeo = (pts) => { const g = new THREE.BufferGeometry(); g.setAttribute('position', new THREE.BufferAttribute(new Float32Array(pts), 3)); return g; };
  const innerGeo = triGeo([0, 0, 0.18, 0.6, 0, 0.12, 0.6, 0, -0.2, 0, 0, 0.18, 0.6, 0, -0.2, 0, 0, -0.16]);
  const outerGeo = triGeo([0, 0, 0.12, 0.75, 0, -0.1, 0, 0, -0.2]);
  const bodyGeo = triGeo([0, 0, 0.45, 0.08, 0, 0, -0.08, 0, 0, 0.08, 0, 0, 0, 0, -0.35, -0.08, 0, 0, 0, 0, -0.35, -0.14, 0, -0.5, 0.14, 0, -0.5]);
  const birds = [];
  for (let i = 0; i < 7; i++) {
    const g = new THREE.Group(); g.add(new THREE.Mesh(bodyGeo, birdMat));
    const wings = [-1, 1].map((sx) => { const inner = new THREE.Group(), outer = new THREE.Group(); inner.scale.x = sx; inner.add(new THREE.Mesh(innerGeo, birdMat)); outer.position.x = 0.6; outer.add(new THREE.Mesh(outerGeo, birdMat)); inner.add(outer); g.add(inner); return { inner, outer, sx }; });
    g.scale.setScalar(1.6); scene.add(g);
    birds.push({ g, wings, r: rand(26, 58), a: Math.random() * TAU, sp: rand(0.04, 0.075) * (Math.random() < 0.5 ? -1 : 1), y: rand(22, 40), ph: Math.random() * 6, glide: 0 });
  }

  /* --------------------------- boat + shooting stars ---------------------- */
  const starMat = new THREE.ShaderMaterial({
    transparent: true, depthWrite: false, blending: THREE.AdditiveBlending, fog: false, uniforms: { uA: { value: 0 } },
    vertexShader: 'varying vec2 vUv; void main(){ vUv = uv; gl_Position = projectionMatrix*modelViewMatrix*vec4(position,1.0); }',
    fragmentShader: 'varying vec2 vUv; uniform float uA; void main(){ float l = pow(vUv.x, 3.0); float w = 1.0 - abs(vUv.y-0.5)*2.0; gl_FragColor = vec4(vec3(0.85,0.92,1.0)*l*w*2.0, l*w*uA); }',
  });
  const star = new THREE.Mesh(new THREE.PlaneGeometry(14, 0.12), starMat); star.visible = false; star.frustumCulled = false; scene.add(star);
  const sstar = { t: 0, life: 0, next: 6, p0: new THREE.Vector3(), v: new THREE.Vector3() };

  /* --------------------------------- update ------------------------------- */
  let boatRip = 4;
  function update(dt, t, env) {
    U_T.value = t;
    const { day, rain } = env;
    ducksUpdate(dt, t);
    koiUpdate(dt, t);
    bflyUpdate(dt, t, day > 0.25 && rain < 0.4);
    frogs.forEach((f) => frogUpdate(f, dt, t));
    dflies.forEach((d) => dflyUpdate(d, dt, t, day > 0.4 && rain < 0.4));
    heronUpdate(dt, t);
    rabbits.forEach((r) => rabbitUpdate(r, dt, t));
    birds.forEach((b) => {
      b.a += b.sp * dt;
      b.g.position.set(Math.cos(b.a) * b.r, b.y + Math.sin(t * 0.6 + b.ph) * 1.5, Math.sin(b.a) * b.r);
      b.g.rotation.set(0, -b.a + (b.sp > 0 ? Math.PI : 0), Math.sign(b.sp) * 0.15);
      const cyc = (t * 0.25 + b.ph) % 4, gliding = cyc > 2.6;
      const f = gliding ? 0.05 : Math.sin(t * 7 + b.ph) * 0.6;
      b.wings.forEach((w) => { w.inner.rotation.z = f; w.outer.rotation.z = gliding ? 0.0 : Math.sin(t * 7 + b.ph - 0.7) * 0.5; });
      b.g.visible = day > 0.3 && rain < 0.5;
    });
    if (boat) {
      boat.position.y = boatY + Math.sin(t * 1.1) * 0.012;
      boat.rotation.z = Math.sin(t * 0.9) * 0.025; boat.rotation.x = Math.sin(t * 0.7 + 1) * 0.02;
      boatRip -= dt; if (boatRip < 0) { boatRip = rand(3, 6); addRipple(boat.position.x, boat.position.z, 0.3); }
    }
    const night = (1 - day) * (1 - rain);
    sstar.next -= dt;
    if (sstar.life <= 0 && sstar.next <= 0 && night > 0.7) {
      sstar.life = 0.9; sstar.t = 0; sstar.next = rand(7, 16);
      const az = Math.random() * TAU;
      sstar.p0.set(Math.cos(az) * 320, rand(120, 220), Math.sin(az) * 320).add(camera.position);
      sstar.v.set(rand(-1, 1), rand(-0.45, -0.2), rand(-1, 1)).normalize().multiplyScalar(260);
    }
    if (sstar.life > 0) {
      sstar.life -= dt; sstar.t += dt; star.visible = true;
      star.position.copy(sstar.p0).addScaledVector(sstar.v, sstar.t);
      star.quaternion.copy(camera.quaternion); star.rotateZ(Math.atan2(sstar.v.y, Math.hypot(sstar.v.x, sstar.v.z)) * 0.6 + 0.2);
      starMat.uniforms.uA.value = Math.sin(Math.PI * Math.min(1, sstar.t / 0.9));
    } else star.visible = false;
  }
  return { update, debug: { hen, drakeA, koi, heron, rabbits, bflies } };
}
