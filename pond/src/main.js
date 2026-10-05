import * as THREE from 'three';
import { GLTFLoader } from 'three/addons/loaders/GLTFLoader.js';
import { OrbitControls } from 'three/addons/controls/OrbitControls.js';
import { EffectComposer } from 'three/addons/postprocessing/EffectComposer.js';
import { RenderPass } from 'three/addons/postprocessing/RenderPass.js';
import { UnrealBloomPass } from 'three/addons/postprocessing/UnrealBloomPass.js';
import { OutputPass } from 'three/addons/postprocessing/OutputPass.js';
import { initFishing } from './fishing.js';

/* =====================================================================
   Still Water — scene geometry comes from Blender (pond.glb).
   Everything alive (water, sky, wind, light, creatures, sound) is added here.
   ===================================================================== */

const WATER_Y = -0.2;           // matches WATER_Z in build_pond.py
const EXT = 46, GRID = 230;     // terrain extent / cells
const clamp = (x, a, b) => Math.max(a, Math.min(b, x));
const lerp = (a, b, t) => a + (b - a) * t;
const $ = (id) => document.getElementById(id);

/* ------------------------------ shared GLSL ------------------------------ */
const NOISE = /* glsl */`
float hash21(vec2 p){ p = fract(p*vec2(123.34, 456.21)); p += dot(p, p+45.32); return fract(p.x*p.y); }
float vnoise(vec2 p){
  vec2 i = floor(p), f = fract(p); f = f*f*(3.0-2.0*f);
  float a = hash21(i), b = hash21(i+vec2(1,0)), c = hash21(i+vec2(0,1)), d = hash21(i+vec2(1,1));
  return mix(mix(a,b,f.x), mix(c,d,f.x), f.y);
}
float fbm(vec2 p){ float s = 0.0, a = 0.5; for(int i=0;i<4;i++){ s += a*vnoise(p); p = p*2.03 + 7.1; a *= 0.5; } return s; }
`;

const SKY_FN = /* glsl */`
uniform vec3 uTop, uHor, uSunDir, uSunCol;
vec3 skyBase(vec3 d){
  float h = clamp(d.y, 0.0, 1.0);
  vec3 c = mix(uHor, uTop, pow(h, 0.5));
  float s = max(dot(d, uSunDir), 0.0);
  c += uSunCol * (pow(s, 900.0)*6.0 + pow(s, 24.0)*0.5 + pow(s, 4.0)*0.22);
  c = mix(c, uHor*0.55, 1.0 - smoothstep(-0.25, 0.0, d.y));
  return c;
}
`;

/* ------------------------------- renderer -------------------------------- */
const canvas = $('c');
const renderer = new THREE.WebGLRenderer({ canvas, antialias: true, powerPreference: 'high-performance' });
const DPR = Math.min(window.devicePixelRatio || 1, 1.75);
renderer.setPixelRatio(DPR);
renderer.setSize(innerWidth, innerHeight);
renderer.toneMapping = THREE.ACESFilmicToneMapping;
renderer.shadowMap.enabled = true;
renderer.shadowMap.type = THREE.PCFShadowMap;
renderer.shadowMap.autoUpdate = false;   // updated once per frame (shared with the reflection pass)

const scene = new THREE.Scene();
scene.fog = new THREE.FogExp2(0xf2b992, 0.011);
const camera = new THREE.PerspectiveCamera(52, innerWidth / innerHeight, 0.1, 1200);
camera.position.set(-6, 22, 34);

const controls = new OrbitControls(camera, canvas);
controls.target.set(0, 0.9, 0);
controls.enableDamping = true;
controls.dampingFactor = 0.06;
controls.minDistance = 3;
controls.maxDistance = 23;
controls.maxPolarAngle = Math.PI * 0.485;
controls.autoRotate = true;
controls.autoRotateSpeed = 0.35;
controls.enablePan = false;

const LITE = /[?&]lite/.test(location.search);
const composer = new EffectComposer(renderer);
composer.addPass(new RenderPass(scene, camera));
const bloom = new UnrealBloomPass(new THREE.Vector2(innerWidth, innerHeight), 0.55, 0.7, 0.82);
composer.addPass(bloom);
composer.addPass(new OutputPass());

/* ------------------------------- uniforms -------------------------------- */
const U = {
  uTime: { value: 0 },
  uTop: { value: new THREE.Color() },
  uHor: { value: new THREE.Color() },
  uSunDir: { value: new THREE.Vector3(0, 1, 0) },
  uSunCol: { value: new THREE.Color() },
  uLight: { value: 1 },
  uStars: { value: 0 },
  uCloud: { value: 1 },
  uFogColor: { value: new THREE.Color() },
  uFogD: { value: 0.01 },
  uRip: { value: Array.from({ length: 12 }, () => new THREE.Vector4(0, 0, -100, 0)) },
  uHeight: { value: null },
  uScale: { value: innerHeight * DPR * 0.5 },
  uReflTex: { value: null },
  uReflMat: { value: new THREE.Matrix4() },
};

/* ------------------------------ time of day ------------------------------ */
const PRESETS = {
  golden: { label: 'Golden hour', top: '#3b66b0', hor: '#ffb27c', sunCol: '#ffa95e', sunInt: 4.4, el: 17, az: -118,
    hemiSky: '#ffe2c4', hemiGnd: '#7a9a3c', hemiInt: 1.55, fog: '#f0b48c', fogD: 0.011, stars: 0, cloud: 0.95,
    light: 1, lantern: 0.5, fire: 0.3, blink: 0.15, fireCol: '#ffd88a', bloom: 0.55, exposure: 1.1, mist: 0.3, petal: 1, day: 1 },
  midday: { label: 'Bright day', top: '#2a82d8', hor: '#cfe8ff', sunCol: '#fff1d6', sunInt: 4.0, el: 56, az: -70,
    hemiSky: '#d6eaff', hemiGnd: '#6f9a3a', hemiInt: 1.8, fog: '#c8e2f5', fogD: 0.007, stars: 0, cloud: 1.1,
    light: 1.1, lantern: 0.1, fire: 0.12, blink: 0.0, fireCol: '#ffffff', bloom: 0.3, exposure: 0.95, mist: 0.05, petal: 1, day: 1 },
  twilight: { label: 'Twilight', top: '#2b2766', hor: '#ff7d88', sunCol: '#ff7a5c', sunInt: 1.8, el: 5, az: -100,
    hemiSky: '#a58ae0', hemiGnd: '#4a4a6a', hemiInt: 1.0, fog: '#c87a96', fogD: 0.013, stars: 0.35, cloud: 0.8,
    light: 0.5, lantern: 1.6, fire: 0.75, blink: 0.7, fireCol: '#d8ff8a', bloom: 0.8, exposure: 1.25, mist: 0.5, petal: 0.8, day: 0.4 },
  night: { label: 'Moonlight', top: '#040a22', hor: '#1c2c58', sunCol: '#a9bfff', sunInt: 1.0, el: 38, az: -60,
    hemiSky: '#4a63b0', hemiGnd: '#1a2440', hemiInt: 0.75, fog: '#101b3b', fogD: 0.011, stars: 1, cloud: 0.35,
    light: 0.14, lantern: 2.4, fire: 1.0, blink: 1.0, fireCol: '#c8ff7a', bloom: 1.05, exposure: 1.45, mist: 0.45, petal: 0.55, day: 0 },
};
const NUM = ['sunInt', 'el', 'az', 'hemiInt', 'fogD', 'stars', 'cloud', 'light', 'lantern', 'fire', 'blink', 'bloom', 'exposure', 'mist', 'petal', 'day'];
const COL = ['top', 'hor', 'sunCol', 'hemiSky', 'hemiGnd', 'fog', 'fireCol'];
const mk = (p) => { const o = {}; NUM.forEach((k) => (o[k] = p[k])); COL.forEach((k) => (o[k] = new THREE.Color(p[k]))); return o; };
const cur = mk(PRESETS.golden);
let target = mk(PRESETS.golden);
let presetKey = 'golden';

const sun = new THREE.DirectionalLight(0xffffff, 3);
sun.castShadow = true;
sun.shadow.mapSize.set(innerWidth < 800 ? 2048 : 4096, innerWidth < 800 ? 2048 : 4096);
Object.assign(sun.shadow.camera, { left: -36, right: 36, top: 36, bottom: -36, near: 1, far: 160 });
sun.shadow.bias = -0.0004;
sun.shadow.normalBias = 0.04;
scene.add(sun, sun.target);
const hemi = new THREE.HemisphereLight(0xffffff, 0x445522, 0.8);
scene.add(hemi);
const fill = new THREE.DirectionalLight(0xffffff, 0.5);   // soft light from the viewer's side
scene.add(fill, fill.target);
const lanternLight = new THREE.PointLight(0xffb454, 0, 14, 1.6);
scene.add(lanternLight);

const fireU = { uTime: U.uTime, uScale: U.uScale, uAmount: { value: 0 }, uBlink: { value: 0 }, uColor: { value: new THREE.Color() } };
const petalU = { uTime: U.uTime, uScale: U.uScale, uLight: U.uLight, uAmount: { value: 1 } };
const mistU = { uTime: U.uTime, uFogColor: U.uFogColor, uAmount: { value: 0.3 }, uLight: U.uLight };
let lanternMat = null;

function applyTime(dt) {
  const k = 1 - Math.exp(-dt * 1.7);
  NUM.forEach((n) => (cur[n] += (target[n] - cur[n]) * k));
  COL.forEach((n) => cur[n].lerp(target[n], k));
  const el = THREE.MathUtils.degToRad(cur.el), az = THREE.MathUtils.degToRad(cur.az);
  U.uSunDir.value.set(Math.cos(el) * Math.cos(az), Math.sin(el), Math.cos(el) * Math.sin(az)).normalize();
  U.uTop.value.copy(cur.top); U.uHor.value.copy(cur.hor); U.uSunCol.value.copy(cur.sunCol);
  U.uLight.value = cur.light; U.uStars.value = cur.stars; U.uCloud.value = cur.cloud;
  U.uFogColor.value.copy(cur.fog); U.uFogD.value = cur.fogD;
  scene.fog.color.copy(cur.fog); scene.fog.density = cur.fogD;
  sun.color.copy(cur.sunCol); sun.intensity = cur.sunInt;
  sun.position.copy(controls.target).addScaledVector(U.uSunDir.value, 70);
  sun.target.position.copy(controls.target);
  hemi.color.copy(cur.hemiSky); hemi.groundColor.copy(cur.hemiGnd); hemi.intensity = cur.hemiInt;
  fill.color.copy(cur.hor).lerp(new THREE.Color(1, 1, 1), 0.5); fill.intensity = cur.hemiInt * 0.5 * (0.35 + 0.65 * cur.light);
  fill.position.set(camera.position.x, 0, camera.position.z).setLength(40).setY(26); fill.target.position.copy(controls.target);
  bloom.strength = cur.bloom; renderer.toneMappingExposure = cur.exposure;
  const flick = 1 + Math.sin(U.uTime.value * 7.3) * 0.04 + Math.sin(U.uTime.value * 12.1) * 0.03;
  lanternLight.intensity = cur.lantern * 9 * flick;
  if (lanternMat) { lanternMat.emissiveIntensity = (0.35 + cur.lantern * 2.2) * flick; }
  fireU.uAmount.value = cur.fire; fireU.uBlink.value = cur.blink; fireU.uColor.value.copy(cur.fireCol);
  petalU.uAmount.value = cur.petal; mistU.uAmount.value = cur.mist;
  audio.setMix(cur.day, 1 - cur.day);
}

/* -------------------------------- sky dome ------------------------------- */
const sky = new THREE.Mesh(
  new THREE.SphereGeometry(500, 48, 24),
  new THREE.ShaderMaterial({
    side: THREE.BackSide, depthWrite: false, fog: false,
    uniforms: U,
    vertexShader: /* glsl */`varying vec3 vD; void main(){ vD = position; gl_Position = projectionMatrix*modelViewMatrix*vec4(position,1.0); gl_Position.z = gl_Position.w; }`,
    fragmentShader: /* glsl */`
      varying vec3 vD; uniform float uTime, uStars, uCloud, uLight;
      ${NOISE}${SKY_FN}
      void main(){
        vec3 d = normalize(vD);
        vec3 c = skyBase(d);
        // stars
        if (uStars > 0.01 && d.y > 0.0){
          vec2 uv = d.xz/(d.y+0.35)*55.0; vec2 id = floor(uv); vec2 f = fract(uv)-0.5;
          float h = hash21(id); vec2 o = (vec2(hash21(id+3.1), hash21(id+9.7))-0.5)*0.6;
          float s = step(0.965, h)*(1.0 - smoothstep(0.0, 0.22, length(f-o)));
          c += vec3(0.8,0.9,1.0)*s*(0.55+0.45*sin(uTime*1.7+h*60.0))*uStars*smoothstep(0.0,0.3,d.y);
        }
        // clouds
        float hh = max(d.y, 0.0);
        vec2 cuv = d.xz/(hh+0.28)*1.5 + vec2(uTime*0.004, uTime*0.002);
        float n = fbm(cuv) * 0.7 + fbm(cuv*2.3 + 4.0) * 0.3;
        float cov = smoothstep(0.46, 0.8, n) * smoothstep(0.02, 0.28, d.y) * uCloud;
        float sunAmt = pow(max(dot(d, uSunDir), 0.0), 3.0);
        vec3 cc = mix(uHor*0.9 + 0.2, uSunCol*1.2, sunAmt*0.8) * (0.28 + 0.75*uLight);
        cc = mix(cc*0.7, cc*1.15, smoothstep(0.5, 0.85, n));
        c = mix(c, cc, cov*0.9);
        gl_FragColor = vec4(c, 1.0);
      }`,
  })
);
sky.renderOrder = -10;
sky.frustumCulled = false;
scene.add(sky);

/* ------------------------------- water mesh ------------------------------ */
function makeWater() {
  const m = new THREE.ShaderMaterial({
    transparent: true, depthWrite: false,
    uniforms: U,
    vertexShader: /* glsl */`varying vec3 vWP; void main(){ vec4 w = modelMatrix*vec4(position,1.0); vWP = w.xyz; gl_Position = projectionMatrix*viewMatrix*w; }`,
    fragmentShader: /* glsl */`
      varying vec3 vWP;
      uniform float uTime, uLight, uFogD; uniform vec3 uFogColor; uniform sampler2D uHeight, uReflTex; uniform mat4 uReflMat; uniform vec4 uRip[12];
      ${NOISE}${SKY_FN}
      float caustic(vec2 p){
        float a = 0.0; vec2 q = p;
        for(int i=0;i<3;i++){ q += vec2(sin(q.y*1.7+uTime*0.6), cos(q.x*1.5-uTime*0.5))*0.45; a += abs(sin(q.x*2.0)*sin(q.y*2.0)); }
        return pow(1.0 - a/3.0, 3.0);
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
        return g;
      }
      void main(){
        vec2 hUV = vec2((vWP.x+${EXT}.0)/${(EXT * 2).toFixed(1)}, (-vWP.z+${EXT}.0)/${(EXT * 2).toFixed(1)});
        float th = texture2D(uHeight, hUV).r;
        float depth = ${WATER_Y.toFixed(2)} - th;
        if(depth <= 0.0) discard;
        vec3 V = normalize(cameraPosition - vWP);
        vec2 g = waves(vWP.xz, uTime);
        vec3 N = normalize(vec3(-g.x, 1.0, -g.y));
        float ndv = max(dot(N, V), 0.0);
        float fres = 0.04 + 0.96*pow(1.0 - ndv, 4.0);
        vec3 R = reflect(-V, N); R.y = abs(R.y);
        vec4 rc = uReflMat * vec4(vWP, 1.0);
        vec2 ruv = rc.xy / rc.w + N.xz * 0.22;
        vec3 refl = texture2D(uReflTex, ruv).rgb;
        refl = mix(skyBase(R), refl, 0.97);
        // body colour: shallow jade -> deep blue-green
        vec3 shallow = vec3(0.12,0.5,0.4), deepc = vec3(0.01,0.12,0.16);
        vec3 body = mix(shallow, deepc, smoothstep(0.08, 1.5, depth));
        float lit = 0.12 + 0.88*uLight;
        body *= lit;
        float cs = caustic(vWP.xz*1.6) * (1.0 - smoothstep(0.0, 1.3, depth));
        body += uSunCol * cs * 0.22 * uLight;
        vec3 col = mix(body, refl, clamp(fres, 0.0, 1.0));
        // glitter
        vec3 H = normalize(V + uSunDir);
        float spec = pow(max(dot(N, H), 0.0), 500.0);
        float sparkle = smoothstep(0.55, 1.0, vnoise(vWP.xz*7.0 + uTime*0.8));
        col += uSunCol * spec * (3.0 + 7.0*sparkle);
        // shoreline foam + soft edge
        float foam = (1.0 - smoothstep(0.0, 0.16, depth)) * (0.55 + 0.45*vnoise(vWP.xz*6.0 + uTime*0.3));
        col = mix(col, vec3(0.85,0.95,0.9)*lit, foam*0.55);
        float alpha = mix(0.32, 0.8, smoothstep(0.05, 1.6, depth));
        alpha = max(alpha, fres);
        alpha *= smoothstep(0.0, 0.05, depth);
        // fog
        float dist = length(cameraPosition - vWP);
        float f = 1.0 - exp(-pow(dist*uFogD, 2.0));
        col = mix(col, uFogColor, f);
        gl_FragColor = vec4(col, alpha);
        #include <tonemapping_fragment>
        #include <colorspace_fragment>
      }`,
  });
  const mesh = new THREE.Mesh(new THREE.PlaneGeometry(130, 130).rotateX(-Math.PI / 2), m);
  mesh.position.y = WATER_Y;
  mesh.renderOrder = 2;
  return mesh;
}

/* --------------------------- material overrides -------------------------- */
function patchSway(mat, { sway = 1, gradient = true, glow = 0 } = {}) {
  mat.onBeforeCompile = (sh) => {
    sh.uniforms.uTime = U.uTime;
    sh.uniforms.uSway = { value: sway };
    sh.vertexShader = sh.vertexShader
      .replace('#include <common>', `#include <common>\nuniform float uTime; uniform float uSway; varying float vT;`)
      .replace('#include <begin_vertex>', `
        vec3 transformed = vec3(position);
        float sw = uv.y * uv.y; float ph = uv.x * 6.2831;
        float gust = sin(uTime*0.45 + position.x*0.07 + position.z*0.05)*0.5 + 0.5;
        float w1 = sin(uTime*1.35 + position.x*0.33 + position.z*0.21 + ph);
        float w2 = cos(uTime*1.1 + position.z*0.37 + ph*1.7);
        transformed.x += (w1*0.045 + gust*0.1) * sw * uSway;
        transformed.z += (w2*0.04 + gust*0.04) * sw * uSway;
        transformed.y -= (abs(w1)*0.01 + gust*0.02) * sw * uSway;
        vT = uv.y;`);
    sh.fragmentShader = sh.fragmentShader
      .replace('#include <common>', `#include <common>\nvarying float vT;`)
      .replace('#include <color_fragment>', `#include <color_fragment>
        ${gradient ? 'diffuseColor.rgb *= mix(vec3(0.38,0.5,0.34), vec3(1.25,1.2,0.85), vT);' : ''}`)
      .replace('#include <emissivemap_fragment>', `#include <emissivemap_fragment>
        totalEmissiveRadiance += diffuseColor.rgb * ${glow.toFixed(2)} * vT * vT;`);
  };
  mat.customProgramCacheKey = () => `sway${sway}-${gradient}-${glow}`;
  return mat;
}

function patchTerrain(mat) {
  mat.onBeforeCompile = (sh) => {
    sh.vertexShader = sh.vertexShader
      .replace('#include <common>', '#include <common>\nvarying vec3 vWP2;')
      .replace('#include <begin_vertex>', '#include <begin_vertex>\nvWP2 = position;');
    sh.fragmentShader = sh.fragmentShader
      .replace('#include <common>', `#include <common>\nvarying vec3 vWP2;\n${NOISE}`)
      .replace('#include <color_fragment>', `#include <color_fragment>
        float tn = fbm(vWP2.xz*1.4)*0.55 + fbm(vWP2.xz*7.0)*0.45;
        diffuseColor.rgb *= 0.74 + 0.5*tn;`);
  };
  mat.customProgramCacheKey = () => 'terrain';
  return mat;
}

function patchLeaves(mat) {
  mat.onBeforeCompile = (sh) => {
    sh.uniforms.uTime = U.uTime;
    sh.vertexShader = sh.vertexShader
      .replace('#include <common>', '#include <common>\nuniform float uTime;')
      .replace('#include <begin_vertex>', `#include <begin_vertex>
        float lh = clamp(position.y*0.1, 0.0, 1.0);
        transformed.x += sin(uTime*0.9 + position.x*0.5 + position.y*0.4)*0.05*lh;
        transformed.z += cos(uTime*0.8 + position.z*0.5 + position.y*0.3)*0.05*lh;`);
  };
  mat.customProgramCacheKey = () => 'leaves';
  return mat;
}

function flattenNormalsUp(geo, k) {
  const n = geo.attributes.normal;
  for (let i = 0; i < n.count; i++) {
    const x = n.getX(i) * (1 - k), y = n.getY(i) * (1 - k) + k, z = n.getZ(i) * (1 - k);
    const l = Math.hypot(x, y, z) || 1;
    n.setXYZ(i, x / l, y / l, z / l);
  }
  n.needsUpdate = true;
}

/* ----------------------------- terrain helpers --------------------------- */
let heights = null;
function hAt(x, z) {
  if (!heights) return 0;
  const u = clamp((x + EXT) / (2 * EXT) * GRID, 0, GRID - 1.001);
  const v = clamp((-z + EXT) / (2 * EXT) * GRID, 0, GRID - 1.001);
  const i = Math.floor(u), j = Math.floor(v), fu = u - i, fv = v - j, W = GRID + 1;
  const a = heights[j * W + i], b = heights[j * W + i + 1], c = heights[(j + 1) * W + i], d = heights[(j + 1) * W + i + 1];
  return lerp(lerp(a, b, fu), lerp(c, d, fu), fv);
}

function buildHeightTexture(terrainMesh) {
  const pos = terrainMesh.geometry.attributes.position;
  const W = GRID + 1;
  heights = new Float32Array(W * W).fill(-2);
  for (let i = 0; i < pos.count; i++) {
    const ix = Math.round((pos.getX(i) + EXT) / (2 * EXT) * GRID);
    const iy = Math.round((-pos.getZ(i) + EXT) / (2 * EXT) * GRID);
    if (ix >= 0 && ix < W && iy >= 0 && iy < W) heights[iy * W + ix] = pos.getY(i);
  }
  const half = new Uint16Array(W * W);
  for (let i = 0; i < half.length; i++) half[i] = THREE.DataUtils.toHalfFloat(heights[i]);
  const tex = new THREE.DataTexture(half, W, W, THREE.RedFormat, THREE.HalfFloatType);
  tex.minFilter = tex.magFilter = THREE.LinearFilter;
  tex.wrapS = tex.wrapT = THREE.ClampToEdgeWrapping;
  tex.needsUpdate = true;
  return tex;
}

/* ----------------------------- reflections ------------------------------- */
const reflRT = new THREE.WebGLRenderTarget(64, 64, { type: THREE.HalfFloatType });
function sizeRefl() {
  const sc = 0.6;
  reflRT.setSize(Math.max(64, Math.floor(innerWidth * DPR * sc)), Math.max(64, Math.floor(innerHeight * DPR * sc)));
}
sizeRefl();
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
  reflCam.projectionMatrix.copy(camera.projectionMatrix);
  reflCam.projectionMatrixInverse.copy(camera.projectionMatrixInverse);
  reflCam.updateMatrixWorld();
  U.uReflMat.value.set(0.5, 0, 0, 0.5, 0, 0.5, 0, 0.5, 0, 0, 0.5, 0.5, 0, 0, 0, 1)
    .multiply(reflCam.projectionMatrix).multiply(reflCam.matrixWorldInverse);
  hideInRefl.forEach((o) => { o._v = o.visible; o.visible = false; });
  renderer.shadowMap.needsUpdate = true;
  renderer.clippingPlanes = [clipPlane];
  renderer.setRenderTarget(reflRT);
  renderer.clear();
  renderer.render(scene, reflCam);
  renderer.setRenderTarget(null);
  renderer.clippingPlanes = [];
  hideInRefl.forEach((o) => (o.visible = o._v));
}

/* ------------------------------ ripples ---------------------------------- */
let ripIdx = 0;
function addRipple(x, z, amp = 1) {
  U.uRip.value[ripIdx].set(x, z, U.uTime.value, amp);
  ripIdx = (ripIdx + 1) % 12;
}

/* ------------------------------ particles -------------------------------- */
function makePoints(n, region, vs, fs, uniforms, blending) {
  const pos = new Float32Array(n * 3), seed = new Float32Array(n * 4);
  for (let i = 0; i < n; i++) {
    pos[i * 3] = region.x + (Math.random() - 0.5) * region.w;
    pos[i * 3 + 1] = region.y + Math.random() * region.h;
    pos[i * 3 + 2] = region.z + (Math.random() - 0.5) * region.d;
    for (let k = 0; k < 4; k++) seed[i * 4 + k] = Math.random();
  }
  const g = new THREE.BufferGeometry();
  g.setAttribute('position', new THREE.BufferAttribute(pos, 3));
  g.setAttribute('aSeed', new THREE.BufferAttribute(seed, 4));
  const m = new THREE.ShaderMaterial({ uniforms, vertexShader: vs, fragmentShader: fs, transparent: true, depthWrite: false, blending });
  const p = new THREE.Points(g, m);
  p.frustumCulled = false;
  return p;
}

const fireflies = makePoints(
  520, { x: 0, y: 0.35, z: 0, w: 56, h: 3.4, d: 56 },
  /* glsl */`attribute vec4 aSeed; uniform float uTime, uScale, uAmount, uBlink; varying float vA;
    void main(){
      vec3 p = position; float t = uTime;
      p.x += sin(t*0.4 + aSeed.x*30.0)*1.3 + sin(t*0.9 + aSeed.y*17.0)*0.3;
      p.y += sin(t*0.5 + aSeed.y*40.0)*0.5 + 0.2*sin(t*1.3 + aSeed.z*9.0);
      p.z += cos(t*0.35 + aSeed.z*25.0)*1.3;
      float b = mix(1.0, 0.5 + 0.5*sin(t*(1.2 + aSeed.w*1.6) + aSeed.x*60.0), uBlink);
      b = b*b;
      float on = step(aSeed.w, uAmount);     // fewer flies when the amount is low
      vA = b * on * (0.35 + 0.65*min(uAmount*1.4, 1.0));
      vec4 mv = viewMatrix * vec4(p, 1.0);
      gl_PointSize = clamp((0.16 + 0.1*aSeed.z) * uScale / -mv.z, 1.0, 44.0);
      gl_Position = projectionMatrix * mv;
    }`,
  /* glsl */`uniform vec3 uColor; varying float vA;
    void main(){ float d = length(gl_PointCoord - 0.5); float a = 1.0 - smoothstep(0.0, 0.5, d); a = a*a; gl_FragColor = vec4(uColor*(1.0 + a*2.5), a*vA); }`,
  fireU, THREE.AdditiveBlending
);
scene.add(fireflies);

let petals = null;
function makePetals(cx, cz) {
  petals = makePoints(
    650, { x: cx, y: 0, z: cz, w: 34, h: 0, d: 34 },
    /* glsl */`attribute vec4 aSeed; uniform float uTime, uScale; varying float vA; varying float vR; varying vec3 vC;
      void main(){
        float life = fract(aSeed.w + uTime*(0.018 + aSeed.z*0.012));
        vec3 p = position;
        p.y = 6.5 - life*7.0;
        p.x += sin(life*14.0 + aSeed.z*20.0)*0.9 + life*7.0 + sin(uTime*0.3)*1.5;
        p.z += cos(life*11.0 + aSeed.x*20.0)*0.9 - life*2.0;
        vA = smoothstep(0.0, 0.08, life) * (1.0 - smoothstep(0.9, 1.0, life)) * step(-0.15, p.y);
        vR = life*20.0 + aSeed.x*6.28;
        vC = mix(vec3(1.0,0.72,0.82), vec3(1.0,0.9,0.94), aSeed.y);
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
      }`,
    petalU, THREE.NormalBlending
  );
  scene.add(petals);
}

/* --------------------------------- mist ---------------------------------- */
function makeMist() {
  const g = new THREE.Group();
  for (let i = 0; i < 2; i++) {
    const m = new THREE.Mesh(
      new THREE.CircleGeometry(15 + i * 3, 48).rotateX(-Math.PI / 2),
      new THREE.ShaderMaterial({
        transparent: true, depthWrite: false, uniforms: { ...mistU, uLayer: { value: i } },
        vertexShader: /* glsl */`varying vec3 vP; varying vec2 vL; void main(){ vP = (modelMatrix*vec4(position,1.0)).xyz; vL = position.xz; gl_Position = projectionMatrix*viewMatrix*modelMatrix*vec4(position,1.0); }`,
        fragmentShader: /* glsl */`
          varying vec3 vP; varying vec2 vL; uniform float uTime, uAmount, uLight, uLayer; uniform vec3 uFogColor;
          ${NOISE}
          void main(){
            float n = fbm(vP.xz*0.16 + vec2(uTime*0.012*(1.0+uLayer), uTime*0.007) + uLayer*9.0);
            float edge = 1.0 - smoothstep(0.55, 1.0, length(vL)/${(15).toFixed(1)});
            float cd = length(cameraPosition - vP);
            float soft = smoothstep(1.0, 7.0, cd);
            float a = smoothstep(0.35, 0.8, n) * edge * soft * uAmount * (0.6 - 0.15*uLayer);
            vec3 c = uFogColor*(0.9 + 0.4*uLight) + 0.1;
            gl_FragColor = vec4(c, a);
            #include <tonemapping_fragment>
            #include <colorspace_fragment>
          }`,
      })
    );
    m.position.set(0, WATER_Y + 0.35 + i * 0.9, 0);
    m.renderOrder = 3 + i;
    g.add(m);
  }
  return g;
}

/* ------------------------------ audio ------------------------------------ */
class Ambience {
  constructor() { this.on = false; this.ctx = null; this.day = 1; this.night = 0; }
  setMix(d, n) {
    this.day = d; this.night = n;
    if (this.ctx && this.on) {
      this.birdBus.gain.setTargetAtTime(d, this.ctx.currentTime, 1.5);
      this.cricket.gain.setTargetAtTime(0.018 * n, this.ctx.currentTime, 1.5);
    }
  }
  noise(seconds, brown) {
    const c = this.ctx, b = c.createBuffer(1, c.sampleRate * seconds, c.sampleRate), d = b.getChannelData(0);
    let last = 0;
    for (let i = 0; i < d.length; i++) {
      const w = Math.random() * 2 - 1;
      if (brown) { last = (last + 0.02 * w) / 1.02; d[i] = last * 3.5; } else d[i] = w;
    }
    const s = c.createBufferSource(); s.buffer = b; s.loop = true; return s;
  }
  init() {
    const c = (this.ctx = new (window.AudioContext || window.webkitAudioContext)());
    this.master = c.createGain(); this.master.gain.value = 0; this.master.connect(c.destination);
    // reverb
    const len = c.sampleRate * 3.4, ir = c.createBuffer(2, len, c.sampleRate);
    for (let ch = 0; ch < 2; ch++) { const d = ir.getChannelData(ch); for (let i = 0; i < len; i++) d[i] = (Math.random() * 2 - 1) * Math.pow(1 - i / len, 2.6); }
    this.verb = c.createConvolver(); this.verb.buffer = ir;
    const vg = c.createGain(); vg.gain.value = 0.55; this.verb.connect(vg); vg.connect(this.master);
    this.dry = c.createGain(); this.dry.gain.value = 0.7; this.dry.connect(this.master); this.dry.connect(this.verb);
    // wind
    const wind = this.noise(6, true), wf = c.createBiquadFilter(); wf.type = 'bandpass'; wf.frequency.value = 420; wf.Q.value = 0.5;
    const wg = c.createGain(); wg.gain.value = 0.22;
    const wl = c.createOscillator(), wlg = c.createGain(); wl.frequency.value = 0.09; wlg.gain.value = 0.12; wl.connect(wlg); wlg.connect(wg.gain);
    wind.connect(wf); wf.connect(wg); wg.connect(this.master); wind.start(); wl.start();
    // water babble
    const wat = this.noise(5, false), wlp = c.createBiquadFilter(); wlp.type = 'bandpass'; wlp.frequency.value = 1500; wlp.Q.value = 0.9;
    const wag = c.createGain(); wag.gain.value = 0.018;
    const wal = c.createOscillator(), walg = c.createGain(); wal.frequency.value = 0.35; walg.gain.value = 0.009; wal.connect(walg); walg.connect(wag.gain);
    wat.connect(wlp); wlp.connect(wag); wag.connect(this.master); wat.start(); wal.start();
    // crickets
    this.cricket = c.createGain(); this.cricket.gain.value = 0;
    const co = c.createOscillator(); co.frequency.value = 4300;
    const am = c.createGain(); am.gain.value = 0.5;
    const lfo = c.createOscillator(); lfo.type = 'square'; lfo.frequency.value = 17;
    const lg = c.createGain(); lg.gain.value = 0.5; lfo.connect(lg); lg.connect(am.gain);
    const lfo2 = c.createOscillator(); lfo2.frequency.value = 0.4; const l2g = c.createGain(); l2g.gain.value = 0.5; lfo2.connect(l2g); l2g.connect(this.cricket.gain);
    co.connect(am); am.connect(this.cricket); this.cricket.connect(this.master); co.start(); lfo.start(); lfo2.start();
    // bird bus
    this.birdBus = c.createGain(); this.birdBus.gain.value = this.day; this.birdBus.connect(this.dry);
    this.scheduleNote(); this.scheduleBird();
  }
  pluck(freq, vel = 0.12) {
    if (!this.ctx || !this.on) return;
    const c = this.ctx, t = c.currentTime;
    const o = c.createOscillator(), o2 = c.createOscillator(), g = c.createGain();
    o.type = 'sine'; o2.type = 'triangle'; o.frequency.value = freq; o2.frequency.value = freq * 2.003;
    const g2 = c.createGain(); g2.gain.value = 0.25;
    g.gain.setValueAtTime(0, t); g.gain.linearRampToValueAtTime(vel, t + 0.012); g.gain.exponentialRampToValueAtTime(0.0005, t + 3.2);
    o.connect(g); o2.connect(g2); g2.connect(g); g.connect(this.dry);
    o.start(t); o2.start(t); o.stop(t + 3.4); o2.stop(t + 3.4);
  }
  scheduleNote() {
    const scale = [0, 2, 4, 7, 9, 12, 14, 16, 19];
    const next = () => {
      if (this.on && Math.random() < 0.75) this.pluck(220 * Math.pow(2, scale[(Math.random() * scale.length) | 0] / 12), 0.05 + Math.random() * 0.06);
      setTimeout(next, 1400 + Math.random() * 3600);
    };
    setTimeout(next, 2500);
  }
  scheduleBird() {
    const next = () => {
      if (this.on && this.day > 0.3 && Math.random() < 0.7) {
        const c = this.ctx, n = 2 + ((Math.random() * 3) | 0), base = 2400 + Math.random() * 1600;
        for (let i = 0; i < n; i++) {
          const t = c.currentTime + i * 0.13, o = c.createOscillator(), g = c.createGain();
          o.frequency.setValueAtTime(base, t); o.frequency.exponentialRampToValueAtTime(base * (1.25 + Math.random() * 0.3), t + 0.09);
          g.gain.setValueAtTime(0, t); g.gain.linearRampToValueAtTime(0.03, t + 0.015); g.gain.exponentialRampToValueAtTime(0.0003, t + 0.1);
          o.connect(g); g.connect(this.birdBus); o.start(t); o.stop(t + 0.12);
        }
      }
      setTimeout(next, 2500 + Math.random() * 6000);
    };
    setTimeout(next, 3000);
  }
  sfx(kind) {
    if (!this.ctx || !this.on) return;
    const seq = {
      cast: [[196, 0], [147, 0.08]], splash: [[330, 0]], bite: [[880, 0], [1175, 0.1], [880, 0.2]],
      hook: [[392, 0], [523, 0.07]], miss: [[294, 0], [220, 0.14], [165, 0.28]],
      win: [[523, 0], [659, 0.1], [784, 0.2], [1047, 0.32]],
      legend: [[392, 0], [523, 0.1], [659, 0.2], [784, 0.3], [1047, 0.42], [1319, 0.56], [1568, 0.72]],
      up: [[440, 0], [554, 0.09], [659, 0.18]],
    }[kind] || [];
    seq.forEach(([f, d]) => setTimeout(() => this.pluck(f, kind === 'miss' ? 0.07 : 0.1), d * 1000));
  }
  toggle() {
    if (!this.ctx) this.init();
    this.on = !this.on;
    if (this.ctx.state === 'suspended') this.ctx.resume();
    this.master.gain.setTargetAtTime(this.on ? 0.9 : 0, this.ctx.currentTime, 0.4);
    this.setMix(this.day, this.night);
    return this.on;
  }
}
const audio = new Ambience();

/* ----------------------------- load the scene ---------------------------- */
window.__pond = { scene, camera, controls, U, renderer, composer, LITE };
let fishing = null;
const state = { koi: [], ducks: {}, bflies: [], last: performance.now(), shot: false, started: false };

async function loadGLB() {
  const b64 = window.__POND_GLB_GZ;
  const bin = Uint8Array.from(atob(b64), (c) => c.charCodeAt(0));
  const stream = new Blob([bin]).stream().pipeThrough(new DecompressionStream('gzip'));
  const buf = await new Response(stream).arrayBuffer();
  return new Promise((res, rej) => new GLTFLoader().parse(buf, '', res, rej));
}

function stdMat(opts = {}) {
  return new THREE.MeshStandardMaterial({ vertexColors: true, roughness: 0.85, metalness: 0, ...opts });
}

function setup(gltf) {
  const root = gltf.scene;
  scene.add(root);
  const byName = (n) => root.getObjectByName(n);

  const terrain = byName('Terrain');
  terrain.material = patchTerrain(stdMat({ roughness: 0.96, color: new THREE.Color(1.5, 1.5, 1.35) }));
  terrain.receiveShadow = true;
  U.uHeight.value = buildHeightTexture(terrain);

  const old = byName('Water'); if (old) old.visible = false;
  const waterMesh = makeWater(); scene.add(waterMesh); hideInRefl.push(waterMesh);

  const grass = byName('Grass');
  flattenNormalsUp(grass.geometry, 0.8);
  grass.material = patchSway(stdMat({ roughness: 0.6, side: THREE.DoubleSide, color: new THREE.Color(1.7, 1.75, 1.2) }), { sway: 1, glow: 0.3 });
  grass.receiveShadow = true;

  const reeds = byName('Reeds');
  flattenNormalsUp(reeds.geometry, 0.6);
  reeds.material = patchSway(stdMat({ roughness: 0.6, side: THREE.DoubleSide, color: new THREE.Color(1.6, 1.6, 1.2) }), { sway: 1.25, glow: 0.25 });
  reeds.receiveShadow = true;

  const flowers = byName('Flowers');
  flattenNormalsUp(flowers.geometry, 0.7);
  flowers.material = patchSway(stdMat({ roughness: 0.7, side: THREE.DoubleSide }), { sway: 1, gradient: false, glow: 0.12 });
  flowers.receiveShadow = true;

  for (const n of ['LilyPads', 'Lotus']) {
    const o = byName(n);
    o.material = stdMat({ roughness: 0.5, side: THREE.DoubleSide });
    o.receiveShadow = true; o.castShadow = n === 'Lotus';
  }
  const rocks = byName('Rocks'); rocks.material = stdMat({ roughness: 0.95, color: new THREE.Color(2.0, 2.0, 1.9) }); rocks.castShadow = rocks.receiveShadow = true;
  const trunks = byName('Trunks'); trunks.material = stdMat({ roughness: 0.95, color: new THREE.Color(1.5, 1.5, 1.5) }); trunks.castShadow = trunks.receiveShadow = true;
  const foliage = byName('Foliage'); foliage.material = patchLeaves(stdMat({ roughness: 0.8, color: new THREE.Color(1.7, 1.7, 1.45) })); foliage.castShadow = foliage.receiveShadow = true;
  const dock = byName('Dock'); dock.material = stdMat({ roughness: 0.85 }); dock.castShadow = dock.receiveShadow = true;
  const lant = byName('Lantern'); lant.material = stdMat({ roughness: 0.9 }); lant.castShadow = lant.receiveShadow = true;
  const glow = byName('LanternGlow');
  lanternMat = stdMat({ roughness: 0.5, emissive: new THREE.Color(1.0, 0.62, 0.22), emissiveIntensity: 1 });
  glow.material = lanternMat;
  const gb = new THREE.Box3().setFromObject(glow); const gc = gb.getCenter(new THREE.Vector3());
  lanternLight.position.copy(gc);
  lanternLight.castShadow = false;

  // animals
  const animalMat = stdMat({ roughness: 0.55, side: THREE.DoubleSide });
  root.traverse((o) => {
    if (!o.isMesh) return;
    if (/^(Duck|Koi|Bfly)/.test(o.name)) { o.material = animalMat; o.castShadow = /^Duck/.test(o.name); o.receiveShadow = false; }
  });
  state.ducks.drake = byName('Duck_Drake'); state.ducks.hen = byName('Duck_Hen');
  state.ducks.chicks = [0, 1, 2].map((i) => byName('Duck_Chick' + i));
  state.koi = [0, 1, 2, 3, 4].map((i) => byName('Koi_' + i));

  // butterflies: clone the Blender-made parts, recolour by shuffling vertex-colour channels
  const bw = [byName('BflyWingL'), byName('BflyWingR'), byName('BflyBody')];
  bw.forEach((o) => (o.visible = false));
  const perms = [[0, 1, 2], [0, 2, 1], [2, 1, 0], [1, 0, 2], [1, 2, 0], [0, 1, 2]];
  for (let i = 0; i < 6; i++) {
    const grp = new THREE.Group();
    const p = perms[i];
    const parts = bw.map((src, k) => {
      const g = src.geometry.clone();
      if (k < 2 && i > 0) {
        const c = g.attributes.color, a = c.itemSize;
        for (let v = 0; v < c.count; v++) {
          const r = [c.getX(v), c.getY(v), c.getZ(v)];
          c.setXYZ(v, r[p[0]], r[p[1]], r[p[2]]);
        }
      }
      const m = new THREE.Mesh(g, animalMat);
      m.position.set(0, 0, 0); m.castShadow = false;
      return m;
    });
    parts.forEach((m) => grp.add(m));
    grp.userData = { wl: parts[0], wr: parts[1], t: Math.random() * 100, cx: (Math.random() - 0.5) * 22, cz: (Math.random() - 0.5) * 22 + 3, r: 3 + Math.random() * 5, s: 0.18 + Math.random() * 0.14, ph: Math.random() * 6, fl: 16 + Math.random() * 6 };
    grp.scale.setScalar(2.1);
    scene.add(grp);
    state.bflies.push(grp);
  }
  const mistG = makeMist(); scene.add(mistG); hideInRefl.push(mistG);

  // find cherry tree position for falling petals (pinkish foliage); fixed from build script
  makePetals(11.8, -4.2);

  fishing = initFishing({ scene, camera, controls, addRipple, hAt, WATER_Y, audio, getPreset: () => presetKey });

  // keep camera out of the ground
  controls.addEventListener('change', () => {
    const g = Math.max(hAt(camera.position.x, camera.position.z) + 0.6, WATER_Y + 0.45);
    if (camera.position.y < g) camera.position.y = g;
  });
}

/* -------------------------------- animate -------------------------------- */
const heading = (x0, z0, x1, z1) => Math.atan2(x1 - x0, z1 - z0);
function hen(t) { return [-1.6 + Math.sin(t * 0.13) * 2.4 + Math.sin(t * 0.31) * 0.5, 1.8 + Math.sin(t * 0.17 + 1.0) * 1.9]; }
function drake(t) { return [1.4 + Math.sin(t * 0.11 + 2.0) * 2.3, -0.8 + Math.sin(t * 0.15) * 2.2 + Math.sin(t * 0.4) * 0.3]; }
function koiPath(i, t) {
  const a = t * (0.18 + i * 0.025) + i * 1.9, r = 1.4 + (i % 3) * 0.55;
  return [Math.cos(a) * r * 1.35 + Math.sin(t * 0.2 + i) * 0.5, Math.sin(a * 1.1) * r];
}
let nextFish = 3, rippleTimer = 0;

function animate() {
  const now = performance.now(), dt = Math.min((now - state.last) / 1000, 0.05);
  state.last = now;
  U.uTime.value += dt;
  const t = U.uTime.value;
  applyTime(dt);

  if (state.started) {
    if (fishing) fishing.update(dt, t);
    // ducks
    const { drake: dk, hen: hn, chicks } = state.ducks;
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
    // koi
    state.koi.forEach((k, i) => {
      const [x, z] = koiPath(i, t), [x2, z2] = koiPath(i, t + 0.1);
      k.position.set(x, -0.36 - (i % 2) * 0.12 + Math.sin(t * 0.7 + i) * 0.03, z);
      k.rotation.y = heading(x, z, x2, z2) + Math.sin(t * 5 + i * 3) * 0.14;
      k.rotation.z = Math.sin(t * 5 + i * 3 + 1) * 0.06;
    });
    // butterflies
    state.bflies.forEach((b) => {
      const d = b.userData; const tt = t * d.s + d.t;
      const x = d.cx + Math.sin(tt * 1.0 + d.ph) * d.r + Math.sin(tt * 2.3) * 1.2;
      const z = d.cz + Math.cos(tt * 0.83) * d.r;
      const x2 = d.cx + Math.sin((tt + 0.02) * 1.0 + d.ph) * d.r + Math.sin((tt + 0.02) * 2.3) * 1.2;
      const z2 = d.cz + Math.cos((tt + 0.02) * 0.83) * d.r;
      const y = Math.max(hAt(x, z), WATER_Y) + 0.9 + Math.sin(tt * 3.1) * 0.35 + Math.sin(t * 2 + d.ph) * 0.1;
      b.position.set(x, y, z); b.rotation.y = heading(x, z, x2, z2);
      const f = 0.25 + Math.sin(t * d.fl + d.ph) * 0.85;
      d.wl.rotation.z = f; d.wr.rotation.z = -f;
      b.visible = cur.day > 0.25 || b.userData.ph < 0;
    });
    // fish plinks
    nextFish -= dt;
    if (nextFish < 0) {
      nextFish = 3 + Math.random() * 5;
      const a = Math.random() * 6.28, r = Math.random() * 3.6;
      addRipple(Math.cos(a) * r, Math.sin(a) * r, 0.9);
      if (audio.on) audio.pluck(1046 * (Math.random() < 0.5 ? 1 : 1.5), 0.035);
    }
  }
  controls.update();
  sky.position.copy(camera.position);
  if (state.started) renderReflection();
  if (LITE) renderer.render(scene, camera); else composer.render();
  if (state.shot) {
    state.shot = false;
    canvas.toBlob((b) => { const a = document.createElement('a'); a.href = URL.createObjectURL(b); a.download = 'still-water.png'; a.click(); });
  }
  requestAnimationFrame(animate);
}

/* ---------------------------------- UI ----------------------------------- */
function setPreset(key) {
  presetKey = key; target = mk(PRESETS[key]);
  document.querySelectorAll('[data-time]').forEach((b) => b.classList.toggle('on', b.dataset.time === key));
  $('mood').textContent = PRESETS[key].label;
}
document.querySelectorAll('[data-time]').forEach((b) => b.addEventListener('click', () => { cycling = false; $('cycle').classList.remove('on'); setPreset(b.dataset.time); }));
let cycling = false, cycleT = 0;
$('cycle').addEventListener('click', () => { cycling = !cycling; $('cycle').classList.toggle('on', cycling); cycleT = 0; });
setInterval(() => {
  if (!cycling) return;
  const keys = Object.keys(PRESETS); setPreset(keys[(keys.indexOf(presetKey) + 1) % keys.length]);
}, 14000);

$('sound').addEventListener('click', () => { const on = audio.toggle(); $('sound').classList.toggle('on', on); $('sound').textContent = on ? '🔊 Sound on' : '🔈 Sound off'; });
$('orbit').addEventListener('click', () => {
  controls.autoRotate = !controls.autoRotate; $('orbit').classList.toggle('on', controls.autoRotate);
  if (controls.autoRotate && fishing) fishing.resetCamera();
});
$('shot').addEventListener('click', () => (state.shot = true));
$('hide').addEventListener('click', () => document.body.classList.toggle('clean'));
addEventListener('keydown', (e) => {
  if (e.key === 'h' || e.key === 'H') document.body.classList.toggle('clean');
  const idx = ['1', '2', '3', '4'].indexOf(e.key);
  if (idx >= 0) setPreset(Object.keys(PRESETS)[idx]);
});

// click on the water: ripple + a soft note
let downX = 0, downY = 0;
canvas.addEventListener('pointerdown', (e) => { downX = e.clientX; downY = e.clientY; });
canvas.addEventListener('pointerup', (e) => {
  if (Math.hypot(e.clientX - downX, e.clientY - downY) > 5) return;
  const ndc = new THREE.Vector2((e.clientX / innerWidth) * 2 - 1, -(e.clientY / innerHeight) * 2 + 1);
  const rc = new THREE.Raycaster(); rc.setFromCamera(ndc, camera);
  const hit = rc.ray.intersectPlane(new THREE.Plane(new THREE.Vector3(0, 1, 0), -WATER_Y), new THREE.Vector3());
  if (fishing && fishing.busy()) return;
  if (hit && hAt(hit.x, hit.z) < WATER_Y - 0.05) {
    if (fishing && fishing.onWaterClick(hit)) return;
    addRipple(hit.x, hit.z, 1.3);
    if (audio.on) audio.pluck(523.25 * Math.pow(2, [0, 2, 4, 7, 9][(Math.random() * 5) | 0] / 12), 0.1);
  }
});

addEventListener('resize', () => {
  camera.aspect = innerWidth / innerHeight; camera.updateProjectionMatrix();
  renderer.setSize(innerWidth, innerHeight); composer.setSize(innerWidth, innerHeight);
  U.uScale.value = innerHeight * DPR * 0.5;
  sizeRefl();
});

/* ---------------------------------- boot --------------------------------- */
(async () => {
  try {
    $('loadmsg').textContent = 'Unpacking the pond…';
    const gltf = await loadGLB();
    $('loadmsg').textContent = 'Growing the grass…';
    await new Promise((r) => setTimeout(r, 30));
    setup(gltf);
    applyTime(1);
    // intro fly-in
    const from = new THREE.Vector3(-6, 22, 34), to = new THREE.Vector3(-9, 5.2, 17.5);
    state.started = true;
    animate();
    const t0 = performance.now();
    controls.enabled = false;
    const fly = () => {
      const k = clamp((performance.now() - t0) / 6500, 0, 1), e = 1 - Math.pow(1 - k, 3);
      camera.position.lerpVectors(from, to, e);
      controls.update();
      if (k < 1) requestAnimationFrame(fly); else controls.enabled = true;
    };
    fly();
    document.body.classList.add('ready');
    window.__pondReady = true;
  } catch (e) {
    console.error(e);
    $('loadmsg').textContent = 'Could not load the scene: ' + e.message;
  }
})();
