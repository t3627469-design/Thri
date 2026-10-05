import * as THREE from 'three';

/* Everything alive in the scene: ducks, koi, butterflies, frogs, dragonflies, birds, the rowboat, shooting stars. */

const rand = (a, b) => a + Math.random() * (b - a);
const heading = (x0, z0, x1, z1) => Math.atan2(x1 - x0, z1 - z0);
const hen = (t) => [-1.6 + Math.sin(t * 0.13) * 2.4 + Math.sin(t * 0.31) * 0.5, 1.8 + Math.sin(t * 0.17 + 1.0) * 1.9];
const drake = (t) => [1.4 + Math.sin(t * 0.11 + 2.0) * 2.3, -0.8 + Math.sin(t * 0.15) * 2.2 + Math.sin(t * 0.4) * 0.3];
const koiPath = (i, t) => {
  const a = t * (0.18 + i * 0.025) + i * 1.9, r = 1.4 + (i % 3) * 0.55;
  return [Math.cos(a) * r * 1.35 + Math.sin(t * 0.2 + i) * 0.5, Math.sin(a * 1.1) * r];
};

export function initCreatures(ctx) {
  const { root, scene, camera, addRipple, hAt, WATER_Y, audio } = ctx;
  const by = (n) => root.getObjectByName(n);
  const mat = new THREE.MeshLambertMaterial({ vertexColors: true, side: THREE.DoubleSide });
  root.traverse((o) => { if (o.isMesh && /^(Duck|Koi|Bfly|Frog|Dfly)/.test(o.name)) { o.material = mat; o.castShadow = /^Duck/.test(o.name); } });

  const ducks = { drake: by('Duck_Drake'), hen: by('Duck_Hen'), chicks: [0, 1, 2].map((i) => by('Duck_Chick' + i)) };
  const koi = [0, 1, 2, 3, 4].map((i) => by('Koi_' + i));
  let pads = [];
  try { pads = JSON.parse((by('PondMeta') && by('PondMeta').userData.pads) || '[]').map(([x, y, r]) => ({ x, z: -y, r })); } catch (e) { /* no pads */ }

  /* ------------------------------ butterflies ---------------------------- */
  const bw = [by('BflyWingL'), by('BflyWingR'), by('BflyBody')];
  bw.forEach((o) => { o.visible = false; });
  const perms = [[0, 1, 2], [0, 2, 1], [2, 1, 0], [1, 0, 2], [1, 2, 0], [0, 1, 2]];
  const bflies = [];
  for (let i = 0; i < 6; i++) {
    const grp = new THREE.Group(), p = perms[i];
    const parts = bw.map((src, k) => {
      const g = src.geometry.clone();
      if (k < 2 && i > 0) {
        const c = g.attributes.color;
        for (let v = 0; v < c.count; v++) { const r = [c.getX(v), c.getY(v), c.getZ(v)]; c.setXYZ(v, r[p[0]], r[p[1]], r[p[2]]); }
      }
      const m = new THREE.Mesh(g, mat); m.position.set(0, 0, 0); return m;
    });
    parts.forEach((m) => grp.add(m));
    grp.userData = { wl: parts[0], wr: parts[1], t: Math.random() * 100, cx: (Math.random() - 0.5) * 22, cz: (Math.random() - 0.5) * 22 + 3, r: 3 + Math.random() * 5, s: 0.18 + Math.random() * 0.14, ph: Math.random() * 6, fl: 16 + Math.random() * 6 };
    grp.scale.setScalar(2.1); scene.add(grp); bflies.push(grp);
  }

  /* --------------------------------- frogs -------------------------------- */
  const frogSrc = by('Frog'); if (frogSrc) frogSrc.visible = false;
  const frogs = [];
  const bigPads = pads.filter((p) => p.r > 0.34);
  for (let i = 0; i < 4 && frogSrc && bigPads.length > 4; i++) {
    const g = new THREE.Group(), m = new THREE.Mesh(frogSrc.geometry, mat); m.castShadow = true; g.add(m);
    g.scale.setScalar(1.6);
    const pad = bigPads[(Math.random() * bigPads.length) | 0];
    g.position.set(pad.x, WATER_Y + 0.045, pad.z); g.rotation.y = Math.random() * 6.28;
    scene.add(g);
    frogs.push({ g, m, pad, next: rand(1, 6), hop: null, ph: Math.random() * 6 });
  }
  function frogUpdate(f, dt, t) {
    if (f.hop) {
      f.hop.t += dt / 0.7;
      const k = Math.min(1, f.hop.t);
      f.g.position.set(f.hop.a.x + (f.hop.b.x - f.hop.a.x) * k, WATER_Y + 0.045 + Math.sin(Math.PI * k) * 0.38, f.hop.a.z + (f.hop.b.z - f.hop.a.z) * k);
      f.m.scale.set(1, 1 + Math.sin(Math.PI * k) * 0.25, 1 - Math.sin(Math.PI * k) * 0.1);
      if (k >= 1) {
        f.hop = null; f.pad = f.hop2; f.m.scale.set(1, 1, 1);
        addRipple(f.g.position.x, f.g.position.z, 0.45);
        if (audio.on && Math.random() < 0.55 && camera.position.distanceTo(f.g.position) < 20) audio.croak();
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
      f.g.rotation.y = heading(f.hop.a.x, f.hop.a.z, dst.x, dst.z);
    }
  }

  /* ------------------------------- dragonflies ---------------------------- */
  const dBody = by('DflyBody'), dWing = by('DflyWing');
  if (dBody) dBody.visible = false; if (dWing) dWing.visible = false;
  const wingMat = new THREE.MeshBasicMaterial({ color: 0xdcefff, transparent: true, opacity: 0.5, side: THREE.DoubleSide, depthWrite: false });
  const dflies = [];
  for (let i = 0; i < 3 && dBody && dWing; i++) {
    const g = new THREE.Group();
    g.add(new THREE.Mesh(dBody.geometry, mat));
    const wings = [];
    for (const [sx, z] of [[1, 0.012], [-1, 0.012], [1, -0.022], [-1, -0.022]]) {
      const w = new THREE.Mesh(dWing.geometry, wingMat); w.scale.x = sx; w.position.z = z; g.add(w); wings.push([w, sx]);
    }
    g.scale.setScalar(1.9);
    const pos = new THREE.Vector3(rand(-5, 5), WATER_Y + 1, rand(-5, 5));
    g.position.copy(pos); scene.add(g);
    dflies.push({ g, wings, pos, wp: pos.clone(), mode: 'hover', t: rand(0.5, 2), ph: Math.random() * 9, yaw: 0 });
  }
  function dflyUpdate(d, dt, t, show) {
    d.g.visible = show;
    if (!show) return;
    d.t -= dt;
    if (d.mode === 'hover') {
      d.pos.x += Math.sin(t * 3.1 + d.ph) * 0.003; d.pos.y += Math.sin(t * 5.3 + d.ph) * 0.002;
      if (d.t <= 0) {
        d.mode = 'dart'; const a = Math.random() * 6.28, r = rand(2.5, 8.5);
        d.wp.set(Math.cos(a) * r, WATER_Y + rand(0.5, 1.7), Math.sin(a) * r); d.t = 4;
      }
    } else {
      const dir = d.wp.clone().sub(d.pos), dist = dir.length();
      if (dist < 0.15 || d.t <= 0) { d.mode = 'hover'; d.t = rand(1, 3.5); }
      else { dir.normalize().multiplyScalar(Math.min(dist * 3, 5.2) * dt); d.pos.add(dir); d.yaw = Math.atan2(dir.x, dir.z); }
    }
    d.g.position.copy(d.pos);
    d.g.rotation.y += (d.yaw - d.g.rotation.y) * Math.min(1, dt * 8);
    const fl = Math.sin(t * 70 + d.ph) * 0.55;
    d.wings.forEach(([w, sx], i) => { w.rotation.z = sx * (i > 1 ? -fl : fl); });
  }

  /* --------------------------------- birds -------------------------------- */
  const wingGeo = new THREE.BufferGeometry();
  wingGeo.setAttribute('position', new THREE.BufferAttribute(new Float32Array([0, 0, 0, 1.3, 0, 0.35, 0.7, 0, -0.4]), 3));
  wingGeo.computeVertexNormals();
  const birdMat = new THREE.MeshBasicMaterial({ color: 0x1b2028, side: THREE.DoubleSide });
  const birds = [];
  for (let i = 0; i < 7; i++) {
    const g = new THREE.Group(), wl = new THREE.Mesh(wingGeo, birdMat), wr = new THREE.Mesh(wingGeo, birdMat);
    wr.scale.x = -1; g.add(wl, wr); g.scale.setScalar(1.5);
    scene.add(g);
    birds.push({ g, wl, wr, r: rand(26, 58), a: Math.random() * 6.28, sp: rand(0.04, 0.075) * (Math.random() < 0.5 ? -1 : 1), y: rand(22, 40), ph: Math.random() * 6 });
  }

  /* --------------------------- boat + shooting stars ---------------------- */
  const boat = by('Boat'); const boatY = boat ? boat.position.y : 0;
  const starMat = new THREE.ShaderMaterial({
    transparent: true, depthWrite: false, blending: THREE.AdditiveBlending, fog: false, uniforms: { uA: { value: 0 } },
    vertexShader: 'varying vec2 vUv; void main(){ vUv = uv; gl_Position = projectionMatrix*modelViewMatrix*vec4(position,1.0); }',
    fragmentShader: 'varying vec2 vUv; uniform float uA; void main(){ float l = pow(vUv.x, 3.0); float w = 1.0 - abs(vUv.y-0.5)*2.0; gl_FragColor = vec4(vec3(0.85,0.92,1.0)*l*w*2.0, l*w*uA); }',
  });
  const star = new THREE.Mesh(new THREE.PlaneGeometry(14, 0.12), starMat); star.visible = false; star.frustumCulled = false; scene.add(star);
  const sstar = { t: 0, life: 0, next: 6, p0: new THREE.Vector3(), v: new THREE.Vector3() };

  /* --------------------------------- update ------------------------------- */
  let nextFish = 3, rippleTimer = 0, boatRip = 4;
  function update(dt, t, env) {
    const { drake: dk, hen: hn, chicks } = ducks;
    if (dk) {
      const [x, z] = drake(t), [x2, z2] = drake(t + 0.1);
      dk.position.set(x, WATER_Y + Math.sin(t * 1.7) * 0.012, z); dk.rotation.y = heading(x, z, x2, z2); dk.rotation.z = Math.sin(t * 1.3) * 0.02;
      const [hx, hz] = hen(t), [hx2, hz2] = hen(t + 0.1);
      hn.position.set(hx, WATER_Y + Math.sin(t * 1.5 + 1) * 0.012, hz); hn.rotation.y = heading(hx, hz, hx2, hz2); hn.rotation.z = Math.sin(t * 1.1) * 0.02;
      chicks.forEach((c, i) => {
        const tl = t - 1.1 - i * 0.9, [cx, cz] = hen(tl), [cx2, cz2] = hen(tl + 0.1);
        c.position.set(cx + Math.sin(i * 2.1) * 0.25, WATER_Y + Math.sin(t * 2.2 + i) * 0.01, cz + Math.cos(i * 2.1) * 0.25);
        c.rotation.y = heading(cx, cz, cx2, cz2);
      });
      rippleTimer -= dt;
      if (rippleTimer < 0) { rippleTimer = 0.85; addRipple(x, z, 0.5); addRipple(hx, hz, 0.45); }
    }
    koi.forEach((k, i) => {
      const [x, z] = koiPath(i, t), [x2, z2] = koiPath(i, t + 0.1);
      k.position.set(x, -0.36 - (i % 2) * 0.12 + Math.sin(t * 0.7 + i) * 0.03, z);
      k.rotation.y = heading(x, z, x2, z2) + Math.sin(t * 5 + i * 3) * 0.14;
      k.rotation.z = Math.sin(t * 5 + i * 3 + 1) * 0.06;
    });
    const day = env.day, rain = env.rain;
    bflies.forEach((b) => {
      const d = b.userData, tt = t * d.s + d.t;
      const x = d.cx + Math.sin(tt + d.ph) * d.r + Math.sin(tt * 2.3) * 1.2, z = d.cz + Math.cos(tt * 0.83) * d.r;
      const x2 = d.cx + Math.sin(tt + 0.02 + d.ph) * d.r + Math.sin((tt + 0.02) * 2.3) * 1.2, z2 = d.cz + Math.cos((tt + 0.02) * 0.83) * d.r;
      const y = Math.max(hAt(x, z), WATER_Y) + 0.9 + Math.sin(tt * 3.1) * 0.35 + Math.sin(t * 2 + d.ph) * 0.1;
      b.position.set(x, y, z); b.rotation.y = heading(x, z, x2, z2);
      const fl = 0.25 + Math.sin(t * d.fl + d.ph) * 0.85;
      d.wl.rotation.z = fl; d.wr.rotation.z = -fl;
      b.visible = day > 0.25 && rain < 0.4;
    });
    frogs.forEach((f) => frogUpdate(f, dt, t));
    dflies.forEach((d) => dflyUpdate(d, dt, t, day > 0.4 && rain < 0.4));
    birds.forEach((b) => {
      b.a += b.sp * dt;
      const x = Math.cos(b.a) * b.r, z = Math.sin(b.a) * b.r;
      b.g.position.set(x, b.y + Math.sin(t * 0.6 + b.ph) * 1.5, z);
      b.g.rotation.y = -b.a + (b.sp > 0 ? Math.PI : 0);
      const f = Math.sin(t * 6 + b.ph) * 0.55; b.wl.rotation.z = f; b.wr.rotation.z = -f;
      b.g.visible = day > 0.3 && rain < 0.5;
    });
    if (boat) {
      boat.position.y = boatY + Math.sin(t * 1.1) * 0.012;
      boat.rotation.z = Math.sin(t * 0.9) * 0.025; boat.rotation.x = Math.sin(t * 0.7 + 1) * 0.02;
      boatRip -= dt; if (boatRip < 0) { boatRip = rand(3, 6); addRipple(boat.position.x, boat.position.z, 0.3); }
    }
    nextFish -= dt;
    if (nextFish < 0) {
      nextFish = 3 + Math.random() * 5;
      const a = Math.random() * 6.28, r = Math.random() * 3.6;
      addRipple(Math.cos(a) * r, Math.sin(a) * r, 0.9);
      if (audio.on) audio.pluck(1046 * (Math.random() < 0.5 ? 1 : 1.5), 0.035);
    }
    // shooting stars on clear nights
    const night = (1 - day) * (1 - rain);
    sstar.next -= dt;
    if (sstar.life <= 0 && sstar.next <= 0 && night > 0.7) {
      sstar.life = 0.9; sstar.t = 0; sstar.next = rand(7, 16);
      const az = Math.random() * 6.28, d = 320;
      sstar.p0.set(Math.cos(az) * d, rand(120, 220), Math.sin(az) * d).add(camera.position);
      sstar.v.set(rand(-1, 1), rand(-0.45, -0.2), rand(-1, 1)).normalize().multiplyScalar(260);
    }
    if (sstar.life > 0) {
      sstar.life -= dt; sstar.t += dt;
      star.visible = true;
      star.position.copy(sstar.p0).addScaledVector(sstar.v, sstar.t);
      star.quaternion.copy(camera.quaternion);
      const ang = Math.atan2(sstar.v.y, Math.hypot(sstar.v.x, sstar.v.z));
      star.rotateZ(ang * 0.6 + 0.2);
      starMat.uniforms.uA.value = Math.sin(Math.PI * Math.min(1, sstar.t / 0.9));
    } else star.visible = false;
  }
  return { update };
}
