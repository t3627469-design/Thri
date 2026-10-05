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

## How it's made
| file | what |
| --- | --- |
| `build_pond.py` | Blender (`bpy`) script that builds the whole scene, saves `pond.blend`, exports `pond.glb`, renders `preview.png` |
| `pond.blend` | the Blender scene |
| `pond.glb` | exported scene (geometry + vertex colours) |
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
