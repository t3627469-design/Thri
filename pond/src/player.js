import * as THREE from 'three';

/* First-person walker: WASD / arrows (+Shift to run), mouse look (pointer lock, or drag when lock is unavailable),
   touch joystick on the left half of the screen and drag-to-look on the right half. */

const EYE = 1.62;

export function initPlayer({ camera, canvas, hAt, WATER_Y, colliders, isUIOpen, getSens }) {
  const coarse = matchMedia('(pointer:coarse)').matches;

  // dock rectangle (same numbers as build_pond.py) so you can walk out over the water
  const th = -Math.PI / 2 - 0.35;
  const R = 8.2 + 1.6 * Math.sin(2 * th + 0.6) + 0.9 * Math.sin(3 * th + 2.0) + 0.5 * Math.sin(5 * th + 1.0);
  const S0 = new THREE.Vector2(R * Math.cos(th), -R * Math.sin(th));
  const U = new THREE.Vector2(-Math.cos(th), Math.sin(th)).normalize();
  const Sd = new THREE.Vector2(-U.y, U.x);
  const onDock = (x, z) => { const rx = x - S0.x, rz = z - S0.y, s = rx * U.x + rz * U.y, l = rx * Sd.x + rz * Sd.y; return s > -1.6 && s < 5.4 && Math.abs(l) < 0.66; };

  const pos = new THREE.Vector3(S0.x - U.x * 1.2, 0, S0.y - U.y * 1.2);   // on the shore at the foot of the dock
  let yaw = Math.atan2(-U.x, -U.y), pitch = -0.12;
  const vel = new THREE.Vector2();
  let eyeY = groundAt(pos.x, pos.z) + EYE, bob = 0, frozen = false, lookFrozen = false;
  const keys = new Set();
  const joy = { id: null, x0: 0, y0: 0, dx: 0, dy: 0 }, look = { id: null, x: 0, y: 0 };
  let locked = false, lockFailed = coarse || !canvas.requestPointerLock;
  let mouseDrag = null;

  function groundAt(x, z) { return onDock(x, z) ? 0.2 : Math.max(hAt(x, z), WATER_Y - 0.3); }
  function blocked(x, z) {
    if (Math.hypot(x, z) > 36) return true;
    if (onDock(x, z)) return false;
    if (hAt(x, z) < WATER_Y - 0.28) return true;           // wade only in the shallows
    for (const c of colliders) { const dx = x - c[0], dz = z - c[1]; if (dx * dx + dz * dz < c[2] * c[2]) return true; }
    return false;
  }
  function turn(dx, dy) {
    const k = 0.0024 * getSens();
    yaw -= dx * k; pitch = Math.max(-1.35, Math.min(1.25, pitch - dy * k));
  }

  /* -------------------------------- input -------------------------------- */
  const typing = (e) => e.target && /INPUT|TEXTAREA|SELECT/.test(e.target.tagName);
  addEventListener('keydown', (e) => { if (!typing(e)) keys.add(e.code); });
  addEventListener('keyup', (e) => keys.delete(e.code));
  addEventListener('blur', () => keys.clear());
  document.addEventListener('pointerlockchange', () => { locked = document.pointerLockElement === canvas; document.body.classList.toggle('locked', locked); });
  document.addEventListener('pointerlockerror', () => { lockFailed = true; });
  document.addEventListener('mousemove', (e) => { if (locked && !lookFrozen) turn(e.movementX, e.movementY); });

  canvas.addEventListener('pointerdown', (e) => {
    if (e.pointerType === 'touch') {
      if (e.clientX < innerWidth * 0.45 && joy.id === null) { joy.id = e.pointerId; joy.x0 = e.clientX; joy.y0 = e.clientY; joy.dx = joy.dy = 0; showJoy(true); }
      else if (look.id === null) { look.id = e.pointerId; look.x = e.clientX; look.y = e.clientY; }
    } else if (!locked && e.button === 0) mouseDrag = { x: e.clientX, y: e.clientY };
  });
  addEventListener('pointermove', (e) => {
    if (e.pointerId === joy.id) {
      joy.dx = Math.max(-60, Math.min(60, e.clientX - joy.x0)); joy.dy = Math.max(-60, Math.min(60, e.clientY - joy.y0));
      knob.style.transform = `translate(${joy.dx}px, ${joy.dy}px)`;
    } else if (e.pointerId === look.id) {
      if (!lookFrozen) turn((e.clientX - look.x) * 1.6, (e.clientY - look.y) * 1.6);
      look.x = e.clientX; look.y = e.clientY;
    } else if (mouseDrag && !locked) {
      if (!lookFrozen) turn(e.clientX - mouseDrag.x, e.clientY - mouseDrag.y);
      mouseDrag.x = e.clientX; mouseDrag.y = e.clientY;
    }
  });
  const end = (e) => {
    if (e.pointerId === joy.id) { joy.id = null; joy.dx = joy.dy = 0; showJoy(false); }
    if (e.pointerId === look.id) look.id = null;
    if (e.pointerType !== 'touch') mouseDrag = null;
  };
  addEventListener('pointerup', end); addEventListener('pointercancel', end);

  // touch joystick visuals
  const base = document.createElement('div'); base.id = 'joy';
  const knob = document.createElement('div'); knob.id = 'joyKnob'; base.appendChild(knob); document.body.appendChild(base);
  function showJoy(on) {
    base.classList.toggle('on', on);
    if (on) { base.style.left = joy.x0 + 'px'; base.style.top = joy.y0 + 'px'; knob.style.transform = 'translate(0,0)'; }
  }

  /* -------------------------------- update ------------------------------- */
  const fwd = new THREE.Vector3();
  function update(dt) {
    let mx = 0, mz = 0;
    if (!frozen && !isUIOpen()) {
      if (keys.has('KeyW') || keys.has('ArrowUp')) mz += 1;
      if (keys.has('KeyS') || keys.has('ArrowDown')) mz -= 1;
      if (keys.has('KeyA') || keys.has('ArrowLeft')) mx -= 1;
      if (keys.has('KeyD') || keys.has('ArrowRight')) mx += 1;
      if (joy.id !== null) { mx += joy.dx / 60; mz -= joy.dy / 60; }
    }
    const len = Math.hypot(mx, mz);
    if (len > 1) { mx /= len; mz /= len; }
    const run = keys.has('ShiftLeft') || keys.has('ShiftRight') || (joy.id !== null && Math.hypot(joy.dx, joy.dy) > 55);
    const speed = run ? 5.2 : 2.7;
    const sx = Math.sin(yaw), cz = Math.cos(yaw);
    // forward is -Z in camera space: world forward = (-sin yaw, -cos yaw)
    const tx = (-sx * mz + cz * mx) * speed, tz = (-cz * mz - sx * mx) * speed;
    const k = 1 - Math.exp(-dt * 10);
    vel.x += (tx - vel.x) * k; vel.y += (tz - vel.y) * k;
    const nx = pos.x + vel.x * dt, nz = pos.z + vel.y * dt;
    if (!blocked(nx, nz)) { pos.x = nx; pos.z = nz; }
    else if (!blocked(nx, pos.z)) { pos.x = nx; vel.y *= 0.5; }
    else if (!blocked(pos.x, nz)) { pos.z = nz; vel.x *= 0.5; }
    else vel.set(0, 0);
    const moving = Math.hypot(vel.x, vel.y);
    bob += dt * moving * 2.2;
    const target = groundAt(pos.x, pos.z) + EYE + Math.sin(bob * 2) * 0.035 * Math.min(1, moving / 2.7);
    eyeY += (target - eyeY) * (1 - Math.exp(-dt * 14));
    pos.y = eyeY;
  }
  function apply() {
    camera.position.copy(pos);
    camera.rotation.set(pitch, yaw, 0, 'YXZ');
  }

  return {
    update, apply, pos,
    get yaw() { return yaw; }, get pitch() { return pitch; },
    eye: () => pos,
    forward: () => fwd.set(-Math.sin(yaw) * Math.cos(pitch), Math.sin(pitch), -Math.cos(yaw) * Math.cos(pitch)),
    freeze(b) { frozen = b; lookFrozen = b; if (b) vel.set(0, 0); },
    get locked() { return locked; }, get canLock() { return !lockFailed; },
    requestLock() { if (lockFailed || locked) return; try { const r = canvas.requestPointerLock(); if (r && r.catch) r.catch(() => { lockFailed = true; }); } catch (e) { lockFailed = true; } },
    exitLock() { if (locked) document.exitPointerLock(); },
    get moving() { return Math.hypot(vel.x, vel.y) > 0.3; },
  };
}
