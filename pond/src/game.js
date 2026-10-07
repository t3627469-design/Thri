/* Game data and save state: species, rods, baits, floats, bag, levels, mutations. */

export const RARITY = [
  { name: 'Common', color: '#b8c4c9' }, { name: 'Uncommon', color: '#7fd36b' }, { name: 'Rare', color: '#58a6ff' },
  { name: 'Epic', color: '#b987ff' }, { name: 'Legendary', color: '#ffc857' }, { name: 'Exotic', color: '#ff6fae', cls: 'rainbow-t' },
  { name: 'Secret', color: '#ffffff', cls: 'secret-t' },
];

// [id, name, rarity, kg[min,max], value[min,max], difficulty, dart, [back, belly, accent], shape, tail, pattern, weight, time/weather hint]
const RAW = [
  ['minnow', 'Pond Minnow', 0, [0.05, 0.25], [4, 8], 0.18, 0.05, ['#b9c6cc', '#eef3f4', '#6f8590'], 'slim', 'fork', 'lateral', 40],
  ['bluegill', 'Bluegill', 0, [0.2, 0.7], [6, 12], 0.28, 0.1, ['#4f8fbf', '#f3b24a', '#1f3f66'], 'deep', 'fork', 'bands', 32],
  ['perch', 'Striped Perch', 0, [0.3, 1.1], [8, 14], 0.34, 0.1, ['#7ba33c', '#f0c96a', '#2a4a14'], 'trout', 'fork', 'stripes', 26],
  ['loach', 'Mud Loach', 0, [0.1, 0.5], [5, 9], 0.3, 0.2, ['#8a7a55', '#cbbd8f', '#4a3f26'], 'eel', 'round', 'spots', 24],
  ['dace', 'Silver Dace', 0, [0.1, 0.4], [5, 10], 0.24, 0.15, ['#cfd8de', '#ffffff', '#8da0ab'], 'slim', 'fork', 'scales', 28],
  ['pumpkin', 'Pumpkinseed', 0, [0.2, 0.6], [7, 12], 0.3, 0.1, ['#e08a2e', '#f7d56b', '#2b7a8c'], 'deep', 'fork', 'spots', 22],
  ['bleak', 'Reed Bleak', 0, [0.05, 0.2], [4, 7], 0.2, 0.2, ['#a6c4a0', '#e8f0e0', '#5b7a58'], 'slim', 'fork', 'plain', 26],
  ['chub', 'Brook Chub', 0, [0.4, 1.4], [8, 14], 0.36, 0.1, ['#8a9a62', '#d9d4a4', '#4d5a30'], 'trout', 'fork', 'scales', 20],

  ['goldcarp', 'Golden Carp', 1, [0.8, 3.2], [24, 40], 0.45, 0.1, ['#f2b632', '#ffe08a', '#b8741a'], 'carp', 'fan', 'scales', 12],
  ['trout', 'Rainbow Trout', 1, [0.6, 2.4], [22, 36], 0.5, 0.2, ['#d98ba6', '#f4e1e8', '#4aa6b0'], 'trout', 'fork', 'lateral+spots', 10],
  ['tench', 'Copper Tench', 1, [1, 3.5], [24, 38], 0.46, 0.05, ['#8a6a3a', '#d4b07a', '#5a4020'], 'carp', 'round', 'plain', 11],
  ['rudd', 'Sunset Rudd', 1, [0.3, 1.2], [20, 32], 0.42, 0.15, ['#c0752d', '#f6c07a', '#d9402a'], 'deep', 'fork', 'scales', 11],
  ['mirror', 'Mirror Carp', 1, [1.5, 5], [26, 42], 0.48, 0.05, ['#8d99a6', '#e4e8ee', '#3b4a5a'], 'carp', 'fan', 'spots', 9],
  ['goby', 'Calico Goby', 1, [0.05, 0.3], [22, 34], 0.4, 0.35, ['#d9a066', '#f4e4c8', '#7a4a2a'], 'eel', 'round', 'patches', 9],
  ['roach', 'Lantern Roach', 1, [0.2, 0.9], [22, 34], 0.44, 0.2, ['#e3b04a', '#fff2c0', '#ffd23f'], 'deep', 'fork', 'glow', 8, 'dusk'],
  ['bream', 'Mossback Bream', 1, [0.8, 2.8], [24, 38], 0.47, 0.05, ['#5a7a3a', '#cfd9a0', '#2f4a1a'], 'deep', 'fork', 'scales', 10],

  ['koi', 'Pond Koi', 2, [1.5, 5], [70, 110], 0.62, 0.1, ['#fff3e6', '#ffffff', '#ff6a2a'], 'carp', 'fan', 'patches', 5],
  ['moonpike', 'Moonlit Pike', 2, [2, 6], [90, 130], 0.68, 0.25, ['#9fb7ff', '#e8efff', '#4a5fb0'], 'pike', 'fork', 'spots', 2.5, 'night'],
  ['eel', 'Glass Eel', 2, [0.3, 1.2], [80, 120], 0.66, 0.4, ['#cfe6ee', '#f4fbff', '#6fa6b8'], 'eel', 'round', 'plain', 3, 'night'],
  ['gar', 'Blue Gar', 2, [1.5, 5], [85, 125], 0.64, 0.3, ['#3f78a8', '#cfe0ee', '#1f3f66'], 'pike', 'round', 'spots', 4],
  ['catfish', 'Ghost Catfish', 2, [2, 7], [88, 130], 0.66, 0.1, ['#b9c0c8', '#eef0f3', '#6a7480'], 'cat', 'round', 'plain', 3, 'rain'],
  ['silk', 'Silk Carp', 2, [1.5, 4.5], [80, 115], 0.6, 0.1, ['#ff9ec4', '#ffe3ee', '#d9588a'], 'carp', 'fan', 'scales', 4],
  ['zander', 'Stormy Zander', 2, [1.2, 4], [84, 120], 0.65, 0.3, ['#5f6f86', '#cfd6de', '#2b3548'], 'pike', 'fork', 'stripes', 3, 'rain'],
  ['willow', 'Willow Pike', 2, [2, 6], [86, 126], 0.67, 0.2, ['#6f8a3a', '#d9e0a8', '#2f4a14'], 'pike', 'fork', 'spots', 4],

  ['ember', 'Ember Carp', 3, [2, 5.5], [180, 240], 0.78, 0.3, ['#ff4a1c', '#ffb347', '#8a1a08'], 'carp', 'fan', 'scales', 1.2, 'dusk'],
  ['aurora', 'Aurora Trout', 3, [1.5, 4], [190, 260], 0.8, 0.35, ['#5fd6b0', '#b8a0ff', '#ff7ac8'], 'trout', 'fork', 'lateral+glow', 1, 'night'],
  ['lotuskoi', 'Lotus Koi', 3, [2, 6], [200, 270], 0.78, 0.15, ['#fff0f5', '#ffffff', '#ff7ab0'], 'carp', 'fan', 'patches', 1, 'day'],
  ['obsidian', 'Obsidian Pike', 3, [3, 8], [210, 280], 0.82, 0.4, ['#2a2e3a', '#6a7080', '#9fb7ff'], 'pike', 'fork', 'glow', 0.9, 'night'],
  ['sturgeon', 'Amber Sturgeon', 3, [6, 16], [220, 300], 0.8, 0.1, ['#c58a3a', '#f0d49a', '#7a4a14'], 'cat', 'round', 'scales', 0.9],
  ['frostfin', 'Frostfin', 3, [1, 3], [190, 250], 0.79, 0.3, ['#bfe8ff', '#ffffff', '#6fb8ff'], 'trout', 'fork', 'glow', 1, 'rain'],

  ['jade', 'Jade Dragonfish', 4, [6, 14], [500, 700], 0.92, 0.45, ['#2fd6a1', '#f4e29a', '#0f7a5a'], 'dragon', 'fan', 'scales', 0.35],
  ['arowana', 'Starlight Arowana', 4, [4, 10], [520, 720], 0.9, 0.4, ['#e8d9a6', '#ffffff', '#ffd23f'], 'dragon', 'fan', 'glow', 0.3, 'night'],
  ['lunker', 'Ancient Lunker', 4, [20, 45], [560, 760], 0.88, 0.2, ['#5a6a4a', '#b9c49a', '#2a3320'], 'carp', 'round', 'spots', 0.3],
  ['crown', 'Sunken Crown Carp', 4, [8, 18], [600, 820], 0.9, 0.2, ['#ffc857', '#fff3c0', '#8a5a0a'], 'carp', 'fan', 'glow', 0.28, 'dusk'],

  ['spirit', 'Spirit Koi', 5, [3, 8], [1500, 2000], 0.96, 0.55, ['#d6f0ff', '#ffffff', '#7ad9ff'], 'dragon', 'fan', 'glow+scales', 0.06],
  ['heart', 'Heart of the Pond', 5, [1, 2], [3000, 4000], 0.98, 0.6, ['#ff7ab0', '#ffd6e8', '#ffffff'], 'deep', 'fan', 'glow+scales', 0.03],

  // secret: only bites once you have landed 100 fish
  // ---- Maple Hollow (unlocked at level 10) ----
  ['shiner', 'Maple Shiner', 0, [0.1, 0.4], [22, 34], 0.32, 0.15, ['#d8743a', '#fbe2c4', '#8a3a1a'], 'slim', 'fork', 'lateral', 40, null, 'maple'],
  ['leafperch', 'Leaf Perch', 0, [0.3, 1.2], [26, 38], 0.38, 0.12, ['#b88a2a', '#f4dca0', '#6a4a14'], 'trout', 'fork', 'stripes', 34, null, 'maple'],
  ['acorn', 'Acorn Sunfish', 0, [0.2, 0.8], [24, 36], 0.36, 0.1, ['#8a5a2a', '#e8b860', '#3a2a14'], 'deep', 'fork', 'spots', 30, null, 'maple'],
  ['copperback', 'Copperback Trout', 1, [0.8, 2.6], [60, 90], 0.52, 0.22, ['#c06a3a', '#f2d2b8', '#5a2a14'], 'trout', 'fork', 'lateral+spots', 14, null, 'maple'],
  ['lanternchar', 'Lantern Char', 1, [0.6, 2.2], [64, 96], 0.55, 0.25, ['#3a5a6a', '#ff8a3a', '#ffd060'], 'trout', 'fork', 'spots', 12, 'dusk', 'maple'],
  ['russet', 'Russet Carp', 1, [1.5, 5], [70, 100], 0.5, 0.08, ['#9a4a2a', '#e8b890', '#5a2010'], 'carp', 'fan', 'scales', 12, null, 'maple'],
  ['ghostsalmon', 'Ghost Salmon', 2, [2, 7], [180, 260], 0.68, 0.3, ['#d8dee4', '#ffffff', '#9aa6b0'], 'trout', 'fork', 'glow', 5, 'night', 'maple'],
  ['ironjaw', 'Ironjaw Pike', 2, [3, 9], [200, 280], 0.7, 0.35, ['#5a646e', '#c8ced4', '#2a3038'], 'pike', 'fork', 'stripes', 5, null, 'maple'],
  ['harvestkoi', 'Harvest Koi', 2, [2, 6], [190, 270], 0.66, 0.12, ['#fff0d8', '#ffffff', '#e8742a'], 'carp', 'fan', 'patches', 5, 'day', 'maple'],
  ['embersturgeon', 'Ember Sturgeon', 3, [10, 24], [420, 560], 0.82, 0.15, ['#5a2a1a', '#d8784a', '#ff9a3a'], 'cat', 'round', 'glow', 1.4, 'dusk', 'maple'],
  ['twilighteel', 'Twilight Eel', 3, [1, 3], [400, 540], 0.84, 0.5, ['#3a2a6a', '#9a7ad8', '#d8a8ff'], 'eel', 'round', 'glow', 1.2, 'night', 'maple'],
  ['sovereign', 'Autumn Sovereign', 4, [12, 28], [1200, 1600], 0.92, 0.4, ['#c8302a', '#ffd27a', '#f4a62a'], 'dragon', 'fan', 'scales', 0.35, null, 'maple'],
  ['phoenix', 'Phoenix Koi', 5, [4, 9], [4000, 5500], 0.97, 0.6, ['#ff5a2a', '#ffe27a', '#ffb02a'], 'dragon', 'fan', 'glow+scales', 0.05, null, 'maple'],

  ['keeper', 'The Pond Keeper', 6, [30, 70], [10000, 15000], 0.9, 0.5, ['#121216', '#f4f4f0', '#ffffff'], 'dragon', 'fan', 'glow+scales', 0, 'secret'],
];
export const SPECIES = RAW.map(([id, name, rarity, w, val, diff, dart, c, shape, tail, pat, weight, hint, area]) => ({ id, name, rarity, w, val, diff, dart, c, shape, tail, pat, weight, hint, area: area || 'pond' }));
export const AREAS = { pond: { id: 'pond', name: 'Still Water', lvl: 1, shop: 'Tackle Shop' }, maple: { id: 'maple', name: 'Maple Hollow', lvl: 10, shop: 'Maple Hollow Outfitters' } };
export const SPECIES_BY_ID = Object.fromEntries(SPECIES.map((s) => [s.id, s]));

export const MUTATIONS = {
  normal: { label: '', p: 0, val: 1, kg: 1 },
  shiny: { label: 'Shiny', p: 0.06, val: 2.2, kg: 1, color: '#ffe27a' },
  albino: { label: 'Albino', p: 0.025, val: 3, kg: 1, color: '#f2a6b4' },
  giant: { label: 'Giant', p: 0.018, val: 1.7, kg: 1.8, color: '#7fd36b' },
};

export const RODS = [
  { id: 'bamboo', name: 'Bamboo Rod', cost: 0, lvl: 1, ctrl: 0, speed: 0, luck: 0, prog: 0, color: '#9a7a44', note: 'Light, simple and reliable.' },
  { id: 'willow', name: 'Willow Rod', cost: 150, lvl: 2, ctrl: 0.03, speed: 0.08, luck: 0.05, prog: 0.05, color: '#6f9a4a', note: 'Springy. Bites come a little sooner.' },
  { id: 'carbon', name: 'Carbon Rod', cost: 500, lvl: 4, ctrl: 0.06, speed: 0.15, luck: 0.12, prog: 0.12, color: '#3a4048', note: 'Stiff and precise. A wider catch zone.' },
  { id: 'moonwood', name: 'Moonwood Rod', cost: 1600, lvl: 7, ctrl: 0.09, speed: 0.2, luck: 0.3, prog: 0.2, color: '#9fb7ff', note: 'Carved at night. Attracts rarer fish.', glow: true },
  { id: 'koi', name: 'Koi Spirit Rod', cost: 4500, lvl: 10, ctrl: 0.12, speed: 0.26, luck: 0.5, prog: 0.3, color: '#ff7a3a', note: 'Warm to the touch. Fish want to be caught.', glow: true },
  { id: 'dragon', name: 'Jade Dragon Rod', cost: 12000, lvl: 14, ctrl: 0.16, speed: 0.32, luck: 0.8, prog: 0.4, color: '#2fd6a1', note: 'The finest rod on the pond.', glow: true },
  // sold at Maple Hollow
  { id: 'maple', name: 'Maple Rod', cost: 8000, lvl: 10, ctrl: 0.14, speed: 0.3, luck: 0.7, prog: 0.36, color: '#c8502a', note: 'Red maple, light and lively.', area: 'maple' },
  { id: 'emberoak', name: 'Ember Oak Rod', cost: 18000, lvl: 12, ctrl: 0.17, speed: 0.34, luck: 0.9, prog: 0.44, color: '#ff7a2a', note: 'Charred oak that still glows inside.', glow: true, area: 'maple' },
  { id: 'aurora', name: 'Aurora Rod', cost: 32000, lvl: 15, ctrl: 0.19, speed: 0.38, luck: 1.1, prog: 0.5, color: '#5fd6c0', note: 'Shimmers like the northern lights.', glow: true, area: 'maple' },
  { id: 'celestial', name: 'Celestial Rod', cost: 55000, lvl: 18, ctrl: 0.22, speed: 0.42, luck: 1.35, prog: 0.58, color: '#3a4ad8', note: 'A night sky you can hold.', glow: true, area: 'maple' },
  { id: 'leviathan', name: 'Leviathan Rod', cost: 90000, lvl: 22, ctrl: 0.25, speed: 0.46, luck: 1.6, prog: 0.66, color: '#1f8a7a', note: 'Scaled like the old sea beasts.', glow: true, area: 'maple' },
];
export const BAITS = [
  { id: 'worm', name: 'Garden Worm', pack: 10, cost: 25, wait: 0.85, luck: 0, note: 'Bites come 15% sooner.' },
  { id: 'cricket', name: 'Field Cricket', pack: 10, cost: 60, wait: 0.8, luck: 0.12, note: 'Sooner bites and a little luck.' },
  { id: 'glow', name: 'Glow Grub', pack: 5, cost: 120, wait: 0.9, luck: 0, boost: 'time', note: 'Dusk and night fish are far more likely.' },
  { id: 'golden', name: 'Golden Lure', pack: 3, cost: 400, wait: 1, luck: 0.6, note: 'Strongly favours rare fish.' },
  { id: 'chum', name: 'Chum Bucket', pack: 5, cost: 90, wait: 0.5, luck: 0, note: 'Fish arrive in half the time.' },
];
export const FLOATS = [
  { id: 'red', name: 'Classic Red', cost: 0, c1: '#ff4630', c2: '#ffffff' },
  { id: 'azure', name: 'Azure', cost: 60, c1: '#3d8bff', c2: '#e8f1ff' },
  { id: 'lime', name: 'Lime Twist', cost: 80, c1: '#8fd13a', c2: '#fffbd0' },
  { id: 'gold', name: 'Gilded', cost: 300, c1: '#ffc02a', c2: '#fff0b8', glow: 0.5 },
  { id: 'neon', name: 'Neon Bloom', cost: 500, c1: '#ff4fa8', c2: '#ffd6ee', glow: 0.9 },
  { id: 'lantern', name: 'Lantern Float', cost: 900, c1: '#ffa94a', c2: '#fff1d2', glow: 1.4 },
];
export const BAGS = [{ cap: 20, cost: 0 }, { cap: 35, cost: 200 }, { cap: 55, cost: 700 }, { cap: 80, cost: 2000 }];

const KEY = 'stillwater.fishing.v2', OLD = 'stillwater.fishing.v1';
const blank = () => ({ v: 2, coins: 0, xp: 0, bag: [], bagLv: 0, rods: ['bamboo'], rod: 'bamboo', floats: ['red'], float: 'red', baits: {}, bait: null,
  caught: {}, stats: { casts: 0, fish: 0, earned: 0 }, tutorial: false, quality: 'auto', weather: 'auto', volume: 0.9, dayLen: '12', paused: false, clock: 0.64, sens: 1, area: 'pond' });

function load() {
  let s = blank();
  try {
    const raw = localStorage.getItem(KEY);
    if (raw) s = { ...s, ...JSON.parse(raw) };
    else {
      const o = JSON.parse(localStorage.getItem(OLD) || 'null');
      if (o) { s.coins = o.coins || 0; s.caught = o.caught || {}; s.tutorial = !!o.tutorial; s.rods = RODS.slice(0, Math.min(o.rod || 1, 3)).map((r) => r.id); s.rod = s.rods[s.rods.length - 1]; }
    }
  } catch (e) { /* storage unavailable */ }
  s.stats = { casts: 0, fish: 0, earned: 0, ...(s.stats || {}) };
  return s;
}

export const state = load();
const listeners = new Set();
let saveTimer = 0;
export function onChange(fn) { listeners.add(fn); return () => listeners.delete(fn); }
export function commit() {
  listeners.forEach((fn) => fn());
  clearTimeout(saveTimer);
  saveTimer = setTimeout(() => { try { localStorage.setItem(KEY, JSON.stringify(state)); } catch (e) { /* ignore */ } }, 250);
}
export function saveQuiet() { try { localStorage.setItem(KEY, JSON.stringify(state)); } catch (e) { /* ignore */ } }
export function resetAll() { Object.keys(state).forEach((k) => delete state[k]); Object.assign(state, blank()); try { localStorage.removeItem(KEY); localStorage.removeItem(OLD); } catch (e) { /* ignore */ } commit(); }

/* ---------------------------- derived values ---------------------------- */
export const level = () => Math.floor(Math.sqrt(state.xp / 70)) + 1;
export const levelProgress = () => { const l = level(), a = 70 * (l - 1) ** 2, b = 70 * l ** 2; return { lv: l, frac: (state.xp - a) / (b - a), into: state.xp - a, need: b - a }; };
export const rod = () => RODS.find((r) => r.id === state.rod) || RODS[0];
export const bait = () => (state.bait && (state.baits[state.bait] || 0) > 0 ? BAITS.find((b) => b.id === state.bait) : null);
export const floatDef = () => FLOATS.find((f) => f.id === state.float) || FLOATS[0];
export const bagCap = () => BAGS[state.bagLv].cap;
export const luck = (b = bait()) => rod().luck + (level() - 1) * 0.012 + (b ? b.luck : 0);

export function addXp(n) {
  const before = level(); state.xp += n; const after = level();
  const gained = [];
  for (let l = before + 1; l <= after; l++) { state.coins += 10 * l; gained.push(l); }
  commit();
  return gained;
}
export function addCoins(n) { state.coins += n; if (n > 0) state.stats.earned += n; commit(); }

export function recordCatch(sp, kg, mut, value) {
  const rec = state.caught[sp.id] || { n: 0, best: 0, mut: {} };
  rec.n += 1; rec.best = Math.max(rec.best, kg);
  if (mut !== 'normal') rec.mut[mut] = (rec.mut[mut] || 0) + 1;
  state.caught[sp.id] = rec; state.stats.fish += 1;
  const fresh = rec.n === 1;
  let stored = true, sold = 0;
  if (state.bag.length < bagCap()) state.bag.push({ id: sp.id, kg, mut, val: value });
  else { stored = false; sold = Math.round(value * 0.6); addCoins(sold); }
  commit();
  return { fresh, stored, sold };
}

/* ------------------------------- shopping ------------------------------- */
export function sellFish(i) { const f = state.bag[i]; if (!f) return 0; state.bag.splice(i, 1); addCoins(f.val); return f.val; }
export function sellAll() { const t = state.bag.reduce((a, f) => a + f.val, 0); state.bag = []; addCoins(t); return t; }
export function canBuyRod(r) { return !state.rods.includes(r.id) && state.coins >= r.cost && level() >= r.lvl && (r.area || 'pond') === (state.area || 'pond'); }
export function buyRod(r) { if (!canBuyRod(r)) return false; state.coins -= r.cost; state.rods.push(r.id); state.rod = r.id; commit(); return true; }
export function equipRod(id) { if (state.rods.includes(id)) { state.rod = id; commit(); } }
export function buyBait(b) { if (state.coins < b.cost) return false; state.coins -= b.cost; state.baits[b.id] = (state.baits[b.id] || 0) + b.pack; if (!state.bait) state.bait = b.id; commit(); return true; }
export function equipBait(id) { state.bait = state.bait === id ? null : id; commit(); }
export function useBait() { if (state.bait && state.baits[state.bait] > 0) { state.baits[state.bait] -= 1; if (state.baits[state.bait] <= 0) state.bait = null; } }
export function buyFloat(f) { if (state.floats.includes(f.id) || state.coins < f.cost) return false; state.coins -= f.cost; state.floats.push(f.id); state.float = f.id; commit(); return true; }
export function equipFloat(id) { if (state.floats.includes(id)) { state.float = id; commit(); } }
export function upgradeBag() { const n = BAGS[state.bagLv + 1]; if (!n || state.coins < n.cost) return false; state.coins -= n.cost; state.bagLv += 1; commit(); return true; }
