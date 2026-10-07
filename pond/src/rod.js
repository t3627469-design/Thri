import * as THREE from 'three';

/* A fishing rod built like a real one: butt cap, cork grips, reel seat, a spinning reel (spool, bail, crank),
   a tapered blank in sections that bend under load, line guides with thread wraps and a tip ring.
   Local +Y runs along the rod; local -Z is the underside where the reel and guides hang. */

const L_BLANK = 1.82, BLANK0 = 0.52, SEGS = 9;
const GUIDES = [[0.1, 0.024], [0.24, 0.016], [0.39, 0.012], [0.53, 0.0095], [0.66, 0.008], [0.78, 0.0068], [0.89, 0.006], [0.965, 0.0052]];
const blankR = (k) => 0.0088 - 0.0068 * Math.pow(k, 0.9);       // k = 0 at butt of blank, 1 at tip

const STYLES = {
  bamboo:   { base: '#c9a35a', alt: '#8a6a32', wrap: 0x3a2412, reel: 0x7a5a36, metal: 0xb8935a, kind: 'nodes' },
  willow:   { base: '#5a6a34', alt: '#3a4420', wrap: 0xd8b04a, reel: 0x30343a, metal: 0xc9a96a, kind: 'grain' },
  carbon:   { base: '#1c1e22', alt: '#33373e', wrap: 0xd8322a, reel: 0x1a1a1e, metal: 0xc8ccd2, kind: 'weave' },
  moonwood: { base: '#aebfe6', alt: '#e8f0ff', wrap: 0xdfe6f0, reel: 0x8b97b0, metal: 0xe6ecf4, kind: 'glow', glow: 0x6f8fff },
  koi:      { base: '#f06a2a', alt: '#fff2e6', wrap: 0xffcf5a, reel: 0xb8462a, metal: 0xffcf5a, kind: 'patches', glow: 0xff7a3a },
  dragon:   { base: '#1fa37a', alt: '#f4d36a', wrap: 0xf4d36a, reel: 0x125a44, metal: 0xf4d36a, kind: 'bands', glow: 0x2fd6a1 },
};

function blankTexture(st) {
  const c = document.createElement('canvas'); c.width = 64; c.height = 512;
  const g = c.getContext('2d');
  g.fillStyle = st.base; g.fillRect(0, 0, 64, 512);
  if (st.kind === 'nodes') {                                       // bamboo: nodes and faint streaks
    for (let x = 0; x < 64; x += 4) { g.fillStyle = `rgba(90,60,20,${0.05 + Math.random() * 0.08})`; g.fillRect(x, 0, 2, 512); }
    for (let y = 40; y < 512; y += 70 + Math.random() * 25) { g.fillStyle = st.alt; g.fillRect(0, y, 64, 5); g.fillStyle = 'rgba(255,240,200,.35)'; g.fillRect(0, y + 5, 64, 2); }
  } else if (st.kind === 'grain') {
    for (let i = 0; i < 40; i++) { g.strokeStyle = `rgba(30,36,14,${0.15 + Math.random() * 0.2})`; g.lineWidth = 1 + Math.random(); g.beginPath(); const x = Math.random() * 64; g.moveTo(x, 0); g.bezierCurveTo(x + 6, 170, x - 6, 340, x + 3, 512); g.stroke(); }
  } else if (st.kind === 'weave') {
    for (let y = 0; y < 512; y += 8) for (let x = 0; x < 64; x += 8) { g.fillStyle = ((x + y) / 8) % 2 ? st.alt : st.base; g.fillRect(x, y, 8, 8); g.fillStyle = 'rgba(255,255,255,.05)'; g.fillRect(x, y, 8, 2); }
  } else if (st.kind === 'glow') {
    const gr = g.createLinearGradient(0, 512, 0, 0); gr.addColorStop(0, st.base); gr.addColorStop(1, st.alt); g.fillStyle = gr; g.fillRect(0, 0, 64, 512);
    for (let i = 0; i < 60; i++) { g.fillStyle = 'rgba(255,255,255,.5)'; g.fillRect(Math.random() * 64, Math.random() * 512, 1.5, 1.5); }
  } else if (st.kind === 'patches') {
    for (let i = 0; i < 14; i++) { g.fillStyle = st.alt; g.beginPath(); g.ellipse(Math.random() * 64, Math.random() * 512, 10 + Math.random() * 18, 18 + Math.random() * 30, 0, 0, 7); g.fill(); }
  } else if (st.kind === 'bands') {
    for (let y = 30; y < 512; y += 64) { g.fillStyle = st.alt; g.fillRect(0, y, 64, 3); }
    for (let i = 0; i < 80; i++) { g.fillStyle = 'rgba(200,255,230,.25)'; g.fillRect(Math.random() * 64, Math.random() * 512, 2, 6); }
  }
  const t = new THREE.CanvasTexture(c);
  t.colorSpace = THREE.SRGBColorSpace; t.wrapS = THREE.RepeatWrapping; t.anisotropy = 4;
  return t;
}
function corkTexture() {
  const c = document.createElement('canvas'); c.width = 128; c.height = 128;
  const g = c.getContext('2d');
  g.fillStyle = '#c69c64'; g.fillRect(0, 0, 128, 128);
  for (let i = 0; i < 900; i++) { const s = Math.random() * 2.4 + 0.4; g.fillStyle = Math.random() < 0.6 ? `rgba(110,72,34,${0.3 + Math.random() * 0.5})` : `rgba(240,214,170,${0.3 + Math.random() * 0.4})`; g.fillRect(Math.random() * 128, Math.random() * 128, s, s * (0.6 + Math.random())); }
  for (let y = 0; y < 128; y += 16) { g.fillStyle = 'rgba(90,60,30,.35)'; g.fillRect(0, y, 128, 1); }
  const t = new THREE.CanvasTexture(c); t.colorSpace = THREE.SRGBColorSpace; t.wrapS = t.wrapT = THREE.RepeatWrapping; t.repeat.set(2, 3);
  return t;
}

export function createRod() {
  const root = new THREE.Group();
  const phong = (color, shin = 60, extra = {}) => new THREE.MeshPhongMaterial({ color, shininess: shin, specular: 0x555555, ...extra });
  const blankMat = phong(0xffffff, 80, { specular: 0x888888 });
  const corkMat = new THREE.MeshLambertMaterial({ map: corkTexture() });
  const metalMat = phong(0xc8ccd2, 110, { specular: 0xffffff });
  const darkMat = phong(0x16171a, 50);
  const wrapMat = phong(0x3a2412, 70);
  const reelMat = phong(0x30343a, 70, { specular: 0x999999 });
  const lineSpool = phong(0xe8e8e0, 20);
  const cyl = (r0, r1, h, seg = 14) => new THREE.CylinderGeometry(r1, r0, h, seg, 1).translate(0, h / 2, 0);
  const at = (mesh, y, x = 0, z = 0) => { mesh.position.set(x, y, z); return mesh; };

  // handle
  root.add(at(new THREE.Mesh(new THREE.SphereGeometry(0.019, 12, 8, 0, Math.PI * 2, Math.PI / 2, Math.PI / 2).scale(1, 0.6, 1), darkMat), 0));   // butt cap
  root.add(at(new THREE.Mesh(cyl(0.019, 0.017, 0.27), corkMat), 0));
  root.add(at(new THREE.Mesh(cyl(0.0135, 0.0135, 0.12), metalMat), 0.27));                     // reel seat
  root.add(at(new THREE.Mesh(cyl(0.016, 0.0165, 0.012), metalMat), 0.27));
  root.add(at(new THREE.Mesh(cyl(0.0165, 0.016, 0.012), metalMat), 0.378));
  root.add(at(new THREE.Mesh(cyl(0.016, 0.0125, 0.13), corkMat), 0.39));                       // fore grip
  root.add(at(new THREE.Mesh(cyl(0.013, 0.0105, 0.012), wrapMat), 0.515));                     // winding check

  // spinning reel hanging below the seat
  const reel = new THREE.Group(); reel.position.set(0, 0.33, -0.012); root.add(reel);
  reel.add(at(new THREE.Mesh(new THREE.BoxGeometry(0.01, 0.07, 0.006), metalMat), 0, 0, -0.003));                // reel foot
  reel.add(at(new THREE.Mesh(new THREE.BoxGeometry(0.008, 0.012, 0.05).translate(0, 0, -0.025), reelMat), 0));   // stem
  const body = new THREE.Mesh(new THREE.SphereGeometry(0.028, 16, 12).scale(0.8, 1.15, 0.95), reelMat); body.position.set(0, -0.005, -0.068); reel.add(body);
  const spool = new THREE.Group(); spool.position.set(0, 0.03, -0.068); reel.add(spool);
  spool.add(new THREE.Mesh(cyl(0.026, 0.026, 0.006), metalMat), at(new THREE.Mesh(cyl(0.021, 0.021, 0.026), lineSpool), 0.006), at(new THREE.Mesh(cyl(0.025, 0.025, 0.004), metalMat), 0.032));
  const bail = new THREE.Mesh(new THREE.TorusGeometry(0.03, 0.0018, 6, 24, Math.PI * 1.15), metalMat);
  bail.rotation.set(Math.PI / 2, 0, -0.3); bail.position.set(0, 0.048, -0.068); reel.add(bail);
  const crank = new THREE.Group(); crank.position.set(0.024, -0.005, -0.068); reel.add(crank);
  crank.add(new THREE.Mesh(new THREE.CylinderGeometry(0.004, 0.004, 0.012, 8).rotateZ(Math.PI / 2).translate(0.006, 0, 0), metalMat));
  const arm = new THREE.Mesh(new THREE.BoxGeometry(0.006, 0.006, 0.05).translate(0.013, 0, -0.022), metalMat); crank.add(arm);
  const knob = new THREE.Mesh(new THREE.CylinderGeometry(0.0075, 0.0075, 0.02, 10).rotateZ(Math.PI / 2).translate(0.024, 0, -0.045), darkMat); crank.add(knob);
  const spoolFront = new THREE.Vector3(0, 0.062, -0.068);

  // blank: chained sections so the rod can bend
  const segLen = L_BLANK / SEGS;
  const segs = [];
  let parent = root;
  const guideObjs = [];
  for (let i = 0; i < SEGS; i++) {
    const s = new THREE.Group();
    s.position.y = i === 0 ? BLANK0 : segLen;
    parent.add(s); segs.push(s); parent = s;
    const k0 = i / SEGS, k1 = (i + 1) / SEGS;
    const geo = cyl(blankR(k0), blankR(k1), segLen + 0.002, 12);
    const uv = geo.attributes.uv, pos = geo.attributes.position;
    for (let v = 0; v < uv.count; v++) uv.setY(v, (k0 + pos.getY(v) / L_BLANK) * 1.0);
    s.add(new THREE.Mesh(geo, blankMat));
  }
  if (true) { const ferr = new THREE.Mesh(cyl(blankR(0.48) * 1.25, blankR(0.5) * 1.2, 0.03), wrapMat); segs[4].add(at(ferr, segLen * 0.25)); }
  // guides: ring + two feet + thread wrap, attached to the section they sit on
  for (const [k, r] of GUIDES) {
    const along = k * L_BLANK, si = Math.min(SEGS - 1, Math.floor(along / segLen)), local = along - si * segLen;
    const br = blankR(k), standoff = br + r + 0.004;
    const g = new THREE.Group(); g.position.y = local; segs[si].add(g);
    const ring = new THREE.Mesh(new THREE.TorusGeometry(r, Math.max(0.0012, r * 0.13), 6, 18), metalMat);
    ring.position.z = -standoff; g.add(ring);                  // ring plane faces along the rod (line passes through)
    ring.rotation.x = Math.PI / 2;
    const foot = new THREE.Mesh(new THREE.CylinderGeometry(0.0012, 0.0012, standoff - r + 0.002, 4).rotateX(Math.PI / 2).translate(0, 0, -(standoff - r) / 2), metalMat);
    g.add(foot);
    g.add(at(new THREE.Mesh(cyl(br * 1.35, br * 1.35, 0.018, 10), wrapMat), -0.009));
    guideObjs.push({ g, z: -standoff });
  }
  const tipTop = new THREE.Mesh(new THREE.TorusGeometry(0.0045, 0.0011, 6, 12), metalMat);
  tipTop.position.set(0, segLen, -0.004); tipTop.rotation.x = Math.PI / 2; segs[SEGS - 1].add(tipTop);
  const tipLocal = new THREE.Vector3(0, segLen + 0.002, -0.004);

  /* ------------------------------ api ------------------------------------ */
  const texCache = {};
  function setStyle(id, rodDef) {
    const st = STYLES[id] || STYLES.bamboo;
    if (!texCache[id]) texCache[id] = blankTexture(st);
    blankMat.map = texCache[id]; blankMat.needsUpdate = true;
    blankMat.emissive.set(st.glow || 0x000000); blankMat.emissiveIntensity = st.glow ? 0.35 : 0;
    blankMat.emissiveMap = st.glow ? texCache[id] : null;
    wrapMat.color.set(st.wrap); reelMat.color.set(st.reel); metalMat.color.set(st.metal);
  }
  let bend = 0, crankA = 0;
  function update(dt, targetBend, reeling) {
    bend += (targetBend - bend) * Math.min(1, dt * 10);
    segs.forEach((s, i) => { s.rotation.x = -bend * (0.04 + 0.16 * Math.pow((i + 1) / SEGS, 1.6)); });
    if (reeling) crankA += dt * 14;
    crank.rotation.x = crankA;
  }
  function linePath(out) {             // world positions: spool -> each guide -> tip
    root.updateMatrixWorld(true);
    const get = (i) => out[i] || (out[i] = new THREE.Vector3());
    let n = 0;
    get(n++).copy(spoolFront).applyMatrix4(reel.matrixWorld);
    for (const go of guideObjs) get(n++).set(0, 0, go.z).applyMatrix4(go.g.matrixWorld);
    get(n++).copy(tipLocal).applyMatrix4(segs[SEGS - 1].matrixWorld);
    return n;
  }
  function tip(v) { root.updateMatrixWorld(true); return v.copy(tipLocal).applyMatrix4(segs[SEGS - 1].matrixWorld); }
  root.traverse((o) => { if (o.isMesh) { o.castShadow = false; o.receiveShadow = false; } });
  return { group: root, setStyle, update, linePath, tip, length: BLANK0 + L_BLANK };
}
