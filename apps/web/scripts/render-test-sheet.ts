import { readFile, writeFile, mkdir } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { renderLayoutMap } from '../src/renderer.js';
import type { LayoutMap } from '../src/layoutMap.js';
import { loadFontBytes, repoRoot } from './formulaAssets.js';

/**
 * Desenha a folha de teste de impressao (D-2b.7).
 *
 * Mesmo renderizador da prova, mesmas primitivas, mesma fonte embarcada. O mapa vem do artefato
 * versionado que o KMP calcula — nada aqui decide geometria.
 *
 *   npx tsx scripts/render-test-sheet.ts
 */
const mapPath = resolve(repoRoot, 'fixtures/folha-de-teste.layout.json');
const map: LayoutMap = JSON.parse(await readFile(mapPath, 'utf8'));

const output = resolve(repoRoot, 'build/parity/teste-web.pdf');
await mkdir(dirname(output), { recursive: true });
// Sem rasters: a folha de teste nao tem formula, e passar um mapa vazio afirma que ela nao
// depende de recurso externo nenhum.
await writeFile(output, await renderLayoutMap(map, await loadFontBytes(), new Map()));

console.log(
  `Folha de teste: ${output} (${map.pages.length} pagina, ` +
    `${map.pages[0].primitives.length} primitivas)`,
);
