import * as THREE from 'three';

/* Rain: streaks that follow the camera, plus a slow clear/rain state machine (or a fixed mode from settings). */
export function initWeather({ scene, camera, U, audio }) {
  const N = 1600;
  const pos = new Float32Array(N * 2 * 3), end = new Float32Array(N * 2), seed = new Float32Array(N * 2 * 3);
  for (let i = 0; i < N; i++) {
    const s = [Math.random(), Math.random(), Math.random()];
    for (let k = 0; k < 2; k++) { const j = i * 2 + k; seed.set(s, j * 3); end[j] = k; }
  }
  const g = new THREE.BufferGeometry();
  g.setAttribute('position', new THREE.BufferAttribute(pos, 3));
  g.setAttribute('aSeed', new THREE.BufferAttribute(seed, 3));
  g.setAttribute('aEnd', new THREE.BufferAttribute(end, 1));
  const uniforms = { uTime: U.uTime, uAmount: { value: 0 }, uCenter: { value: new THREE.Vector3() }, uLight: U.uLight };
  const m = new THREE.ShaderMaterial({
    uniforms, transparent: true, depthWrite: false,
    vertexShader: /* glsl */`attribute vec3 aSeed; attribute float aEnd; uniform float uTime, uAmount; uniform vec3 uCenter; varying float vA;
      void main(){
        float fall = fract(aSeed.z + uTime*(1.15 + aSeed.x*0.35));
        vec3 p = vec3((aSeed.x-0.5)*38.0, 17.0 - fall*21.0, (aSeed.y-0.5)*38.0);
        vec3 w = uCenter + p;
        w += vec3(0.5, -5.2, 0.25) * 0.06 * aEnd;
        vA = step(aSeed.y, uAmount) * (0.25 + 0.5*aSeed.x);
        gl_Position = projectionMatrix * viewMatrix * vec4(w, 1.0);
      }`,
    fragmentShader: /* glsl */`uniform float uLight; varying float vA; void main(){ gl_FragColor = vec4(vec3(0.75,0.85,1.0)*(0.35+0.65*uLight), vA*0.55); }`,
  });
  const rain = new THREE.LineSegments(g, m);
  rain.frustumCulled = false; rain.visible = false; rain.renderOrder = 5;
  scene.add(rain);

  const st = { mode: 'auto', target: 0, amount: 0, timer: rand(70, 140) };
  function rand(a, b) { return a + Math.random() * (b - a); }
  function setMode(mode) { st.mode = mode; if (mode === 'clear') st.target = 0; else if (mode === 'rain') st.target = 1; else { st.target = 0; st.timer = rand(40, 90); } }

  function update(dt) {
    if (st.mode === 'auto') {
      st.timer -= dt;
      if (st.timer <= 0) {
        if (st.target === 0) { if (Math.random() < 0.45) { st.target = 1; st.timer = rand(60, 120); } else st.timer = rand(60, 120); }
        else { st.target = 0; st.timer = rand(120, 240); }
      }
    }
    st.amount += (st.target - st.amount) * (1 - Math.exp(-dt * 0.35));
    if (Math.abs(st.target - st.amount) < 0.003) st.amount = st.target;
    rain.visible = st.amount > 0.02;
    uniforms.uAmount.value = st.amount;
    uniforms.uCenter.value.set(camera.position.x, camera.position.y - 4, camera.position.z);
    U.uRain.value = st.amount;
    audio.setRain(st.amount);
  }
  return { update, setMode, get amount() { return st.amount; }, mesh: rain };
}
