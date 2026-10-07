import * as THREE from 'three';
import { GLTFLoader } from 'three/addons/loaders/GLTFLoader.js';
import { EffectComposer } from 'three/addons/postprocessing/EffectComposer.js';
import { RenderPass } from 'three/addons/postprocessing/RenderPass.js';
import { UnrealBloomPass } from 'three/addons/postprocessing/UnrealBloomPass.js';
import { OutputPass } from 'three/addons/postprocessing/OutputPass.js';
import { ShaderPass } from 'three/addons/postprocessing/ShaderPass.js';
import { NOISE, SKY_FN } from './glsl.js';
import { Ambience } from './audio.js';
import { initFishing } from './fishing.js';
import { initCreatures } from './creatures.js';
import { initWeather } from './weather.js';
import { initUI, open as openPanel, toast } from './ui.js';
import { icon } from './icons.js';
import * as G from './game.js';
import { initPlayer } from './player.js';

/* =====================================================================
   Still Water. Geometry comes from Blender (pond.glb); everything alive
   (water, sky, wind, light, creatures, weather, sound, the game) is added here.
   ===================================================================== */

const WATER_Y = -0.2;            // WATER_Z in build_pond.py
const EXT = 46, GRID = 230;      // terrain extent / cells
const clamp = (x, a, b) => Math.max(a, Math.min(b, x));
const lerp = (a, b, t) => a + (b - a) * t;
const $ = (id) => document.getElementById(id);
const params = new URLSearchParams(location.search);

/* ------------------------------- quality --------------------------------- */
const QL = {
  low:  { pr: 0.75, shadow: 0,    every: 6, refl: 0,    reflEvery: 0, bloom: false, bloomScale: 0.5, msaa: 0, grass: 22, mist: 0, rays: false, flies: 180, post: true },
  med:  { pr: 1.0,  shadow: 2048, every: 4, refl: 0.33, reflEvery: 3, bloom: true,  bloomScale: 0.5, msaa: 0, grass: 34, mist: 1, rays: true,  flies: 360, post: true },
  high: { pr: 1.5,  shadow: 3072, every: 2, refl: 0.5,  reflEvery: 2, bloom: true,  bloomScale: 1.0, msaa: 0, grass: 52, mist: 2, rays: true,  flies: 520, post: true },
};
const LEVELS = ['low', 'med', 'high'];
const coarse = matchMedia('(pointer:coarse)').matches || /Mobi|Android/i.test(navigator.userAgent);
const forced = params.get('q') && QL[params.get('q')] ? params.get('q') : null;
const lite = params.has('lite');
let qMode = forced || (lite ? 'low' : G.state.quality);            // auto | low | med | high
let qLevel = forced || (lite ? 'low' : qMode !== 'auto' && QL[qMode] ? qMode : (coarse ? 'low' : 'med'));
let resScale = 1;
let Q = QL[qLevel];

/* ------------------------------- renderer -------------------------------- */
const canvas = $('c');
const renderer = new THREE.WebGLRenderer({ canvas, antialias: false, powerPreference: 'high-performance' });
const DPR = window.devicePixelRatio || 1;
renderer.toneMapping = THREE.ACESFilmicToneMapping;
renderer.shadowMap.enabled = true;
renderer.shadowMap.type = THREE.PCFShadowMap;
renderer.shadowMap.autoUpdate = false;

const scene = new THREE.Scene();
scene.fog = new THREE.FogExp2(0xf2b992, 0.011);
const camera = new THREE.PerspectiveCamera(70, innerWidth / innerHeight, 0.1, 1200);
camera.position.set(-6, 22, 34);

scene.add(camera);                 // the held rod is a child of the camera
let player = null;
const focus = new THREE.Vector3();  // where the player stands; lights and shadows follow it

const rtMain = new THREE.WebGLRenderTarget(innerWidth, innerHeight, { type: THREE.HalfFloatType, samples: Q.msaa });
const composer = new EffectComposer(renderer, rtMain);
composer.addPass(new RenderPass(scene, camera));
const bloom = new UnrealBloomPass(new THREE.Vector2(innerWidth, innerHeight), 0.55, 0.7, 0.82);
const bloomSetSize = bloom.setSize.bind(bloom);
bloom.setSize = (w, h) => bloomSetSize(Math.max(8, w * Q.bloomScale), Math.max(8, h * Q.bloomScale));
composer.addPass(bloom);
composer.addPass(new OutputPass());

/* ------------------------------- uniforms -------------------------------- */
const U = {
  uTime: { value: 0 }, uTop: { value: new THREE.Color() }, uHor: { value: new THREE.Color() },
  uSunDir: { value: new THREE.Vector3(0, 1, 0) }, uSunCol: { value: new THREE.Color() },
  uLight: { value: 1 }, uStars: { value: 0 }, uCloud: { value: 1 }, uDisc: { value: 0.9996 }, uDiscInt: { value: 6 },
  uFogColor: { value: new THREE.Color() }, uFogD: { value: 0.01 }, uRain: { value: 0 }, uCloudSh: { value: 0.3 },
  uRip: { value: Array.from({ length: 12 }, () => new THREE.Vector4(0, 0, -100, 0)) },
  uHeight: { value: null }, uScale: { value: innerHeight * 0.5 },
  uReflTex: { value: null }, uReflMat: { value: new THREE.Matrix4() }, uUseRefl: { value: 1 },
};

/* ------------------------- post: god rays, grade, vignette ---------------- */
const fxShader = {
  uniforms: { tDiffuse: { value: null }, uSunUV: { value: new THREE.Vector2(0.5, 0.5) }, uSunCol: { value: new THREE.Color() }, uRays: { value: 0 },
    uFxaa: { value: 1 }, uSat: { value: 1.1 }, uTint: { value: new THREE.Color(1, 1, 1) }, uVig: { value: 0.32 }, uRes: { value: new THREE.Vector2(1, 1) }, uTime: U.uTime, uAspect: { value: 1 } },
  vertexShader: 'varying vec2 vUv; void main(){ vUv = uv; gl_Position = projectionMatrix*modelViewMatrix*vec4(position,1.0); }',
  fragmentShader: /* glsl */`
    uniform sampler2D tDiffuse; uniform vec2 uSunUV, uRes; uniform vec3 uSunCol, uTint; uniform float uRays, uSat, uVig, uTime, uAspect, uFxaa; varying vec2 vUv;
    float h21(vec2 p){ p = fract(p*vec2(123.34, 456.21)); p += dot(p, p+45.32); return fract(p.x*p.y); }
    vec3 fxaa(vec2 uv){
      vec2 px = 1.0 / uRes; const vec3 L = vec3(0.299, 0.587, 0.114);
      vec3 m = texture2D(tDiffuse, uv).rgb;
      vec3 nw = texture2D(tDiffuse, uv + vec2(-1.0,-1.0)*px).rgb, ne = texture2D(tDiffuse, uv + vec2(1.0,-1.0)*px).rgb;
      vec3 sw = texture2D(tDiffuse, uv + vec2(-1.0,1.0)*px).rgb, se = texture2D(tDiffuse, uv + vec2(1.0,1.0)*px).rgb;
      float lM = dot(m,L), lNW = dot(nw,L), lNE = dot(ne,L), lSW = dot(sw,L), lSE = dot(se,L);
      float lMin = min(lM, min(min(lNW,lNE), min(lSW,lSE))), lMax = max(lM, max(max(lNW,lNE), max(lSW,lSE)));
      if (lMax - lMin < max(0.03, lMax*0.1)) return m;
      vec2 dir = vec2(-((lNW + lNE) - (lSW + lSE)), (lNW + lSW) - (lNE + lSE));
      float red = max((lNW + lNE + lSW + lSE) * 0.03125, 1.0/128.0);
      dir = clamp(dir / (min(abs(dir.x), abs(dir.y)) + red), -8.0, 8.0) * px;
      vec3 a = 0.5 * (texture2D(tDiffuse, uv + dir*(1.0/3.0 - 0.5)).rgb + texture2D(tDiffuse, uv + dir*(2.0/3.0 - 0.5)).rgb);
      vec3 b = a*0.5 + 0.25*(texture2D(tDiffuse, uv + dir*-0.5).rgb + texture2D(tDiffuse, uv + dir*0.5).rgb);
      float lB = dot(b, L);
      return (lB < lMin || lB > lMax) ? a : b;
    }
    void main(){
      vec3 c = uFxaa > 0.5 ? fxaa(vUv) : texture2D(tDiffuse, vUv).rgb;
      if (uRays > 0.001) {
        vec2 d = uSunUV - vUv; float dist = length(d);
        vec2 stp = d * (0.9/10.0); vec2 p = vUv + stp * h21(vUv*uRes + uTime);
        vec3 acc = vec3(0.0); float w = 1.0;
        for (int i = 0; i < 10; i++) { p += stp; vec3 s = texture2D(tDiffuse, p).rgb; float l = max(dot(s, vec3(0.3,0.59,0.11)) - 0.72, 0.0); acc += s*l*w; w *= 0.92; }
        c += acc * uRays * 0.24 * uSunCol * (1.0 - smoothstep(0.0, 1.15, dist));
      }
      float l = dot(c, vec3(0.299,0.587,0.114));
      c = mix(vec3(l), c, uSat);
      c = (c - 0.5) * 1.05 + 0.5;
      c *= uTint;
      vec2 q = (vUv - 0.5) * vec2(uAspect, 1.0);
      c *= 1.0 - uVig * smoothstep(0.32, 1.0, length(q) * 1.15);
      c += (h21(vUv*uRes + fract(uTime)*91.7) - 0.5) * 0.016;
      gl_FragColor = vec4(c, 1.0);
    }`,
};
const fxPass = new ShaderPass(fxShader);
fxPass.uniforms.uTime = U.uTime;     // ShaderPass clones its uniforms; re-link the shared clock
composer.addPass(fxPass);

/* ------------------------------ time of day ------------------------------ */
const PRESETS = {
  golden: { label: 'Golden hour', top: '#3b66b0', hor: '#ffb27c', sunCol: '#ffa95e', sunInt: 4.4, el: 17, az: -118,
    hemiSky: '#ffe2c4', hemiGnd: '#7a9a3c', hemiInt: 1.55, fog: '#f0b48c', fogD: 0.011, stars: 0, cloud: 0.95, light: 1, lantern: 0.5, fire: 0.3, blink: 0.15,
    fireCol: '#ffd88a', bloom: 0.55, exposure: 1.1, mist: 0.3, petal: 1, day: 1, rays: 0.9, sat: 1.06, tint: '#fff4e4', disc: 0.99962, discInt: 7 },
  midday: { label: 'Bright day', top: '#2a82d8', hor: '#cfe8ff', sunCol: '#fff1d6', sunInt: 3.5, el: 56, az: -70,
    hemiSky: '#d6eaff', hemiGnd: '#7d8f5a', hemiInt: 1.85, fog: '#c8e2f5', fogD: 0.007, stars: 0, cloud: 1.1, light: 1.1, lantern: 0.1, fire: 0.12, blink: 0,
    fireCol: '#ffffff', bloom: 0.3, exposure: 0.92, mist: 0.05, petal: 1, day: 1, rays: 0.3, sat: 0.98, tint: '#ffffff', disc: 0.99962, discInt: 6 },
  twilight: { label: 'Twilight', top: '#2b2766', hor: '#ff7d88', sunCol: '#ff7a5c', sunInt: 1.8, el: 5, az: -100,
    hemiSky: '#a58ae0', hemiGnd: '#4a4a6a', hemiInt: 1.0, fog: '#c87a96', fogD: 0.013, stars: 0.35, cloud: 0.8, light: 0.5, lantern: 1.6, fire: 0.75, blink: 0.7,
    fireCol: '#d8ff8a', bloom: 0.8, exposure: 1.25, mist: 0.5, petal: 0.8, day: 0.4, rays: 0.55, sat: 1.12, tint: '#fff0f6', disc: 0.9996, discInt: 6 },
  night: { label: 'Moonlight', top: '#040a22', hor: '#1c2c58', sunCol: '#b9ccff', sunInt: 1.0, el: 38, az: -60,
    hemiSky: '#4a63b0', hemiGnd: '#1a2440', hemiInt: 0.75, fog: '#101b3b', fogD: 0.011, stars: 1, cloud: 0.35, light: 0.14, lantern: 2.4, fire: 1.0, blink: 1.0,
    fireCol: '#c8ff7a', bloom: 1.05, exposure: 1.45, mist: 0.45, petal: 0.55, day: 0, rays: 0.0, sat: 1.02, tint: '#e6f0ff', disc: 0.99915, discInt: 3.2 },
};
const NUM = ['sunInt', 'el', 'az', 'hemiInt', 'fogD', 'stars', 'cloud', 'light', 'lantern', 'fire', 'blink', 'bloom', 'exposure', 'mist', 'petal', 'day', 'rays', 'sat', 'disc', 'discInt'];
const COL = ['top', 'hor', 'sunCol', 'hemiSky', 'hemiGnd', 'fog', 'fireCol', 'tint'];
const mk = (p) => { const o = {}; NUM.forEach((k) => (o[k] = p[k])); COL.forEach((k) => (o[k] = new THREE.Color(p[k]))); return o; };
const cur = mk(PRESETS.golden);
let target = mk(PRESETS.golden);
let presetKey = 'golden';

/* Automatic day / night: a clock from 0 (midnight) to 1 drives the sun and every preset in between. */
const DAY_KEYS = [[0.0, 'night', 300], [0.22, 'twilight', 440], [0.28, 'golden', 460], [0.5, 'midday', 540], [0.7, 'golden', 610], [0.78, 'twilight', 630], [0.86, 'night', 660], [1.0, 'night', 660]];
const JUMP = { golden: 0.68, midday: 0.5, twilight: 0.77, night: 0.95 };
const PRE = Object.fromEntries(Object.keys(PRESETS).map((k) => [k, mk(PRESETS[k])]));
let clock = typeof G.state.clock === 'number' ? G.state.clock : 0.64;
function phaseOf(c) { return (c >= 0.2 && c < 0.25) || (c >= 0.74 && c < 0.83) ? 'twilight' : (c >= 0.25 && c < 0.34) || (c >= 0.6 && c < 0.74) ? 'golden' : c >= 0.34 && c < 0.6 ? 'midday' : 'night'; }
function sampleDay(c) {
  let i = 0; while (i < DAY_KEYS.length - 2 && c >= DAY_KEYS[i + 1][0]) i++;
  const [ta, ka, aza] = DAY_KEYS[i], [tb, kb, azb] = DAY_KEYS[i + 1];
  let f = clamp((c - ta) / (tb - ta), 0, 1); f = f * f * (3 - 2 * f);
  const A = PRE[ka], B = PRE[kb];
  NUM.forEach((n) => (target[n] = A[n] + (B[n] - A[n]) * f));
  COL.forEach((n) => target[n].copy(A[n]).lerp(B[n], f));
  target.az = aza + (azb - aza) * f;
}
let clockLabelT = 0;
function tickClock(dt) {
  const len = (+G.state.dayLen || 12) * 60;
  if (!G.state.paused) clock = (clock + dt / len) % 1;
  sampleDay(clock);
  const ph = phaseOf(clock);
  if (ph !== presetKey) { presetKey = ph; document.querySelectorAll('[data-time]').forEach((b) => b.classList.toggle('on', b.dataset.time === ph)); }
  clockLabelT -= dt;
  if (clockLabelT <= 0) {
    clockLabelT = 0.5;
    const mins = Math.floor(clock * 24 * 60), hh = String(Math.floor(mins / 60)).padStart(2, '0'), mm = String(Math.floor(mins % 60 / 10) * 10).padStart(2, '0');
    $('mood').textContent = `${area === 'maple' ? 'Maple Hollow \u00b7 ' : ''}${PRESETS[presetKey].label} \u00b7 ${hh}:${mm}`;
    const cl = $('clock'); if (cl) cl.textContent = `${hh}:${mm}`;
  }
}
setInterval(() => { G.state.clock = clock; G.saveQuiet(); }, 5000);

const sun = new THREE.DirectionalLight(0xffffff, 3);
sun.castShadow = true;
Object.assign(sun.shadow.camera, { left: -32, right: 32, top: 32, bottom: -32, near: 1, far: 160 });
sun.shadow.bias = -0.0004; sun.shadow.normalBias = 0.04;
scene.add(sun, sun.target);
const hemi = new THREE.HemisphereLight(0xffffff, 0x445522, 0.8);
const fill = new THREE.DirectionalLight(0xffffff, 0.5);
scene.add(hemi, fill, fill.target);
const lanternLight = new THREE.PointLight(0xffb454, 0, 14, 1.6);
const shopLight = new THREE.PointLight(0xffb454, 0, 12, 1.6);
scene.add(lanternLight, shopLight);

const fireU = { uTime: U.uTime, uScale: U.uScale, uAmount: { value: 0 }, uBlink: { value: 0 }, uColor: { value: new THREE.Color() } };
const petalU = { uTime: U.uTime, uScale: U.uScale, uLight: U.uLight, uAmount: { value: 1 }, uLeaf: { value: 0 } };
const mistU = { uTime: U.uTime, uFogColor: U.uFogColor, uAmount: { value: 0.3 }, uLight: U.uLight };
let lanternMat = null, shopGlowMat = null, weather = null, fishing = null, creatures = null;
const audio = new Ambience();
audio.setVolume(G.state.volume);

const tmpA = new THREE.Color(), tmpB = new THREE.Color(), grey = new THREE.Color(0.42, 0.45, 0.5);
function wxTint(src, dst, wx, k) { const l = src.r * 0.3 + src.g * 0.59 + src.b * 0.11; tmpB.setRGB(l * 0.9 + 0.05, l * 0.95 + 0.06, l * 1.0 + 0.08); return dst.copy(src).lerp(tmpB, wx * k); }
const _sunV = new THREE.Vector3(), _camDir = new THREE.Vector3();

function applyTime(dt) {
  const k = 1 - Math.exp(-dt * 1.7);
  while (target.az - cur.az > 180) cur.az += 360;
  while (target.az - cur.az < -180) cur.az -= 360;
  NUM.forEach((n) => (cur[n] += (target[n] - cur[n]) * k));
  COL.forEach((n) => cur[n].lerp(target[n], k));
  const wx = weather ? weather.amount : 0;
  const el = THREE.MathUtils.degToRad(cur.el), az = THREE.MathUtils.degToRad(cur.az);
  U.uSunDir.value.set(Math.cos(el) * Math.cos(az), Math.sin(el), Math.cos(el) * Math.sin(az)).normalize();
  wxTint(cur.top, U.uTop.value, wx, 0.55); wxTint(cur.hor, U.uHor.value, wx, 0.5); U.uSunCol.value.copy(cur.sunCol);
  U.uLight.value = cur.light * (1 - 0.32 * wx); U.uStars.value = cur.stars * (1 - wx); U.uCloud.value = cur.cloud + wx * 0.9;
  U.uCloudSh.value = clamp((U.uCloud.value - 0.5) * 0.6, 0, 0.42) * clamp(cur.light, 0, 1) * (1 - wx * 0.7);
  U.uDisc.value = cur.disc; U.uDiscInt.value = cur.discInt * (1 - wx * 0.9);
  wxTint(cur.fog, U.uFogColor.value, wx, 0.5); U.uFogD.value = cur.fogD * (1 + 0.7 * wx);
  scene.fog.color.copy(U.uFogColor.value); scene.fog.density = U.uFogD.value;
  sun.color.copy(cur.sunCol); sun.intensity = cur.sunInt * (1 - 0.72 * wx);
  sun.position.copy(focus).addScaledVector(U.uSunDir.value, 70);
  sun.target.position.copy(focus);
  hemi.color.copy(cur.hemiSky); hemi.groundColor.copy(cur.hemiGnd); hemi.intensity = cur.hemiInt * (1 - 0.08 * wx);
  fill.color.copy(cur.hor).lerp(tmpA.setRGB(1, 1, 1), 0.5); fill.intensity = cur.hemiInt * 0.5 * (0.35 + 0.65 * cur.light);
  fill.position.set(camera.position.x, 0, camera.position.z).setLength(40).setY(26); fill.target.position.copy(focus);
  bloom.strength = Q.bloom ? cur.bloom : 0; renderer.toneMappingExposure = cur.exposure;
  const T = U.uTime.value, flick = 1 + Math.sin(T * 7.3) * 0.04 + Math.sin(T * 12.1) * 0.03;
  lanternLight.intensity = cur.lantern * 9 * flick; shopLight.intensity = cur.lantern * 8 * flick;
  if (lanternMat) lanternMat.emissiveIntensity = (0.35 + cur.lantern * 2.2) * flick;
  if (shopGlowMat) shopGlowMat.emissiveIntensity = (0.35 + cur.lantern * 2.2) * (1 + Math.sin(T * 5.1) * 0.05);
  fireU.uAmount.value = cur.fire * (1 - wx * 0.7); fireU.uBlink.value = cur.blink; fireU.uColor.value.copy(cur.fireCol);
  petalU.uAmount.value = cur.petal * (1 - wx * 0.8); mistU.uAmount.value = cur.mist + wx * 0.35;
  audio.setMix(cur.day, 1 - cur.day);
  // post settings
  const fu = fxPass.uniforms;
  fu.uSat.value = cur.sat; fu.uTint.value.copy(cur.tint); fu.uSunCol.value.copy(cur.sunCol);
  _sunV.copy(camera.position).addScaledVector(U.uSunDir.value, 100).project(camera);
  camera.getWorldDirection(_camDir);
  const front = _camDir.dot(U.uSunDir.value);
  fu.uSunUV.value.set(_sunV.x * 0.5 + 0.5, _sunV.y * 0.5 + 0.5);
  fu.uRays.value = Q.rays && front > 0.05 ? cur.rays * (1 - wx * 0.85) * clamp(front * 2.2, 0, 1) : 0;
}

/* -------------------------------- sky dome ------------------------------- */
const sky = new THREE.Mesh(
  new THREE.SphereGeometry(500, 40, 20),
  new THREE.ShaderMaterial({
    side: THREE.BackSide, depthWrite: false, fog: false, uniforms: U,
    vertexShader: `varying vec3 vD; void main(){ vD = position; gl_Position = projectionMatrix*modelViewMatrix*vec4(position,1.0); gl_Position.z = gl_Position.w; }`,
    fragmentShader: /* glsl */`
      varying vec3 vD; uniform float uTime, uStars, uCloud, uLight, uDisc, uDiscInt;
      ${NOISE}${SKY_FN}
      void main(){
        vec3 d = normalize(vD);
        vec3 c = skyBase(d);
        float s = dot(d, uSunDir);
        float disc = smoothstep(uDisc, uDisc + 0.00045, s);
        float tex = 0.88 + 0.12*vnoise(d.xz*95.0 + d.y*60.0);
        c += uSunCol * disc * uDiscInt * tex * step(-0.02, d.y);
        if (uStars > 0.01 && d.y > 0.0){
          vec2 uv = d.xz/(d.y+0.35)*55.0; vec2 id = floor(uv); vec2 f = fract(uv)-0.5;
          float h = hash21(id); vec2 o = (vec2(hash21(id+3.1), hash21(id+9.7))-0.5)*0.6;
          float st = step(0.965, h)*(1.0 - smoothstep(0.0, 0.22, length(f-o)));
          c += vec3(0.8,0.9,1.0)*st*(0.55+0.45*sin(uTime*1.7+h*60.0))*uStars*smoothstep(0.0,0.3,d.y)*(1.0-disc);
        }
        if (uCloud > 0.02) {
          float hh = max(d.y, 0.0);
          vec2 cuv = d.xz/(hh+0.28)*1.5 + vec2(uTime*0.004, uTime*0.002);
          float n = fbm(cuv) * 0.7 + fbm3(cuv*2.3 + 4.0) * 0.3;
          float cov = smoothstep(0.46 - 0.12*min(uCloud-0.9, 0.8), 0.8, n) * smoothstep(0.02, 0.28, d.y) * min(uCloud, 1.6);
          float sunAmt = pow(max(s, 0.0), 3.0);
          vec3 cc = mix(uHor*0.9 + 0.2, uSunCol*1.2, sunAmt*0.8) * (0.28 + 0.75*uLight);
          cc = mix(cc*0.7, cc*1.15, smoothstep(0.5, 0.85, n));
          c = mix(c, cc, clamp(cov*0.9, 0.0, 1.0));
        }
        gl_FragColor = vec4(c, 1.0);
      }`,
  })
);
sky.renderOrder = -10; sky.frustumCulled = false;
scene.add(sky);

/* ------------------------------- water mesh ------------------------------ */
function makeWater(ox = 0, heightTex = null) {
  const m = new THREE.ShaderMaterial({
    transparent: true, depthWrite: false, uniforms: { ...U, uHeight: { value: heightTex }, uOrigin: { value: ox } },
    vertexShader: `varying vec3 vWP; void main(){ vec4 w = modelMatrix*vec4(position,1.0); vWP = w.xyz; gl_Position = projectionMatrix*viewMatrix*w; }`,
    fragmentShader: /* glsl */`
      varying vec3 vWP;
      uniform float uTime, uLight, uFogD, uRain, uUseRefl, uOrigin; uniform vec3 uFogColor; uniform sampler2D uHeight, uReflTex; uniform mat4 uReflMat; uniform vec4 uRip[12];
      ${NOISE}${SKY_FN}
      float caustic(vec2 p){
        float a = 0.0; vec2 q = p;
        for(int i=0;i<2;i++){ q += vec2(sin(q.y*1.7+uTime*0.6), cos(q.x*1.5-uTime*0.5))*0.45; a += abs(sin(q.x*2.0)*sin(q.y*2.0)); }
        return pow(1.0 - a*0.5, 3.0);
      }
      vec2 rainRipples(vec2 p, float t){
        vec2 g = vec2(0.0);
        for(int L=0; L<2; L++){
          float sc = 2.6 + float(L)*1.7;
          vec2 q = p*sc + float(L)*13.7; vec2 id = floor(q); vec2 f = fract(q) - 0.5;
          float h = hash21(id); float ph = fract(t*0.9 + h*7.0);
          vec2 off = (vec2(hash21(id+3.3), hash21(id+8.1)) - 0.5)*0.5;
          vec2 dv = f - off; float d = length(dv) + 1e-4; float R = ph*0.55;
          float env = exp(-pow((d-R)*14.0, 2.0)) * (1.0-ph) * step(h, 0.6);
          g += (dv/d) * cos((d-R)*38.0) * env * 0.5;
        }
        return g;
      }
      vec2 waves(vec2 p, float t){
        vec2 g = vec2(0.0);
        g += vec2(0.8,0.6)  * cos(dot(p,vec2(0.8,0.6))*1.6 + t*0.8)  * 0.045;
        g += vec2(-0.5,0.86)* cos(dot(p,vec2(-0.5,0.86))*2.7 - t*1.05)* 0.032;
        g += vec2(0.2,-0.98)* cos(dot(p,vec2(0.2,-0.98))*5.1 + t*1.5) * 0.02;
        g += (vec2(vnoise(p*3.2+t*0.25), vnoise(p*3.6-t*0.3+17.0))-0.5) * 0.06;
        for(int i=0;i<12;i++){
          vec4 r = uRip[i]; float age = t - r.z;
          if(age < 0.0 || age > 7.0) continue;
          vec2 dv = p - r.xy; float d = length(dv) + 1e-4;
          float R = age*1.35;
          float env = exp(-pow((d-R)*2.4, 2.0)) * r.w * exp(-age*0.55) / (1.0 + d*0.35);
          g += (dv/d) * cos((d-R)*11.0) * env * 0.28;
        }
        if (uRain > 0.02) g += rainRipples(p, t) * uRain * 0.55;
        return g;
      }
      void main(){
        vec2 hUV = vec2((vWP.x-uOrigin+${EXT}.0)/${(EXT * 2).toFixed(1)}, (-vWP.z+${EXT}.0)/${(EXT * 2).toFixed(1)});
        float th = texture2D(uHeight, hUV).r;
        float depth = ${WATER_Y.toFixed(2)} - th;
        if(depth <= 0.0) discard;
        vec3 V = normalize(cameraPosition - vWP);
        vec2 g = waves(vWP.xz, uTime);
        vec3 N = normalize(vec3(-g.x, 1.0, -g.y));
        float ndv = max(dot(N, V), 0.0);
        float fres = 0.04 + 0.96*pow(1.0 - ndv, 4.0);
        vec3 R = reflect(-V, N); R.y = abs(R.y);
        vec3 refl = skyBase(R);
        if (uUseRefl > 0.5) {
          vec4 rc = uReflMat * vec4(vWP, 1.0);
          refl = mix(refl, texture2D(uReflTex, rc.xy / rc.w + N.xz * 0.22).rgb, 0.97);
        }
        vec3 shallow = vec3(0.12,0.5,0.4), deepc = vec3(0.01,0.12,0.16);
        vec3 body = mix(shallow, deepc, smoothstep(0.08, 1.5, depth));
        float lit = 0.12 + 0.88*uLight;
        body *= lit;
        if (depth < 1.3) body += uSunCol * caustic(vWP.xz*1.6) * (1.0 - smoothstep(0.0, 1.3, depth)) * 0.22 * uLight;
        vec3 col = mix(body, refl, clamp(fres, 0.0, 1.0));
        vec3 H = normalize(V + uSunDir);
        float spec = pow(max(dot(N, H), 0.0), 500.0);
        if (spec > 0.002) col += uSunCol * spec * (3.0 + 7.0*smoothstep(0.55, 1.0, vnoise(vWP.xz*7.0 + uTime*0.8)));
        if (depth < 0.16) col = mix(col, vec3(0.85,0.95,0.9)*lit, (1.0 - smoothstep(0.0, 0.16, depth)) * (0.55 + 0.45*vnoise(vWP.xz*6.0 + uTime*0.3)) * 0.55);
        float alpha = mix(0.32, 0.8, smoothstep(0.05, 1.6, depth));
        alpha = max(alpha, fres);
        alpha *= smoothstep(0.0, 0.05, depth);
        float dist = length(cameraPosition - vWP);
        col = mix(col, uFogColor, 1.0 - exp(-pow(dist*uFogD, 2.0)));
        gl_FragColor = vec4(col, alpha);
        #include <tonemapping_fragment>
        #include <colorspace_fragment>
      }`,
  });
  const mesh = new THREE.Mesh(new THREE.PlaneGeometry(130, 130).rotateX(-Math.PI / 2), m);
  mesh.position.set(ox, WATER_Y, 0); mesh.renderOrder = 2;
  return mesh;
}

/* --------------------------- material helpers ---------------------------- */
const lam = (opts = {}) => new THREE.MeshLambertMaterial({ vertexColors: true, ...opts });

/* One material patcher: wind sway + gust highlights, ground / leaf / bark detail, drifting cloud shadows. */
const SWAY_GLSL = /* glsl */`
  vec3 transformed = vec3(position);
  float sw = uv.y * uv.y; float ph = uv.x * 6.2831;
  float wave = sin(uTime*1.25 - dot(position.xz, vec2(0.30, 0.17)));
  float gust = smoothstep(-0.2, 1.0, wave) * (0.55 + 0.45*sin(uTime*0.21 + position.x*0.04 - position.z*0.03));
  float w1 = sin(uTime*1.35 + position.x*0.33 + position.z*0.21 + ph);
  float w2 = cos(uTime*1.1 + position.z*0.37 + ph*1.7);
  transformed.x += (w1*0.04 + gust*0.13) * sw * uSway;
  transformed.z += (w2*0.035 + gust*0.075) * sw * uSway;
  transformed.y -= (abs(w1)*0.01 + gust*0.03) * sw * uSway;
  vT = uv.y; vGust = gust;`;
const LEAF_GLSL = /* glsl */`
  float lh = clamp(position.y*0.1, 0.0, 1.0);
  transformed.x += sin(uTime*0.9 + position.x*0.5 + position.y*0.4)*0.05*lh;
  transformed.z += cos(uTime*0.8 + position.z*0.5 + position.y*0.3)*0.05*lh;`;
const TERRAIN_GLSL = /* glsl */`
  float tn = vnoise(vCW.xz*1.4)*0.6 + vnoise(vCW.xz*6.5)*0.4;
  diffuseColor.rgb *= 0.76 + 0.46*tn;
  diffuseColor.rgb *= mix(vec3(1.07,1.0,0.85), vec3(0.9,1.05,1.02), vnoise(vCW.xz*0.11));
  diffuseColor.rgb *= 0.92 + 0.16*vnoise(vCW.xz*23.0);`;
function patchMat(mat, o = {}) {
  const key = JSON.stringify(o);
  mat.onBeforeCompile = (sh) => {
    sh.uniforms.uTime = U.uTime; sh.uniforms.uCloudSh = U.uCloudSh;
    if (o.sway) sh.uniforms.uSway = { value: o.sway };
    const sv = o.sway ? ' uniform float uSway; varying float vT; varying float vGust;' : '';
    let vs = sh.vertexShader.replace('#include <common>', `#include <common>\nuniform float uTime;${sv}\nvarying vec3 vCW;`);
    if (o.sway) vs = vs.replace('#include <begin_vertex>', SWAY_GLSL);
    if (o.leaves) vs = vs.replace('#include <begin_vertex>', '#include <begin_vertex>\n' + LEAF_GLSL);
    vs = vs.replace('#include <project_vertex>', '#include <project_vertex>\n  vCW = (modelMatrix * vec4(transformed, 1.0)).xyz;');
    let fs = sh.fragmentShader.replace('#include <common>', `#include <common>\nuniform float uTime, uCloudSh; varying vec3 vCW;${o.sway ? ' varying float vT; varying float vGust;' : ''}\n${NOISE}`);
    let col = '';
    if (o.sway && o.gradient !== false) col += 'diffuseColor.rgb *= mix(vec3(0.38,0.5,0.34), vec3(1.25,1.2,0.85), vT);\n';
    if (o.sway) col += 'diffuseColor.rgb *= 1.0 + vGust * vT * 0.34;\n';
    if (o.terrain) col += TERRAIN_GLSL;
    if (o.leaves) col += 'diffuseColor.rgb *= 0.78 + 0.36*vnoise(vCW.xz*4.5 + vCW.y*3.7);\n';
    if (o.bark) col += 'diffuseColor.rgb *= 0.72 + 0.42*vnoise(vec2((vCW.x + vCW.z)*14.0, vCW.y*1.6));\n';
    fs = fs.replace('#include <color_fragment>', '#include <color_fragment>\n' + col);
    if (o.sway) fs = fs.replace('#include <normal_fragment_maps>', '#include <normal_fragment_maps>\n  normal = normalize(vNormal);');
    if (o.glow) fs = fs.replace('#include <emissivemap_fragment>', `#include <emissivemap_fragment>\n  totalEmissiveRadiance += diffuseColor.rgb * ${o.glow.toFixed(2)} * vT * vT;`);
    fs = fs.replace('#include <lights_fragment_end>', `#include <lights_fragment_end>
      reflectedLight.directDiffuse *= 1.0 - uCloudSh * smoothstep(0.45, 0.75, fbm3(vCW.xz*0.022 + vec2(uTime*0.010, uTime*0.005)));`);
    sh.vertexShader = vs; sh.fragmentShader = fs;
  };
  mat.customProgramCacheKey = () => 'p' + key;
  return mat;
}
const patchSway = (m, o = {}) => patchMat(m, { sway: o.sway || 1, gradient: o.gradient, glow: o.glow || 0 });
const patchTerrain = (m) => patchMat(m, { terrain: true });
const patchLeaves = (m) => patchMat(m, { leaves: true });
function flattenNormalsUp(geo, k) {
  const n = geo.attributes.normal;
  for (let i = 0; i < n.count; i++) {
    const x = n.getX(i) * (1 - k), y = n.getY(i) * (1 - k) + k, z = n.getZ(i) * (1 - k), l = Math.hypot(x, y, z) || 1;
    n.setXYZ(i, x / l, y / l, z / l);
  }
  n.needsUpdate = true;
}

/* A big mesh split into spatial chunks that share one set of buffers: frustum culling per chunk + draw distance. */
const chunkSets = [];
function chunkify(mesh, cell, maxDist) {
  const geo = mesh.geometry, pos = geo.attributes.position, idx = geo.index.array, buckets = new Map();
  for (let i = 0; i < idx.length; i += 3) {
    const a = idx[i], key = Math.floor((pos.getX(a) + EXT) / cell) * 1000 + Math.floor((pos.getZ(a) + EXT) / cell);
    let b = buckets.get(key); if (!b) buckets.set(key, (b = []));
    b.push(a, idx[i + 1], idx[i + 2]);
  }
  const group = new THREE.Group(); group.name = mesh.name + 'Chunks';
  const chunks = [];
  for (const arr of buckets.values()) {
    const g = new THREE.BufferGeometry();
    for (const k in geo.attributes) g.setAttribute(k, geo.attributes[k]);
    let mx = 0;
    for (const v of arr) if (v > mx) mx = v;
    g.setIndex(new THREE.BufferAttribute(mx > 65535 ? Uint32Array.from(arr) : Uint16Array.from(arr), 1));
    const bb = new THREE.Box3();
    const v3 = new THREE.Vector3();
    for (const v of arr) bb.expandByPoint(v3.fromBufferAttribute(pos, v));
    g.boundingBox = bb; g.boundingSphere = bb.getBoundingSphere(new THREE.Sphere());
    const m = new THREE.Mesh(g, mesh.material);
    m.castShadow = mesh.castShadow; m.receiveShadow = mesh.receiveShadow;
    group.add(m); chunks.push({ mesh: m, c: g.boundingSphere.center, r: g.boundingSphere.radius });
  }
  mesh.parent.remove(mesh);
  scene.add(group);
  const set = { group, chunks, maxDist };
  chunkSets.push(set);
  return set;
}
function cullChunks() {
  const cp = camera.position;
  for (const s of chunkSets) {
    if (!s.maxDist) continue;
    const md = typeof s.maxDist === 'function' ? s.maxDist() : s.maxDist;
    for (const c of s.chunks) { const dx = c.c.x - cp.x, dz = c.c.z - cp.z, r = md + c.r; c.mesh.visible = dx * dx + dz * dz < r * r; }
  }
}

/* ----------------------------- terrain helpers --------------------------- */
const HF = [];          // height fields: { ox, grid, h }
function hAt(x, z) {
  const F = HF.length > 1 && x > 200 ? HF[1] : HF[0];
  if (!F) return 0;
  const G2 = F.grid, W = G2 + 1, lx = x - F.ox;
  const u = clamp((lx + EXT) / (2 * EXT) * G2, 0, G2 - 1.001), v = clamp((-z + EXT) / (2 * EXT) * G2, 0, G2 - 1.001);
  const i = Math.floor(u), j = Math.floor(v), fu = u - i, fv = v - j, h = F.h;
  const a = h[j * W + i], b = h[j * W + i + 1], c = h[(j + 1) * W + i], d = h[(j + 1) * W + i + 1];
  return lerp(lerp(a, b, fu), lerp(c, d, fu), fv);
}
function buildHeightTexture(terrainMesh, ox = 0) {
  const pos = terrainMesh.geometry.attributes.position;
  const grid = Math.round(Math.sqrt(pos.count)) - 1, W = grid + 1;
  const h = new Float32Array(W * W).fill(-2);
  for (let i = 0; i < pos.count; i++) {
    const ix = Math.round((pos.getX(i) - ox + EXT) / (2 * EXT) * grid), iy = Math.round((-pos.getZ(i) + EXT) / (2 * EXT) * grid);
    if (ix >= 0 && ix < W && iy >= 0 && iy < W) h[iy * W + ix] = pos.getY(i);
  }
  HF.push({ ox, grid, h });
  const half = new Uint16Array(W * W);
  for (let i = 0; i < half.length; i++) half[i] = THREE.DataUtils.toHalfFloat(h[i]);
  const tex = new THREE.DataTexture(half, W, W, THREE.RedFormat, THREE.HalfFloatType);
  tex.minFilter = tex.magFilter = THREE.LinearFilter; tex.wrapS = tex.wrapT = THREE.ClampToEdgeWrapping; tex.needsUpdate = true;
  return tex;
}

/* -------------------------- reflections + ripples ------------------------ */
const reflRT = new THREE.WebGLRenderTarget(64, 64, { type: THREE.HalfFloatType });
U.uReflTex.value = reflRT.texture;
const reflCam = new THREE.PerspectiveCamera();
const clipPlane = new THREE.Plane(new THREE.Vector3(0, 1, 0), -WATER_Y - 0.02);
const _d = new THREE.Vector3(), _u = new THREE.Vector3(), _t = new THREE.Vector3();
const hideInRefl = [];
function renderReflection() {
  camera.updateMatrixWorld();
  camera.getWorldDirection(_d);
  _u.set(0, 1, 0).applyQuaternion(camera.quaternion);
  reflCam.position.set(camera.position.x, 2 * WATER_Y - camera.position.y, camera.position.z);
  reflCam.up.set(_u.x, -_u.y, _u.z);
  _t.set(camera.position.x + _d.x, 2 * WATER_Y - (camera.position.y + _d.y), camera.position.z + _d.z);
  reflCam.lookAt(_t);
  reflCam.projectionMatrix.copy(camera.projectionMatrix); reflCam.projectionMatrixInverse.copy(camera.projectionMatrixInverse);
  reflCam.updateMatrixWorld();
  U.uReflMat.value.set(0.5, 0, 0, 0.5, 0, 0.5, 0, 0.5, 0, 0, 0.5, 0.5, 0, 0, 0, 1).multiply(reflCam.projectionMatrix).multiply(reflCam.matrixWorldInverse);
  hideInRefl.forEach((o) => { o._v = o.visible; o.visible = false; });
  renderer.clippingPlanes = [clipPlane];
  renderer.setRenderTarget(reflRT); renderer.clear(); renderer.render(scene, reflCam); renderer.setRenderTarget(null);
  renderer.clippingPlanes = [];
  hideInRefl.forEach((o) => (o.visible = o._v));
}
let ripIdx = 0;
function addRipple(x, z, amp = 1) { U.uRip.value[ripIdx].set(x, z, U.uTime.value, amp); ripIdx = (ripIdx + 1) % 12; }

/* ------------------------ applying quality settings ---------------------- */
function setResolution() {
  const pr = Math.max(0.4, Math.min(DPR, Q.pr) * resScale);
  renderer.setPixelRatio(pr); composer.setPixelRatio(pr);
  renderer.setSize(innerWidth, innerHeight); composer.setSize(innerWidth, innerHeight);
  U.uScale.value = innerHeight * pr * 0.5;
  fxPass.uniforms.uRes.value.set(innerWidth * pr, innerHeight * pr); fxPass.uniforms.uAspect.value = innerWidth / innerHeight;
  const rs = Math.max(0.05, Q.refl);
  reflRT.setSize(Math.max(64, Math.floor(innerWidth * pr * rs)), Math.max(64, Math.floor(innerHeight * pr * rs)));
}
function applyQuality() {
  Q = QL[qLevel];
  document.body.dataset.q = qLevel;
  const wantShadow = Q.shadow > 0;
  if (wantShadow) { sun.shadow.mapSize.set(Q.shadow, Q.shadow); if (sun.shadow.map) { sun.shadow.map.dispose(); sun.shadow.map = null; } }
  if (renderer.shadowMap.enabled !== wantShadow) { renderer.shadowMap.enabled = wantShadow; sun.castShadow = wantShadow; scene.traverse((o) => { if (o.material) { (Array.isArray(o.material) ? o.material : [o.material]).forEach((m) => (m.needsUpdate = true)); } }); }
  try { [composer.renderTarget1, composer.renderTarget2].forEach((r) => { if (r.samples !== Q.msaa) { r.samples = Q.msaa; r.dispose(); } }); } catch (e) { /* msaa unsupported */ }
  bloom.enabled = Q.bloom;
  U.uUseRefl.value = Q.refl > 0 ? 1 : 0;
  if (fireflies) fireflies.geometry.setDrawRange(0, Q.flies);
  mistGroup && mistGroup.children.forEach((m, i) => (m.userData.on = i < Q.mist));
  setResolution();
  forceShadow = 3;
}
function setQualityMode(mode) {
  qMode = mode;
  if (mode !== 'auto' && QL[mode]) { qLevel = mode; resScale = 1; }
  else { qLevel = coarse ? 'low' : 'med'; resScale = 1; }
  gov.reset(); applyQuality();
}
const gov = {
  frames: 0, acc: 0, warm: 100, good: 0, cool: 0,
  reset() { this.frames = 0; this.acc = 0; this.warm = 60; this.good = 0; this.cool = 3; },
  tick(dt) {
    if (qMode !== 'auto' || document.hidden) return;
    if (this.warm > 0) { this.warm--; return; }
    if (dt > 0.3) return;
    this.acc += dt; this.frames++;
    this.cool -= dt;
    if (this.frames < 40) return;
    const avg = this.acc / this.frames; this.frames = 0; this.acc = 0;
    const li = LEVELS.indexOf(qLevel);
    if (avg > 0.027) {
      this.good = 0;
      if (resScale > 0.65) { resScale = Math.max(0.6, resScale - 0.12); setResolution(); }
      else if (li > 0) { qLevel = LEVELS[li - 1]; resScale = 0.85; applyQuality(); this.warm = 40; }
    } else if (avg < 0.0175) {
      if (resScale < 1) { resScale = Math.min(1, resScale + 0.05); setResolution(); }
      else if (li < 2 && ++this.good > 7 && this.cool <= 0) { qLevel = LEVELS[li + 1]; this.good = 0; applyQuality(); this.warm = 40; this.cool = 6; }
    } else this.good = 0;
  },
};

/* ------------------------------ particles -------------------------------- */
function makePoints(n, region, vs, fs, uniforms, blending) {
  const pos = new Float32Array(n * 3), seed = new Float32Array(n * 4);
  for (let i = 0; i < n; i++) {
    pos[i * 3] = region.x + (Math.random() - 0.5) * region.w; pos[i * 3 + 1] = region.y + Math.random() * region.h; pos[i * 3 + 2] = region.z + (Math.random() - 0.5) * region.d;
    for (let k = 0; k < 4; k++) seed[i * 4 + k] = Math.random();
  }
  const g = new THREE.BufferGeometry();
  g.setAttribute('position', new THREE.BufferAttribute(pos, 3)); g.setAttribute('aSeed', new THREE.BufferAttribute(seed, 4));
  const p = new THREE.Points(g, new THREE.ShaderMaterial({ uniforms, vertexShader: vs, fragmentShader: fs, transparent: true, depthWrite: false, blending }));
  p.frustumCulled = false;
  return p;
}
const fireflies = makePoints(520, { x: 0, y: 0.35, z: 0, w: 56, h: 3.4, d: 56 },
  /* glsl */`attribute vec4 aSeed; uniform float uTime, uScale, uAmount, uBlink; varying float vA;
    void main(){
      vec3 p = position; float t = uTime;
      p.x += sin(t*0.4 + aSeed.x*30.0)*1.3 + sin(t*0.9 + aSeed.y*17.0)*0.3;
      p.y += sin(t*0.5 + aSeed.y*40.0)*0.5 + 0.2*sin(t*1.3 + aSeed.z*9.0);
      p.z += cos(t*0.35 + aSeed.z*25.0)*1.3;
      float b = mix(1.0, 0.5 + 0.5*sin(t*(1.2 + aSeed.w*1.6) + aSeed.x*60.0), uBlink); b = b*b;
      float on = step(aSeed.w, uAmount);
      vA = b * on * (0.35 + 0.65*min(uAmount*1.4, 1.0));
      vec4 mv = viewMatrix * vec4(p, 1.0);
      gl_PointSize = clamp((0.16 + 0.1*aSeed.z) * uScale / -mv.z, 1.0, 44.0);
      gl_Position = projectionMatrix * mv;
    }`,
  /* glsl */`uniform vec3 uColor; varying float vA;
    void main(){ float d = length(gl_PointCoord - 0.5); float a = 1.0 - smoothstep(0.0, 0.5, d); a = a*a; gl_FragColor = vec4(uColor*(1.0 + a*2.5), a*vA); }`,
  fireU, THREE.AdditiveBlending);
scene.add(fireflies);

let petals = null;
function makePetals(cx, cz) {
  petals = makePoints(650, { x: cx, y: 0, z: cz, w: 34, h: 0, d: 34 },
    /* glsl */`attribute vec4 aSeed; uniform float uTime, uScale, uLeaf; varying float vA; varying float vR; varying vec3 vC;
      void main(){
        float life = fract(aSeed.w + uTime*(0.018 + aSeed.z*0.012));
        vec3 p = position;
        p.y = 6.5 - life*7.0;
        p.x += sin(life*14.0 + aSeed.z*20.0)*0.9 + life*7.0 + sin(uTime*0.3)*1.5;
        p.z += cos(life*11.0 + aSeed.x*20.0)*0.9 - life*2.0;
        vA = smoothstep(0.0, 0.08, life) * (1.0 - smoothstep(0.9, 1.0, life)) * step(-0.15, p.y);
        vR = life*20.0 + aSeed.x*6.28;
        vC = mix(mix(vec3(1.0,0.72,0.82), vec3(1.0,0.9,0.94), aSeed.y), mix(vec3(0.85,0.25,0.08), vec3(1.0,0.72,0.18), aSeed.y), uLeaf);
        vec4 mv = viewMatrix*vec4(p,1.0);
        gl_PointSize = clamp((0.075 + 0.06*aSeed.y) * uScale / -mv.z, 1.0, 26.0);
        gl_Position = projectionMatrix*mv;
      }`,
    /* glsl */`uniform float uLight, uAmount; varying float vA; varying float vR; varying vec3 vC;
      void main(){
        vec2 p = gl_PointCoord - 0.5; float c = cos(vR), s = sin(vR); p = mat2(c,-s,s,c)*p;
        float d = length(p*vec2(1.0, 1.9));
        float a = 1.0 - smoothstep(0.25, 0.5, d);
        gl_FragColor = vec4(vC*(0.25 + 0.9*uLight), a*vA*uAmount*0.95);
      }`, petalU, THREE.NormalBlending);
  scene.add(petals);
}

let mistGroup = null;
function makeMist() {
  const g = new THREE.Group();
  for (let i = 0; i < 2; i++) {
    const m = new THREE.Mesh(new THREE.CircleGeometry(15 + i * 3, 40).rotateX(-Math.PI / 2), new THREE.ShaderMaterial({
      transparent: true, depthWrite: false, uniforms: { ...mistU, uLayer: { value: i } },
      vertexShader: `varying vec3 vP; varying vec2 vL; void main(){ vP = (modelMatrix*vec4(position,1.0)).xyz; vL = position.xz; gl_Position = projectionMatrix*viewMatrix*modelMatrix*vec4(position,1.0); }`,
      fragmentShader: /* glsl */`
        varying vec3 vP; varying vec2 vL; uniform float uTime, uAmount, uLight, uLayer; uniform vec3 uFogColor;
        ${NOISE}
        void main(){
          float n = fbm3(vP.xz*0.16 + vec2(uTime*0.012*(1.0+uLayer), uTime*0.007) + uLayer*9.0);
          float edge = 1.0 - smoothstep(0.55, 1.0, length(vL)/15.0);
          float soft = smoothstep(1.0, 7.0, length(cameraPosition - vP));
          float a = smoothstep(0.35, 0.8, n) * edge * soft * uAmount * (0.6 - 0.15*uLayer);
          gl_FragColor = vec4(uFogColor*(0.9 + 0.4*uLight) + 0.1, a);
          #include <tonemapping_fragment>
          #include <colorspace_fragment>
        }`,
    }));
    m.position.set(0, WATER_Y + 0.35 + i * 0.9, 0); m.renderOrder = 3 + i; m.userData.on = true;
    g.add(m);
  }
  return g;
}

/* ----------------------------- load the scene ---------------------------- */
const state = { started: false, last: performance.now(), shot: false, frame: 0 };
let forceShadow = 3, shopMesh = null, shopAnchor = null, shopMesh2 = null, falls = null;
const OX2 = 400;
const wayGlowMats = [];
const AREA = { pond: { cx: 0 }, maple: { cx: OX2 } };
let area = 'pond';
/* Animated waterfall: streaks that run down the cascade, foam where it is steep, a soft edge. */
function waterfallMat() {
  return new THREE.ShaderMaterial({
    transparent: true, depthWrite: false, side: THREE.DoubleSide, uniforms: { uTime: U.uTime, uLight: U.uLight, uFogColor: U.uFogColor, uFogD: U.uFogD },
    vertexShader: 'varying vec2 vUv; varying vec3 vWP; void main(){ vUv = uv; vec4 w = modelMatrix*vec4(position,1.0); vWP = w.xyz; gl_Position = projectionMatrix*viewMatrix*w; }',
    fragmentShader: `varying vec2 vUv; varying vec3 vWP; uniform float uTime, uLight, uFogD; uniform vec3 uFogColor;
      ${NOISE}
      void main(){
        float flow = vUv.y * 6.0 - uTime * 1.6;
        float streak = vnoise(vec2(vUv.x * 18.0, flow)) * 0.6 + vnoise(vec2(vUv.x * 40.0, flow * 2.0)) * 0.4;
        float foam = smoothstep(0.55, 0.85, streak) + smoothstep(0.8, 1.0, vUv.y) * 0.6;
        vec3 col = mix(vec3(0.42, 0.62, 0.66), vec3(0.95, 0.98, 1.0), clamp(foam, 0.0, 1.0)) * (0.3 + 0.8 * uLight);
        float edge = smoothstep(0.0, 0.18, vUv.x) * smoothstep(1.0, 0.82, vUv.x);
        float a = (0.55 + 0.4 * streak) * edge;
        col = mix(col, uFogColor, 1.0 - exp(-pow(length(cameraPosition - vWP) * uFogD, 2.0)));
        gl_FragColor = vec4(col, a);
        #include <tonemapping_fragment>
        #include <colorspace_fragment>
      }`,
  });
}
function spawnFor(a) {
  if (a === 'pond') { const th = -Math.PI / 2 - 0.35, R = 8.2 + 1.6 * Math.sin(2 * th + 0.6) + 0.9 * Math.sin(3 * th + 2.0) + 0.5 * Math.sin(5 * th + 1.0); return [R * Math.cos(th) + Math.cos(th) * 1.2, -R * Math.sin(th) - Math.sin(th) * 1.2]; }
  const w = AREA.maple.way.position, cx = OX2;
  const d = new THREE.Vector2(cx - w.x, -w.z).normalize();
  return [w.x + d.x * 1.8, w.z + d.y * 1.8];
}
function enterArea(a, initial = false) {
  area = a; G.state.area = a; G.saveQuiet();
  AREA.pond.group.visible = a === 'pond'; AREA.maple.group.visible = a === 'maple';
  const A = AREA[a];
  shopAnchor = A.shop; shopMesh = A.shopMesh;
  if (shopAnchor) { shopLight.position.setFromMatrixPosition(shopAnchor.matrixWorld).add(new THREE.Vector3(0, 0.4, 0)); }
  lanternLight.visible = a === 'pond';
  fireflies.position.x = A.cx; mistGroup.position.x = A.cx;
  if (petals) { petals.position.set(a === 'maple' ? OX2 - 11.8 : 0, 0, a === 'maple' ? 4.2 : 0); petalU.uLeaf.value = a === 'maple' ? 1 : 0; }
  $('shoplabel').querySelector('span:not([data-i])').textContent = a === 'maple' ? 'Outfitters' : 'Tackle Shop';
  const [x, z] = spawnFor(a);
  player.teleport(x, z); player.lookAt(A.cx, 0, 0);
  if (!initial) player.apply();
  forceShadow = 3;
}
function travel(to) {
  if (to === area) { toast(`You are already at ${G.AREAS[to].name}`); return; }
  if (G.level() < G.AREAS[to].lvl) { toast(`${icon('lock', 16)} Reach level ${G.AREAS[to].lvl} to travel to ${G.AREAS[to].name}`); return; }
  if (fishing && fishing.busy()) { toast('Finish reeling in first'); return; }
  const fade = $('fade');
  fade.classList.add('on'); audio.sfx('up');
  setTimeout(() => {
    enterArea(to);
    toast(`${icon('map', 16)} Welcome to ${G.AREAS[to].name}`, 'good');
    setTimeout(() => fade.classList.remove('on'), 120);
  }, 650);
}
const grassSets = [];

async function loadGLB() {
  const bin = Uint8Array.from(atob(window.__POND_GLB_GZ), (c) => c.charCodeAt(0));
  const buf = await new Response(new Blob([bin]).stream().pipeThrough(new DecompressionStream('gzip'))).arrayBuffer();
  return new Promise((res, rej) => new GLTFLoader().parse(buf, '', res, rej));
}

function setup(gltf) {
  const root = gltf.scene; scene.add(root);
  const by = (n) => root.getObjectByName(n);
  const set = (n, mat, cast = false, recv = true) => { const o = by(n); if (!o) return null; o.material = mat; o.castShadow = cast; o.receiveShadow = recv; return o; };

  const terrain = set('Terrain', patchTerrain(lam({ color: new THREE.Color(1.32, 1.3, 1.2) })));
  const htex1 = buildHeightTexture(terrain, 0);
  const old = by('Water'); if (old) old.visible = false;
  const waterMesh = makeWater(0, htex1); scene.add(waterMesh); hideInRefl.push(waterMesh);

  // plants: flatten normals up, then split into chunks that are culled by distance
  const plant = (name, k, mat, cell, dist) => { const o = by(name); flattenNormalsUp(o.geometry, k); o.material = mat; o.receiveShadow = true; return chunkify(o, cell, dist); };
  const grassSet = plant('Grass', 0.8, patchSway(lam({ side: THREE.DoubleSide, color: new THREE.Color(1.38, 1.4, 1.08) }), { sway: 1, glow: 0.26 }), 12, () => Q.grass);
  const flowerSet = plant('Flowers', 0.7, patchSway(lam({ side: THREE.DoubleSide }), { sway: 1, gradient: false, glow: 0.12 }), 14, () => Q.grass * 0.85);
  const reedSet = plant('Reeds', 0.6, patchSway(lam({ side: THREE.DoubleSide, color: new THREE.Color(1.35, 1.35, 1.1) }), { sway: 1.25, glow: 0.22 }), 10, () => Q.grass * 1.1);
  const fernO = by('Ferns'); flattenNormalsUp(fernO.geometry, 0.5);
  fernO.material = patchSway(lam({ side: THREE.DoubleSide, color: new THREE.Color(1.3, 1.35, 1.1) }), { sway: 0.8, glow: 0.18 }); fernO.receiveShadow = true;
  const bushSet = (() => { const o = by('Bushes'); o.material = patchLeaves(lam({ color: new THREE.Color(1.4, 1.38, 1.25) })); o.receiveShadow = true; return chunkify(o, 16, () => Q.grass * 1.6); })();
  const folO = set('Foliage', patchLeaves(lam({ color: new THREE.Color(1.55, 1.52, 1.38) })), true, true);
  chunkify(folO, 22, 0);
  grassSets.push(grassSet, flowerSet);
  hideInRefl.push(grassSet.group, flowerSet.group, bushSet.group);

  for (const n of ['LilyPads', 'Lotus']) set(n, lam({ side: THREE.DoubleSide }), n === 'Lotus');
  set('Rocks', patchMat(lam({ color: new THREE.Color(2.0, 2.0, 1.9) }), { leaves: false }), true);
  set('Trunks', patchMat(lam({ color: new THREE.Color(1.5, 1.5, 1.5) }), { bark: true }), true);
  set('Dock', patchMat(lam(), { dock: 1 }), true); set('Lantern', lam(), true);
  set('Bench', lam({ color: new THREE.Color(1.5, 1.5, 1.4) }), true); set('Log', lam({ color: new THREE.Color(1.4, 1.4, 1.3) }), true);
  set('Mushrooms', lam({ color: new THREE.Color(1.3, 1.3, 1.3) }), false);
  const boat = set('Boat', lam({ side: THREE.DoubleSide, color: new THREE.Color(1.4, 1.4, 1.4) }), true);
  shopMesh = set('Shop', lam({ side: THREE.DoubleSide, color: new THREE.Color(1.45, 1.45, 1.4) }), true);
  shopGlowMat = lam({ emissive: new THREE.Color(1.0, 0.62, 0.22), emissiveIntensity: 1 }); set('ShopGlow', shopGlowMat, false, false);
  lanternMat = lam({ emissive: new THREE.Color(1.0, 0.62, 0.22), emissiveIntensity: 1 });
  const glow = set('LanternGlow', lanternMat, false, false);
  lanternLight.position.copy(new THREE.Box3().setFromObject(glow).getCenter(new THREE.Vector3()));
  shopAnchor = by('ShopAnchor');
  if (shopAnchor) { shopAnchor.updateMatrixWorld(); shopLight.position.setFromMatrixPosition(shopAnchor.matrixWorld).add(new THREE.Vector3(1.2, 0.6, 0)); }

  makePetals(11.8, -4.2);
  mistGroup = makeMist(); scene.add(mistGroup); hideInRefl.push(mistGroup);
  hideInRefl.push(fireflies, petals);

  weather = initWeather({ scene, camera, U, audio });
  hideInRefl.push(weather.mesh, fernO);
  weather.setMode(G.state.weather);
  let colliders = [];
  try { colliders = JSON.parse((by('PondMeta') && by('PondMeta').userData.colliders) || '[]').map(([x, y, r]) => [x, -y, r]); } catch (e) { /* none */ }
  let docks = [];
  try { docks = JSON.parse((by('PondMeta') && by('PondMeta').userData.docks) || '[]'); } catch (e) { /* none */ }
  player = initPlayer({ camera, canvas, hAt, WATER_Y, colliders, docks, isUIOpen: () => !$('modal').hidden, getSens: () => +G.state.sens || 1 });
  creatures = initCreatures({ root, scene, camera, addRipple, hAt, WATER_Y, audio, getPlayer: () => player, colliders });
  fishing = initFishing({ scene, camera, player, renderer, getArea: () => area, addRipple, hAt, WATER_Y, audio, getPreset: () => presetKey, getWeather: () => (weather ? weather.amount : 0) });

  initUI({
    audio,
    hooks: {
      onGear: () => fishing.applyGear(),
      onReplay: () => fishing.replayTutorial(),
      onSetting: (k, v) => { if (k === 'quality') setQualityMode(v); else if (k === 'weather') weather.setMode(v); },
      onOpen: () => player && player.exitLock(),
      onTravel: (to) => travel(to),
    },
  });
  fishing.applyGear();
  G.onChange(() => fishing.applyGear());

  hideInRefl.push(fishing.rod, fishing.line);
  window.__pond.boat = boat;

  /* ------------------------------ Maple Hollow ------------------------------ */
  const terrain2 = set('A2_Terrain', patchTerrain(lam({ color: new THREE.Color(1.3, 1.25, 1.15) })));
  const water2 = terrain2 ? makeWater(OX2, buildHeightTexture(terrain2, OX2)) : null;
  if (water2) { scene.add(water2); hideInRefl.push(water2); }
  const a2 = [];
  if (by('A2_Grass')) {
    const g2 = plant('A2_Grass', 0.8, patchSway(lam({ side: THREE.DoubleSide, color: new THREE.Color(1.3, 1.25, 1.0) }), { sway: 1, glow: 0.24 }), 12, () => Q.grass);
    const r2 = plant('A2_Reeds', 0.6, patchSway(lam({ side: THREE.DoubleSide, color: new THREE.Color(1.3, 1.25, 1.05) }), { sway: 1.25, glow: 0.2 }), 10, () => Q.grass * 1.1);
    const f2 = chunkify(set('A2_Foliage', patchLeaves(lam({ color: new THREE.Color(1.45, 1.35, 1.25) })), true, true), 22, 0);
    hideInRefl.push(g2.group);
    a2.push(g2.group, r2.group, f2.group);
  }
  set('A2_Leaves', patchMat(lam({ side: THREE.DoubleSide, color: new THREE.Color(1.3, 1.25, 1.2) }), {}), false, true);
  set('A2_FloatLeaves', lam({ side: THREE.DoubleSide, color: new THREE.Color(1.3, 1.25, 1.2) }));
  set('A2_Rocks', patchMat(lam({ color: new THREE.Color(1.9, 1.9, 1.85) }), {}), true);
  set('A2_Trunks', patchMat(lam({ color: new THREE.Color(1.5, 1.5, 1.5) }), { bark: true }), true);
  set('A2_Dock', patchMat(lam(), { dock: 1 }), true);
  shopMesh2 = set('A2_Shop', lam({ side: THREE.DoubleSide, color: new THREE.Color(1.45, 1.45, 1.4) }), true);
  set('A2_ShopGlow', shopGlowMat, false, false);
  const wf = by('A2_Waterfall');
  if (wf) { wf.material = waterfallMat(); wf.renderOrder = 3; wf.castShadow = false; hideInRefl.push(wf); falls = wf; wf.geometry.computeBoundingBox(); wf.geometry.boundingBox.getCenter(_fallsAt); }
  for (const [n, c] of [['Waystone1', 0x7ff0ff], ['A2_Waystone', 0xffb35a]]) {
    set(n, patchMat(lam({ color: new THREE.Color(1.6, 1.6, 1.55) }), {}), true);
    const gm = lam({ emissive: new THREE.Color(c), emissiveIntensity: 2.2 }); set(n + 'Glow', gm, false, false); wayGlowMats.push(gm);
  }
  AREA.pond.shop = shopAnchor; AREA.pond.shopMesh = shopMesh; AREA.pond.way = by('Waystone1Anchor');
  AREA.maple.shop = by('A2_ShopAnchor'); AREA.maple.shopMesh = shopMesh2; AREA.maple.way = by('A2_WaystoneAnchor');
  [AREA.maple.shop, AREA.pond.way, AREA.maple.way].forEach((o) => o && o.updateMatrixWorld());

  // sort everything into one group per area so only the place you are in is drawn
  const g1 = new THREE.Group(), g2 = new THREE.Group(); g1.name = 'Area_pond'; g2.name = 'Area_maple';
  scene.add(g1, g2);
  [...root.children].forEach((o) => { if (o.name === 'PondMeta') return; (o.name.startsWith('A2_') ? g2 : g1).attach(o); });
  chunkSets.forEach((cs) => { if (!a2.includes(cs.group)) g1.add(cs.group); });
  a2.forEach((g) => g2.add(g));
  g1.add(waterMesh, creatures.group); if (water2) g2.add(water2); g2.add(creatures.group2);
  AREA.pond.group = g1; AREA.maple.group = g2;
  const start = G.state.area === 'maple' && G.level() >= 10 ? 'maple' : 'pond';
  enterArea(start, start === 'maple');
}

/* -------------------------------- animate -------------------------------- */
const shopLabel = $('shoplabel'), _sp = new THREE.Vector3(), _q = new THREE.Quaternion();
let nearShop = false, nearWay = false;
const wayLabel = $('waylabel'), _wp = new THREE.Vector3(), _fallsAt = new THREE.Vector3();
const intro = { k: 1, from: new THREE.Vector3(), q0: new THREE.Quaternion() };
function animate() {
  const now = performance.now(), raw = (now - state.last) / 1000, dt = Math.min(raw, 0.05);
  state.last = now; state.frame++;
  U.uTime.value += dt;
  const t = U.uTime.value;
  gov.tick(raw);

  if (state.started) {
    tickClock(dt);
    if (player) {
      player.update(dt);
      if (intro.k < 1) {
        intro.k = Math.min(1, intro.k + Math.min(raw, 0.25) / 4.5);
        const e = 1 - Math.pow(1 - intro.k, 3);
        camera.position.lerpVectors(intro.from, player.pos, e);
        _q.setFromEuler(new THREE.Euler(player.pitch, player.yaw, 0, 'YXZ'));
        camera.quaternion.copy(intro.q0).slerp(_q, e);
        if (intro.k >= 1) player.freeze(false);
      } else player.apply();
      focus.set(player.pos.x, 0, player.pos.z);
    }
    if (weather) weather.update(dt);
    applyTime(dt);
    if (fishing) fishing.update(dt, t);
    if (creatures) creatures.update(dt, t, { day: cur.day, rain: weather ? weather.amount : 0 });
  }
  sky.position.copy(camera.position);
  cullChunks();

  // shop label follows the stall
  if (shopAnchor && shopLabel) {
    _sp.setFromMatrixPosition(shopAnchor.matrixWorld).add(new THREE.Vector3(0, 1.8, 0));
    const dist = camera.position.distanceTo(_sp);
    _sp.project(camera);
    nearShop = player ? Math.hypot(player.pos.x - shopAnchor.position.x, player.pos.z - shopAnchor.position.z) < 4.6 : false;
    shopLabel.classList.toggle('near', nearShop);
    const vis = _sp.z < 1 && dist < 34 && Math.abs(_sp.x) < 1.05 && Math.abs(_sp.y) < 1.05 && !document.body.classList.contains('clean');
    shopLabel.style.opacity = vis ? String(clamp(1.4 - dist / 26, 0.35, 1)) : '0';
    shopLabel.style.transform = `translate(-50%,-100%) translate(${(_sp.x * 0.5 + 0.5) * innerWidth}px, ${(-_sp.y * 0.5 + 0.5) * innerHeight}px)`;
  }

  // waystone: glow, prompt and travel
  const way = AREA[area] && AREA[area].way;
  if (way && wayLabel && player) {
    _wp.copy(way.position).add(new THREE.Vector3(0, 1.9, 0));
    const dist = camera.position.distanceTo(_wp);
    nearWay = Math.hypot(player.pos.x - way.position.x, player.pos.z - way.position.z) < 3.2;
    wayLabel.classList.toggle('near', nearWay);
    _wp.project(camera);
    const vis = _wp.z < 1 && dist < 26 && Math.abs(_wp.x) < 1.05 && Math.abs(_wp.y) < 1.05 && !document.body.classList.contains('clean');
    wayLabel.style.opacity = vis ? String(clamp(1.3 - dist / 22, 0.3, 1)) : '0';
    wayLabel.style.transform = `translate(-50%,-100%) translate(${(_wp.x * 0.5 + 0.5) * innerWidth}px, ${(-_wp.y * 0.5 + 0.5) * innerHeight}px)`;
  }
  wayGlowMats.forEach((m, i) => (m.emissiveIntensity = 1.6 + Math.sin(t * 2.2 + i) * 0.7));
  if (falls) audio.setFalls(area === 'maple' ? clamp(1 - camera.position.distanceTo(_fallsAt) / 30, 0, 1) : 0);

  if (state.frame % Math.max(1, Q.every) === 0 || forceShadow > 0) { renderer.shadowMap.needsUpdate = true; if (forceShadow > 0) forceShadow--; }
  if (state.started && Q.refl > 0 && state.frame % Q.reflEvery === 0) renderReflection();
  if (Q.post) composer.render(); else renderer.render(scene, camera);
  if (state.shot) {
    state.shot = false;
    canvas.toBlob((b) => { const a = document.createElement('a'); a.href = URL.createObjectURL(b); a.download = 'still-water.png'; a.click(); });
  }
  requestAnimationFrame(animate);
}

/* ---------------------------------- UI ----------------------------------- */
const TIMES = [['golden', 'sunset', 'Golden'], ['midday', 'sun', 'Day'], ['twilight', 'dusk', 'Dusk'], ['night', 'moon', 'Night']];
function setPreset(key) { clock = JUMP[key]; clockLabelT = 0; }
function setPauseBtn() { const b = $('cycle'); b.innerHTML = icon(G.state.paused ? 'play' : 'pause', 18); b.title = G.state.paused ? 'Resume the day cycle' : 'Pause the day cycle'; b.setAttribute('aria-label', b.title); b.classList.toggle('on', !!G.state.paused); }
function wireToolbar() {
  const tb = $('timebtns');
  tb.innerHTML = `<span id="clock" class="clock">--:--</span>` + TIMES.map(([k, ic, l]) => `<button data-time="${k}" aria-label="Jump to ${l}" title="Jump to ${l}">${icon(ic, 18)}<span>${l}</span></button>`).join('') + `<button id="cycle"></button>`;
  tb.addEventListener('click', (e) => { const b = e.target.closest('[data-time]'); if (b) setPreset(b.dataset.time); });
  $('cycle').addEventListener('click', () => { G.state.paused = !G.state.paused; G.commit(); setPauseBtn(); });
  setPauseBtn();
  $('sound').addEventListener('click', () => { const on = audio.toggle(); $('sound').classList.toggle('on', on); $('sound').innerHTML = `${icon(on ? 'soundOn' : 'soundOff', 18)}<span>${on ? 'Sound on' : 'Sound off'}</span>`; });
  $('shot').addEventListener('click', () => (state.shot = true));
  $('hide').addEventListener('click', () => document.body.classList.toggle('clean'));
  $('shopBtn').addEventListener('click', () => openPanel('shop'));
  $('journalBtn').addEventListener('click', () => openPanel('journal'));
  $('settingsBtn').addEventListener('click', () => openPanel('settings'));
  $('travelBtn').addEventListener('click', () => openPanel('travel'));
  $('waylabel').addEventListener('click', () => openPanel('travel'));
  $('sound').innerHTML = `${icon('soundOff', 18)}<span>Sound off</span>`;
}
addEventListener('keydown', (e) => {
  if (e.target && /INPUT|TEXTAREA/.test(e.target.tagName)) return;
  if (e.key === 'h' || e.key === 'H') document.body.classList.toggle('clean');
  const idx = ['1', '2', '3', '4'].indexOf(e.key);
  if (idx >= 0) setPreset(Object.keys(PRESETS)[idx]);
  if (e.code === 'KeyE' && nearWay && $('modal').hidden) { audio.sfx('click'); openPanel('travel'); return; }
  if (e.code === 'KeyE' && nearShop && $('modal').hidden) { audio.sfx('click'); openPanel('shop'); return; }
  if ((e.key === 't' || e.key === 'T') && $('modal').hidden) openPanel('travel');
  if (e.key === 'r' || e.key === 'R') { const nx = { auto: 'rain', rain: 'clear', clear: 'auto' }[G.state.weather] || 'auto'; G.state.weather = nx; G.commit(); weather && weather.setMode(nx); toast(`Weather: ${nx}`); }
});

// click: shop stall, or the water (cast / ripple)
let downX = 0, downY = 0;
const rc = new THREE.Raycaster(), ndc = new THREE.Vector2(), wplane = new THREE.Plane(new THREE.Vector3(0, 1, 0), -WATER_Y);
const setNdc = (e) => ndc.set((e.clientX / innerWidth) * 2 - 1, -(e.clientY / innerHeight) * 2 + 1);
canvas.addEventListener('pointerdown', (e) => { downX = e.clientX; downY = e.clientY; });
canvas.addEventListener('pointermove', (e) => {
  if (e.buttons || !shopMesh || e.pointerType === 'touch') return;
  setNdc(e); rc.setFromCamera(ndc, camera);
  canvas.style.cursor = rc.intersectObject(shopMesh, false).length ? 'pointer' : '';
});
canvas.addEventListener('pointerup', (e) => {
  if (!player || intro.k < 1) return;
  if (!player.locked && Math.hypot(e.clientX - downX, e.clientY - downY) > 5) return;
  // first mouse click captures the mouse for looking around (when the browser allows it)
  if (e.pointerType === 'mouse' && player.canLock && !player.locked) { player.requestLock(); return; }
  if (player.locked) ndc.set(0, 0); else setNdc(e);
  rc.setFromCamera(ndc, camera);
  if (shopMesh && rc.intersectObject(shopMesh, false).length && !(fishing && fishing.busy())) { audio.sfx('click'); openPanel('shop'); return; }
  if (fishing && fishing.busy()) return;
  const hit = rc.ray.intersectPlane(wplane, new THREE.Vector3());
  if (fishing && fishing.onWaterClick(hit && hAt(hit.x, hit.z) < WATER_Y - 0.05 ? hit : null)) return;
  if (hit && hAt(hit.x, hit.z) < WATER_Y - 0.05) {
    addRipple(hit.x, hit.z, 1.3);
    if (audio.on) audio.pluck(523.25 * Math.pow(2, [0, 2, 4, 7, 9][(Math.random() * 5) | 0] / 12), 0.1);
  }
});
$('shoplabel').addEventListener('click', () => openPanel('shop'));
addEventListener('resize', () => { camera.aspect = innerWidth / innerHeight; camera.updateProjectionMatrix(); setResolution(); });

window.__pond = { get creatures() { return creatures; }, scene, camera, get player() { return player; }, get clock() { return clock; }, set clock(v) { clock = v; }, U, renderer, composer, G, gov, get qMode() { return qMode; }, openPanel, setQualityMode, get qLevel() { return qLevel; }, get resScale() { return resScale; }, get Q() { return Q; }, setPreset, get weather() { return weather; } };

/* ---------------------------------- boot --------------------------------- */
(async () => {
  try {
    document.body.classList.toggle('coarse', coarse);
    document.querySelectorAll('[data-i]').forEach((el) => { el.innerHTML = icon(el.dataset.i, +el.dataset.s || 20); });
    wireToolbar();
    applyQuality();
    $('loadmsg').textContent = 'Unpacking the pond';
    const gltf = await loadGLB();
    $('loadmsg').textContent = 'Growing the grass';
    await new Promise((r) => setTimeout(r, 30));
    setup(gltf);
    applyQuality();
    applyTime(1);
    // intro: glide down from the sky into the player's eyes
    intro.from.set(-6, 22, 30); camera.position.copy(intro.from); camera.lookAt(0, 0, 0); intro.q0.copy(camera.quaternion); intro.k = 0;
    player.freeze(true);
    state.started = true;
    gov.reset();
    animate();
    document.body.classList.add('ready');
    window.__pondReady = true;
  } catch (e) {
    console.error(e);
    $('loadmsg').textContent = 'Could not load the scene: ' + e.message;
  }
})();
