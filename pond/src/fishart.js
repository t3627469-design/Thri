// Procedural fish illustrations: every species is drawn from its own parameters (shape, tail, pattern, colours).
let uid = 0;
const SHAPES = {
  slim:  { x0: 10, x1: 94, D: 9.5, tw: 2.4, fin: 0.9, snout: 0 },
  trout: { x0: 8, x1: 96, D: 12, tw: 2.6, fin: 0.9, snout: 0 },
  deep:  { x0: 12, x1: 92, D: 21, tw: 3.2, fin: 1.4, snout: 0 },
  carp:  { x0: 8, x1: 94, D: 16, tw: 3, fin: 1.1, snout: 0, barbels: true },
  pike:  { x0: 4, x1: 98, D: 8.5, tw: 2.4, fin: 0.7, snout: 8 },
  cat:   { x0: 6, x1: 94, D: 14, tw: 3, fin: 0.8, snout: 0, barbels: true, flatHead: true },
  dragon:{ x0: 6, x1: 96, D: 13, tw: 2.6, fin: 1.2, snout: 0, whiskers: true, crest: true },
  eel:   { eel: true },
};
function rng(seed) { let s = 0; for (const ch of seed) s = (s * 31 + ch.charCodeAt(0)) >>> 0; return () => ((s = (s * 1664525 + 1013904223) >>> 0) / 4294967296); }
const shade = (hex, k) => { const n = parseInt(hex.slice(1), 16), f = (v) => Math.max(0, Math.min(255, Math.round(v * k))); return `rgb(${f(n >> 16)},${f((n >> 8) & 255)},${f(n & 255)})`; };

export function fishArt(sp, opts = {}) {
  const id = 'f' + (++uid), unknown = !!opts.unknown, mut = opts.mut || 'normal';
  let [c1, c2, c3] = sp.c;
  if (mut === 'albino') { c1 = '#f7ece4'; c2 = '#ffffff'; c3 = '#f2a6b4'; }
  if (unknown) { c1 = c2 = c3 = '#0a1210'; }
  const S = SHAPES[sp.shape] || SHAPES.trout, r = rng(sp.id);
  const pats = (sp.pat || 'plain').split('+');
  let body, tailX, cy = 30, eyeX, eyeY, headX, dorsA, dorsB, topY, botY;
  if (S.eel) {
    body = 'M6 33C20 18 36 46 56 31S88 18 104 29L106 33C90 25 70 40 56 37S22 52 6 37Z';
    tailX = 104; eyeX = 12; eyeY = 32; headX = 6; dorsA = 30; dorsB = 90; topY = 20; botY = 46;
  } else {
    const { x0, x1, D, tw, snout } = S, a = x0 - snout;
    body = `M${a} 30C${x0 + 14} ${30 - D * 1.15} ${x1 - 28} ${30 - D * 1.1} ${x1} ${30 - tw}L${x1} ${30 + tw}C${x1 - 28} ${30 + D * 1.05} ${x0 + 14} ${30 + D * 1.1} ${a} 30Z`;
    tailX = x1; eyeX = x0 + (x1 - x0) * 0.13 - snout * 0.2; eyeY = 30 - D * 0.22; headX = a; topY = 30 - D; botY = 30 + D;
    dorsA = x0 + (x1 - x0) * 0.36; dorsB = x0 + (x1 - x0) * 0.7;
  }
  const D = S.D || 9, fin = S.fin || 1, tailKind = sp.tail || 'fork';
  const tail = tailKind === 'fan'
    ? `M${tailX - 2} 28C${tailX + 12} 10 ${tailX + 26} 16 ${tailX + 22} 30C${tailX + 26} 44 ${tailX + 12} 50 ${tailX - 2} 32Z`
    : tailKind === 'round'
      ? `M${tailX - 2} 28C${tailX + 10} 20 ${tailX + 18} 24 ${tailX + 16} 30C${tailX + 18} 36 ${tailX + 10} 40 ${tailX - 2} 32Z`
      : `M${tailX - 2} 28L${tailX + 17} ${30 - D * 0.85}C${tailX + 11} 30 ${tailX + 11} 30 ${tailX + 17} ${30 + D * 0.85}L${tailX - 2} 32Z`;
  const dfh = D * 0.7 * fin;
  const dorsal = `M${dorsA} ${topY + 2}C${dorsA + 8} ${topY - dfh} ${dorsB - 8} ${topY - dfh * 0.9} ${dorsB} ${topY + 3}Z`;
  const anal = `M${tailX - 26} ${botY - 2}C${tailX - 20} ${botY + dfh * 0.7} ${tailX - 12} ${botY + dfh * 0.6} ${tailX - 8} ${botY - 1}Z`;
  const pelv = `M${headX + 30} ${botY - 1}l8 ${dfh * 0.8}l6 ${-dfh * 0.2}z`;
  let pat = '';
  for (const p of pats) {
    if (p === 'stripes') for (let i = 0; i < 6; i++) pat += `<rect x="${24 + i * 11}" y="0" width="4.2" height="60" fill="${c3}" opacity=".5" transform="skewX(-8)"/>`;
    else if (p === 'bands') for (let i = 0; i < 4; i++) pat += `<rect x="${28 + i * 16}" y="0" width="7" height="60" fill="${c3}" opacity=".38" transform="skewX(-6)"/>`;
    else if (p === 'spots') for (let i = 0; i < 16; i++) pat += `<circle cx="${22 + r() * 70}" cy="${30 - D * 0.7 + r() * D * 1.2}" r="${1.1 + r() * 1.8}" fill="${c3}" opacity=".75"/>`;
    else if (p === 'lateral') pat += `<path d="M${headX + 14} 29.5 ${tailX} 29.5" stroke="${c3}" stroke-width="${D * 0.3}" opacity=".7" stroke-linecap="round"/>`;
    else if (p === 'patches') for (let i = 0; i < 5; i++) pat += `<ellipse cx="${26 + i * 13 + r() * 4}" cy="${30 - D * 0.35 + r() * D * 0.4}" rx="${6 + r() * 5}" ry="${D * (0.35 + r() * 0.25)}" fill="${i % 3 === 1 ? '#1d1d24' : c3}" opacity=".9"/>`;
    else if (p === 'scales') pat += `<g stroke="${shade(c1, 0.55)}" stroke-width=".8" fill="none" opacity=".45">${Array.from({ length: 36 }, (_, i) => { const cx = 20 + (i % 9) * 8.4 + ((i / 9 | 0) % 2) * 4.2, y = 30 - D * 0.75 + (i / 9 | 0) * D * 0.5; return `<path d="M${cx} ${y}a4.2 4.2 0 0 0 4.2 4.2"/>`; }).join('')}</g>`;
    else if (p === 'glow') pat += `<g fill="${c3}">${Array.from({ length: 12 }, () => `<circle cx="${20 + r() * 72}" cy="${30 - D * 0.6 + r() * D * 1.1}" r="${0.9 + r() * 1.2}" opacity="${0.55 + r() * 0.4}"/>`).join('')}</g>`;
  }
  const aura = !unknown && (pats.includes('glow') || mut === 'shiny') ? `<ellipse cx="58" cy="30" rx="58" ry="${D + 14}" fill="url(#${id}g)"/>` : '';
  const barb = S.barbels && !unknown ? `<path d="M${headX + 1} 33q-6 4-3 9M${headX + 1} 35q-4 6 0 10" stroke="${shade(c1, 0.7)}" stroke-width="1.3" fill="none" stroke-linecap="round"/>` : '';
  const whisk = S.whiskers && !unknown ? `<path d="M${headX + 2} 31C-2 36 0 46 8 50M${headX + 3} 29C0 22 2 14 10 12" stroke="${c3}" stroke-width="1.5" fill="none" stroke-linecap="round"/>` : '';
  const crest = S.crest && !unknown ? Array.from({ length: 9 }, (_, i) => `<path d="M${dorsA - 16 + i * 8.6} ${topY + 2}l3.2 -6 3.2 6z" fill="${c3}"/>`).join('') : '';
  const sparkle = mut === 'shiny' && !unknown ? [[20, 8], [96, 10], [60, 52], [108, 40]].map(([x, y], i) => `<path d="M${x} ${y - 5}l1.3 3.7 3.7 1.3-3.7 1.3L${x} ${y + 5}l-1.3-3.7-3.7-1.3 3.7-1.3z" fill="#ffe27a" opacity="${0.9 - i * 0.1}"/>`).join('') : '';
  const eyeCol = mut === 'albino' ? '#d8283c' : '#10161a';
  return `<svg viewBox="-2 -14 136 80" role="img" aria-label="${unknown ? 'Unknown fish' : sp.name}">
    <defs>
      <linearGradient id="${id}b" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="${c1}"/><stop offset=".62" stop-color="${shade(c1, 1.08)}"/><stop offset="1" stop-color="${c2}"/></linearGradient>
      <radialGradient id="${id}g"><stop offset="0" stop-color="${mut === 'shiny' ? '#ffe27a' : c3}" stop-opacity=".45"/><stop offset="1" stop-color="${c3}" stop-opacity="0"/></radialGradient>
      <clipPath id="${id}c"><path d="${body}"/></clipPath>
    </defs>
    ${aura}
    ${S.eel ? '' : `<path d="${dorsal}" fill="${shade(c1, 0.85)}"/><path d="${anal}" fill="${shade(c2, 0.9)}" opacity=".9"/><path d="${pelv}" fill="${shade(c2, 0.9)}" opacity=".9"/>`}
    <path d="${tail}" fill="${unknown ? c1 : shade(c3 === c1 ? c2 : c3, 0.95)}" opacity=".95"/>
    ${S.eel && !unknown ? `<path d="M30 24C50 28 70 18 100 25L100 27C70 21 50 31 30 27Z" fill="${shade(c1, 0.8)}" opacity=".7"/>` : ''}
    <path d="${body}" fill="url(#${id}b)"/>
    <g clip-path="url(#${id}c)">${unknown ? '' : pat}${unknown ? '' : `<path d="${body}" fill="none" stroke="#000" stroke-opacity=".15" stroke-width="2"/>`}</g>
    ${crest}${barb}${whisk}
    ${unknown ? '' : `<path d="M${headX + (tailX - headX) * 0.27} ${30 - D * 0.75}q-4 ${D * 0.8} 0 ${D * 1.5}" stroke="#000" stroke-opacity=".28" stroke-width="1.3" fill="none" stroke-linecap="round"/>
    <ellipse cx="${headX + (tailX - headX) * 0.4}" cy="${30 + D * 0.15}" rx="6" ry="${D * 0.32}" fill="${shade(c2, 0.95)}" opacity=".55"/>
    <circle cx="${eyeX}" cy="${eyeY}" r="${S.eel ? 1.7 : 2.7}" fill="#fff"/><circle cx="${eyeX + 0.4}" cy="${eyeY}" r="${S.eel ? 1.1 : 1.8}" fill="${eyeCol}"/><circle cx="${eyeX - 0.2}" cy="${eyeY - 0.8}" r=".6" fill="#fff"/>`}
    ${sparkle}
  </svg>`;
}
