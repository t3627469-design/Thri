// Hand-drawn icon set (24x24, stroke based). Everything here is drawn for this game.
const P = {
  rod: '<path d="M3.5 21 17.5 6.2"/><path d="M17.5 6.2C21 5.5 22 8 21.4 11"/><circle cx="21.2" cy="13.6" r="1.7" fill="currentColor" stroke="none"/><path d="M5.6 18.6l1.7 1.7"/>',
  book: '<path d="M5.5 4.5h9.5a3 3 0 0 1 3 3V20H8.5a3 3 0 0 1-3-3z"/><path d="M8.5 4.5V17"/><path d="M11 8.5h4M11 11.5h3"/>',
  shop: '<path d="M3 9.5 5 4.5h14l2 5"/><path d="M3 9.5c1.1 2.2 3.3 2.2 4.5 0 1.2 2.2 3.3 2.2 4.5 0 1.2 2.2 3.3 2.2 4.5 0 1.2 2.2 3.4 2.2 4.5 0"/><path d="M5 13v7h14v-7"/><path d="M10 20v-4.5h4V20"/>',
  coin: '<circle cx="12" cy="12" r="8.5"/><path d="M9.3 9.6c0-1.2 1.2-2 2.7-2s2.7.8 2.7 2c0 2.7-5.4 1.2-5.4 4.2 0 1.2 1.2 2 2.7 2s2.7-.8 2.7-2"/><path d="M12 6.2v1.4M12 15.8v2"/>',
  bag: '<path d="M6 8.5h12l1.1 11.5H4.9z"/><path d="M9 8.5V7a3 3 0 0 1 6 0v1.5"/><path d="M9.5 13.2c.8 1 4.2 1 5 0"/>',
  hook: '<path d="M14.5 3v11a4.5 4.5 0 0 1-9 0v-1.5"/><path d="M3.4 11.2l2.1 2.2 2.1-2.2"/><path d="M12.5 3h4"/>',
  worm: '<path d="M3 14.5c1.6-4.2 3.6 2 5.6-1.5s3.8 1.8 5.6-1.2 3.2 1.4 6.8-1.3"/><circle cx="19.6" cy="9.6" r="1" fill="currentColor" stroke="none"/>',
  float: '<path d="M12 2.5v4"/><path d="M7.2 12a4.8 4.8 0 0 1 9.6 0z" fill="currentColor" fill-opacity=".35"/><path d="M7.2 12a4.8 4.8 0 0 0 9.6 0"/><path d="M12 16.8v4.7"/>',
  gear: '<circle cx="12" cy="12" r="3.4"/><path d="M12 2.8v3M12 18.2v3M2.8 12h3M18.2 12h3M5.5 5.5l2.1 2.1M16.4 16.4l2.1 2.1M5.5 18.5l2.1-2.1M16.4 7.6l2.1-2.1"/>',
  help: '<circle cx="12" cy="12" r="8.5"/><path d="M9.4 9.6a2.7 2.7 0 1 1 3.9 2.4c-.9.5-1.3 1-1.3 1.9"/><path d="M12 17.1v.1"/>',
  camera: '<path d="M3.5 8.5h3.2l1.6-2.3h7.4l1.6 2.3h3.2v10.5h-17z"/><circle cx="12" cy="13.6" r="3.3"/>',
  expand: '<path d="M4 9V4h5M20 9V4h-5M4 15v5h5M20 15v5h-5"/>',
  orbit: '<circle cx="12" cy="12" r="2.2" fill="currentColor" stroke="none"/><path d="M3 12c0-3 4-5.2 9-5.2s9 2.2 9 5.2-4 5.2-9 5.2S3 15 3 12z"/><path d="M8 5.5c2-1 4.6-.4 7.4 1.4" opacity=".0"/>',
  soundOn: '<path d="M4 9.5h3.2L12 5.5v13l-4.8-4H4z"/><path d="M15.2 9.2a4 4 0 0 1 0 5.6M17.8 6.8a7.4 7.4 0 0 1 0 10.4"/>',
  soundOff: '<path d="M4 9.5h3.2L12 5.5v13l-4.8-4H4z"/><path d="M16 9.5l5 5M21 9.5l-5 5"/>',
  sun: '<circle cx="12" cy="12" r="4"/><path d="M12 2.5v3M12 18.5v3M2.5 12h3M18.5 12h3M5.3 5.3l2.1 2.1M16.6 16.6l2.1 2.1M5.3 18.7l2.1-2.1M16.6 7.4l2.1-2.1"/>',
  sunset: '<path d="M6.5 16.5a5.5 5.5 0 0 1 11 0"/><path d="M2.5 16.5h19M5 20h14"/><path d="M12 5v3M4.6 9l2 1.8M19.4 9l-2 1.8"/>',
  dusk: '<path d="M6.5 17a5.5 5.5 0 0 1 11 0"/><path d="M2.5 17h19"/><path d="M15.5 4.5a3.4 3.4 0 1 0 3.6 4.6 4 4 0 0 1-3.6-4.6z" fill="currentColor" fill-opacity=".3"/>',
  moon: '<path d="M19 14.2A7.6 7.6 0 0 1 9.8 5a7.6 7.6 0 1 0 9.2 9.2z"/><path d="M16.5 4.5v2.2M15.4 5.6h2.2"/>',
  cycle: '<path d="M19.8 11A8 8 0 0 0 6 6.6M4 3.5v4h4"/><path d="M4.2 13A8 8 0 0 0 18 17.4M20 20.5v-4h-4"/>',
  close: '<path d="M6 6l12 12M18 6 6 18"/>',
  lock: '<rect x="5.5" y="10.5" width="13" height="9.5" rx="2"/><path d="M8.5 10.5V8a3.5 3.5 0 0 1 7 0v2.5"/>',
  star: '<path d="M12 3.5l2.5 5.3 5.8.7-4.3 4 1.2 5.7L12 16.3 6.8 19.2 8 13.5l-4.3-4 5.8-.7z"/>',
  rain: '<path d="M7 14.5a4 4 0 0 1-.4-8 5.4 5.4 0 0 1 10.4 1.4 3.3 3.3 0 0 1 .4 6.6z"/><path d="M8.5 17.5l-1 2.5M12.5 17.5l-1 2.5M16.5 17.5l-1 2.5"/>',
  cloud: '<path d="M7 18a4 4 0 0 1-.4-8 5.4 5.4 0 0 1 10.4 1.4 3.3 3.3 0 0 1 .4 6.6z"/>',
  check: '<path d="M5 12.8l4.2 4.2L19 7.2"/>',
  hand: '<path d="M9.5 12V5.6a1.6 1.6 0 0 1 3.2 0V11"/><path d="M12.7 10.4a1.6 1.6 0 0 1 3.2 0V12"/><path d="M15.9 11.4a1.5 1.5 0 0 1 3.1 0v4.4a5.4 5.4 0 0 1-5.4 5.4h-1.2a5.4 5.4 0 0 1-4.3-2.2l-2.9-3.9a1.4 1.4 0 0 1 2.2-1.6L9.5 15"/>',
  fish: '<path d="M2.8 12c3-4.2 8.5-5.4 12.4-2.6L21 6.8v10.4l-5.8-2.6C11.3 17.4 5.8 16.2 2.8 12z"/><circle cx="7.4" cy="11" r=".9" fill="currentColor" stroke="none"/>',
  down: '<path d="M6 9.5l6 6 6-6"/>',
  up: '<path d="M6 14.5l6-6 6 6"/>',
  lily: '<path d="M12 20.5c-4.2 0-8.2-2-9.2-6.2 3.2 0 6.4 1.2 9.2 6.2z"/><path d="M12 20.5c4.2 0 8.2-2 9.2-6.2-3.2 0-6.4 1.2-9.2 6.2z"/><path d="M12 19.4c-3.2-2.2-4.2-6.4 0-12.6 4.2 6.2 3.2 10.4 0 12.6z"/><path d="M6.2 11.8c1.4-.2 2.7.2 3.7 1.2M17.8 11.8c-1.4-.2-2.7.2-3.7 1.2"/>',
  sparkle: '<path d="M12 3l1.8 5.2L19 10l-5.2 1.8L12 17l-1.8-5.2L5 10l5.2-1.8z" fill="currentColor" fill-opacity=".25"/><path d="M19 15.5l.7 2 2 .7-2 .7-.7 2-.7-2-2-.7 2-.7z"/>',
};

export function icon(name, size = 20, extra = '') {
  const body = P[name] || P.fish;
  return `<svg class="ic ${extra}" viewBox="0 0 24 24" width="${size}" height="${size}" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${body}</svg>`;
}

/* ---- little illustrations for shop items (own artwork, 64x64) ---- */
export function rodArt(color = '#8a6a3a', glow = false) {
  const g = glow ? `<filter id="gl"><feGaussianBlur stdDeviation="1.6"/></filter><path d="M10 56 54 12" stroke="${color}" stroke-width="5" opacity=".55" filter="url(#gl)"/>` : '';
  return `<svg viewBox="0 0 64 64" aria-hidden="true">${g}
    <path d="M10 56 53 13" stroke="#2a1f16" stroke-width="3.6" stroke-linecap="round"/>
    <path d="M10 56 53 13" stroke="${color}" stroke-width="2.2" stroke-linecap="round"/>
    <path d="M10 56 22 44" stroke="#c9a36a" stroke-width="5.2" stroke-linecap="round"/>
    <circle cx="25" cy="42" r="4.4" fill="#aab2bb" stroke="#4a5058" stroke-width="1.2"/>
    <path d="M53 13c6-1.4 8.6 2.4 7.6 9" fill="none" stroke="#e9eef2" stroke-width="1"/>
    <circle cx="60.4" cy="25" r="2.4" fill="#ff4630"/>
  </svg>`;
}
export function floatArt(c1 = '#ff4630', c2 = '#fff', glow = false) {
  return `<svg viewBox="0 0 64 64" aria-hidden="true">
    ${glow ? `<circle cx="32" cy="34" r="19" fill="${c1}" opacity=".25"/>` : ''}
    <path d="M32 6v10" stroke="${c1}" stroke-width="2.4" stroke-linecap="round"/>
    <path d="M17 34a15 15 0 0 1 30 0z" fill="${c1}"/>
    <path d="M17 34a15 15 0 0 0 30 0z" fill="${c2}"/>
    <path d="M22 28a11 11 0 0 1 8-6" stroke="#fff" stroke-opacity=".55" stroke-width="2.4" fill="none" stroke-linecap="round"/>
    <path d="M32 49v10" stroke="#9aa6b0" stroke-width="2"/>
  </svg>`;
}
export function baitArt(id) {
  const a = {
    worm: '<path d="M8 40c6-14 11 6 17-4s11 6 17-3 9 4 14-4" stroke="#d98a8a" stroke-width="7" fill="none" stroke-linecap="round"/><path d="M8 40c6-14 11 6 17-4s11 6 17-3 9 4 14-4" stroke="#f0b8b0" stroke-width="2" fill="none" stroke-linecap="round" stroke-dasharray="1 6"/><circle cx="54" cy="29" r="1.6" fill="#2a1f16"/>',
    cricket: '<ellipse cx="30" cy="36" rx="16" ry="7" fill="#6a8a3a"/><circle cx="48" cy="32" r="5" fill="#5a7a2e"/><path d="M20 40l-6 10M28 42l-4 11M36 42l2 11M16 32 8 22M44 28l-6-9M50 28l6-8" stroke="#3a5a1e" stroke-width="2.2" fill="none" stroke-linecap="round"/>',
    glow: '<circle cx="32" cy="34" r="20" fill="#c8ff7a" opacity=".22"/><circle cx="32" cy="34" r="12" fill="#c8ff7a" opacity=".4"/><ellipse cx="32" cy="34" rx="9" ry="6" fill="#e9ffb0"/><path d="M23 34h18" stroke="#8fd13a" stroke-width="1.6"/>',
    golden: '<path d="M32 8 40 26l-8 6-8-6z" fill="#ffd45a"/><path d="M32 32 40 26l6 18c-4 8-8 11-14 11s-10-3-14-11l6-18z" fill="#f0b02a"/><path d="M32 8v47" stroke="#fff" stroke-opacity=".5"/><path d="M32 55v6" stroke="#c9b27a" stroke-width="2"/>',
    chum: '<path d="M12 22h40l-4 32H16z" fill="#7a8a96"/><path d="M12 22h40" stroke="#4a5560" stroke-width="3"/><path d="M18 22c0-8 28-8 28 0" fill="none" stroke="#4a5560" stroke-width="3"/><circle cx="26" cy="12" r="4" fill="#d98a8a"/><circle cx="38" cy="9" r="3.4" fill="#b8c4c9"/><circle cx="33" cy="15" r="3" fill="#e8a24a"/>',
  };
  return `<svg viewBox="0 0 64 64" aria-hidden="true">${a[id] || a.worm}</svg>`;
}
export function bagArt(level = 0) {
  return `<svg viewBox="0 0 64 64" aria-hidden="true"><path d="M14 22h36l3 34H11z" fill="${['#8a6a3a', '#5a7a8c', '#6a5aa8', '#a8782a'][level] || '#8a6a3a'}"/><path d="M14 22h36" stroke="#2a1f16" stroke-width="3"/><path d="M22 22v-3a10 10 0 0 1 20 0v3" fill="none" stroke="#2a1f16" stroke-width="3.2"/><rect x="26" y="34" width="12" height="9" rx="2" fill="#e9d9a8"/><path d="M18 30l-1 22M46 30l1 22" stroke="#fff" stroke-opacity=".18" stroke-width="2"/></svg>`;
}
