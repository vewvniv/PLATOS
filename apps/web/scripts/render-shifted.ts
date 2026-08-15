import { readFile, writeFile, mkdir } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { renderLayoutMap } from '../src/renderer.js';
import type { LayoutMap } from '../src/layoutMap.js';

/**
 * Gera um PDF com deslocamento deliberado, para provar que o comparador de paridade e o medidor
 * de fidelidade continuam capazes de falhar.
 *
 * Uma verificacao que nunca falha e pior que nenhuma, e este projeto ja produziu duas: uma janela
 * de medicao que englobava o elemento vizinho e diluia o desvio, e um `NaN` que passava em
 * qualquer tolerancia porque `NaN > x` e falso. Rodar isto depois de mexer nas ferramentas custa
 * segundos.
 *
 *   npx tsx scripts/render-shifted.ts
 *   node ../../tools/parity/compare.mjs ../../build/parity/web.pdf \
 *        ../../build/parity/shifted.pdf ../../fixtures/prova-referencia.layout.json   # deve sair 1
 */
const SHIFT_UM = 500; // 0,5 mm: acima da tolerancia de 0,3 mm da paridade

const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), '../../..');
const map: LayoutMap = JSON.parse(
  await readFile(resolve(repoRoot, 'fixtures/prova-referencia.layout.json'), 'utf8'),
);

let bubbles = 0;
let markers = 0;
for (const page of map.pages) {
  for (const primitive of page.primitives) {
    if (primitive.type === 'circle' && bubbles < 1) {
      primitive.center_x += SHIFT_UM;
      bubbles += 1;
    }
    if (primitive.type === 'aruco' && markers < 1) {
      primitive.y += SHIFT_UM;
      markers += 1;
    }
  }
}

const font = new Uint8Array(
  await readFile(resolve(repoRoot, 'packages/domain/fonts/SourceSerif4-Regular.ttf')),
);
const output = resolve(repoRoot, 'build/parity/shifted.pdf');
await mkdir(dirname(output), { recursive: true });
await writeFile(output, await renderLayoutMap(map, font));

console.log(
  `PDF deslocado: ${output} — ${bubbles} bolha e ${markers} marcador movidos ${SHIFT_UM / 1000} mm`,
);
