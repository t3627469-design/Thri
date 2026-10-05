// Bundles src/main.js (three.js) and inlines the Blender export (pond.glb, gzipped) into one index.html
import { build } from 'esbuild';
import { readFileSync, writeFileSync } from 'node:fs';
import { gzipSync } from 'node:zlib';

const out = await build({
  entryPoints: ['src/main.js'], bundle: true, minify: true, write: false,
  format: 'iife', target: 'es2020', legalComments: 'none',
});
const js = out.outputFiles[0].text.replace(/<\/script/gi, '<\\/script');
const glb = gzipSync(readFileSync('pond.glb'), { level: 9 });
const html = readFileSync('src/template.html', 'utf8')
  .replace('/*GLB*/', () => glb.toString('base64'))
  .replace('/*BUNDLE*/', () => js);
writeFileSync('index.html', html);
console.log(`index.html  ${(html.length / 1048576).toFixed(1)} MB  (glb gz ${(glb.length / 1048576).toFixed(1)} MB, js ${(js.length / 1024).toFixed(0)} KB)`);
