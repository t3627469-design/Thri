# Still Water 🪷

A peaceful 3D pond — **modelled in Blender**, shown live in the browser.

![Blender (Cycles) render](preview.png)

Open **`index.html`** in any modern browser (single file, works offline).

## What's in it
- Sculpted terrain with an irregular pond, rolling hills and a tree-lined horizon
- ~28,000 individually modelled grass blades, reeds & cattails, wildflowers
- Lily pads, lotus blossoms, mossy rocks, a wooden dock, a stone lantern
- Mallard ducks with ducklings, koi under the surface, butterflies
- Water with real planar reflections, caustics, shoreline foam, ripples (click it!)
- Sky with clouds/stars, mist, falling cherry blossom petals, fireflies, bloom
- Four times of day (Golden hour · Day · Twilight · Moonlight, keys `1`–`4`) and soft generated ambience (wind, water, birds, crickets, gentle chimes)

## Fishing game
Cast from the dock, wait for the bobber to dip, hook it, then win the catch bar at the bottom of the screen.
- **Space** or click the water: cast · **Space** at the bite: hook it · **hold** Space / mouse / touch: move the zone right, release: it falls back left
- Keep the zone over the fish to fill the bar; if it drains to zero the fish escapes
- 9 species from Common to Legendary; Moonlit Pike bites at night, Ember Carp at dusk
- Coins buy rod upgrades (wider zone, shorter wait). **J** opens the journal. Progress is saved in your browser.

## How it's made
| file | what |
| --- | --- |
| `build_pond.py` | Blender (`bpy`) script that builds the whole scene, saves `pond.blend`, exports `pond.glb`, renders `preview.png` |
| `pond.blend` | the Blender scene |
| `pond.glb` | exported scene (geometry + vertex colours) |
| `src/fishing.js` | the fishing game (rod, bobber, line, catch bar, journal) |
| `src/main.js`, `src/template.html` | three.js viewer: water, sky, wind, light, creatures, sound |
| `build.mjs` | bundles the viewer and inlines the gzipped GLB into `index.html` |

Rebuild:
```bash
pip install bpy numpy          # Blender as a Python module (Python 3.11)
python3 build_pond.py          # add --no-render to skip the Cycles preview
npm install && npm run build   # writes index.html
```

Controls: drag to orbit · scroll to zoom · click water for a ripple + note · `H` hides the UI.
Add `?lite` to the URL to skip post-processing on slower machines.
