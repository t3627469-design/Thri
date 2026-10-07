/* Generated ambience and sound effects (no audio files). */
export class Ambience {
  constructor() { this.on = false; this.ctx = null; this.day = 1; this.night = 0; this.rain = 0; this.vol = 0.9; }
  setVolume(v) { this.vol = v; if (this.ctx && this.on) this.master.gain.setTargetAtTime(v, this.ctx.currentTime, 0.2); }
  setMix(d, n) {
    this.day = d; this.night = n;
    if (this.ctx && this.on) {
      this.birdBus.gain.setTargetAtTime(d * (1 - this.rain), this.ctx.currentTime, 1.5);
      this.cricket.gain.setTargetAtTime(0.018 * n * (1 - this.rain * 0.6), this.ctx.currentTime, 1.5);
    }
  }
  setFalls(a) {
    this.fallsAmt = a;
    if (this.ctx && this.on && this.fallsGain) this.fallsGain.gain.setTargetAtTime(0.22 * a * a, this.ctx.currentTime, 0.5);
  }
  setRain(a) {
    this.rain = a;
    if (this.ctx && this.on) this.rainGain.gain.setTargetAtTime(0.16 * a, this.ctx.currentTime, 1.2);
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
    const len = c.sampleRate * 3.4, ir = c.createBuffer(2, len, c.sampleRate);
    for (let ch = 0; ch < 2; ch++) { const d = ir.getChannelData(ch); for (let i = 0; i < len; i++) d[i] = (Math.random() * 2 - 1) * Math.pow(1 - i / len, 2.6); }
    this.verb = c.createConvolver(); this.verb.buffer = ir;
    const vg = c.createGain(); vg.gain.value = 0.55; this.verb.connect(vg); vg.connect(this.master);
    this.dry = c.createGain(); this.dry.gain.value = 0.7; this.dry.connect(this.master); this.dry.connect(this.verb);
    const wind = this.noise(6, true), wf = c.createBiquadFilter(); wf.type = 'bandpass'; wf.frequency.value = 420; wf.Q.value = 0.5;
    const wg = c.createGain(); wg.gain.value = 0.22;
    const wl = c.createOscillator(), wlg = c.createGain(); wl.frequency.value = 0.09; wlg.gain.value = 0.12; wl.connect(wlg); wlg.connect(wg.gain);
    wind.connect(wf); wf.connect(wg); wg.connect(this.master); wind.start(); wl.start();
    const wat = this.noise(5, false), wlp = c.createBiquadFilter(); wlp.type = 'bandpass'; wlp.frequency.value = 1500; wlp.Q.value = 0.9;
    const wag = c.createGain(); wag.gain.value = 0.018;
    const wal = c.createOscillator(), walg = c.createGain(); wal.frequency.value = 0.35; walg.gain.value = 0.009; wal.connect(walg); walg.connect(wag.gain);
    wat.connect(wlp); wlp.connect(wag); wag.connect(this.master); wat.start(); wal.start();
    // rain
    const rn = this.noise(4, false), rhp = c.createBiquadFilter(); rhp.type = 'highpass'; rhp.frequency.value = 1800;
    const rlp = c.createBiquadFilter(); rlp.type = 'lowpass'; rlp.frequency.value = 7500;
    this.rainGain = c.createGain(); this.rainGain.gain.value = 0;
    rn.connect(rhp); rhp.connect(rlp); rlp.connect(this.rainGain); this.rainGain.connect(this.master); rn.start();
    // waterfall: a low rushing roar that fades in as you get close
    const fl = this.noise(5, true), flp = c.createBiquadFilter(); flp.type = 'lowpass'; flp.frequency.value = 900;
    const fhp = c.createBiquadFilter(); fhp.type = 'highpass'; fhp.frequency.value = 120;
    this.fallsGain = c.createGain(); this.fallsGain.gain.value = 0;
    fl.connect(fhp); fhp.connect(flp); flp.connect(this.fallsGain); this.fallsGain.connect(this.master); fl.start();
    // crickets
    this.cricket = c.createGain(); this.cricket.gain.value = 0;
    const co = c.createOscillator(); co.frequency.value = 4300;
    const am = c.createGain(); am.gain.value = 0.5;
    const lfo = c.createOscillator(); lfo.type = 'square'; lfo.frequency.value = 17;
    const lg = c.createGain(); lg.gain.value = 0.5; lfo.connect(lg); lg.connect(am.gain);
    const lfo2 = c.createOscillator(); lfo2.frequency.value = 0.4; const l2g = c.createGain(); l2g.gain.value = 0.5; lfo2.connect(l2g); l2g.connect(this.cricket.gain);
    co.connect(am); am.connect(this.cricket); this.cricket.connect(this.master); co.start(); lfo.start(); lfo2.start();
    this.birdBus = c.createGain(); this.birdBus.gain.value = this.day; this.birdBus.connect(this.dry);
    this.scheduleNote(); this.scheduleBird();
  }
  pluck(freq, vel = 0.12, dur = 3.2) {
    if (!this.ctx || !this.on) return;
    const c = this.ctx, t = c.currentTime;
    const o = c.createOscillator(), o2 = c.createOscillator(), g = c.createGain();
    o.type = 'sine'; o2.type = 'triangle'; o.frequency.value = freq; o2.frequency.value = freq * 2.003;
    const g2 = c.createGain(); g2.gain.value = 0.25;
    g.gain.setValueAtTime(0, t); g.gain.linearRampToValueAtTime(vel, t + 0.012); g.gain.exponentialRampToValueAtTime(0.0005, t + dur);
    o.connect(g); o2.connect(g2); g2.connect(g); g.connect(this.dry);
    o.start(t); o2.start(t); o.stop(t + dur + 0.2); o2.stop(t + dur + 0.2);
  }
  croak() {
    if (!this.ctx || !this.on) return;
    const c = this.ctx, t = c.currentTime;
    for (let i = 0; i < 3; i++) {
      const o = c.createOscillator(), g = c.createGain(), f = c.createBiquadFilter();
      o.type = 'sawtooth'; o.frequency.setValueAtTime(150 + i * 12, t + i * 0.11); o.frequency.exponentialRampToValueAtTime(95, t + i * 0.11 + 0.09);
      f.type = 'bandpass'; f.frequency.value = 480; f.Q.value = 3;
      g.gain.setValueAtTime(0, t + i * 0.11); g.gain.linearRampToValueAtTime(0.05, t + i * 0.11 + 0.015); g.gain.exponentialRampToValueAtTime(0.0003, t + i * 0.11 + 0.1);
      o.connect(f); f.connect(g); g.connect(this.dry); o.start(t + i * 0.11); o.stop(t + i * 0.11 + 0.12);
    }
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
      if (this.on && this.day * (1 - this.rain) > 0.3 && Math.random() < 0.7) {
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
      up: [[440, 0], [554, 0.09], [659, 0.18]], coin: [[1318, 0], [1760, 0.07]], buy: [[659, 0], [880, 0.08], [1175, 0.17]],
      level: [[523, 0], [659, 0.1], [784, 0.2], [1047, 0.3], [784, 0.42], [1047, 0.52], [1319, 0.64]],
      click: [[900, 0]],
    }[kind] || [];
    seq.forEach(([f, d]) => setTimeout(() => this.pluck(f, kind === 'miss' ? 0.07 : kind === 'click' ? 0.04 : 0.1, kind === 'click' || kind === 'coin' ? 0.5 : 3.2), d * 1000));
  }
  toggle() {
    if (!this.ctx) this.init();
    this.on = !this.on;
    if (this.ctx.state === 'suspended') this.ctx.resume();
    this.master.gain.setTargetAtTime(this.on ? this.vol : 0, this.ctx.currentTime, 0.4);
    this.setMix(this.day, this.night); this.setRain(this.rain);
    return this.on;
  }
}
