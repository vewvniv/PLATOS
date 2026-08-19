import { readFile, writeFile, mkdir } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { renderLayoutMap } from '../src/renderer.js';
import type { LayoutMap } from '../src/layoutMap.js';
import { loadFontBytes, loadFormulaRasters, repoRoot } from './formulaAssets.js';

/**
 * Passo reproduzivel de build: desenha o `LayoutMap` golden e grava o PDF do lado web.
 *
 * E este arquivo que o job de paridade compara com o PDF gerado no Android. Le o golden
 * versionado, e nao um mapa recalculado aqui: o renderizador nunca calcula geometria.
 */
const goldenPath = resolve(repoRoot, 'fixtures/prova-referencia.layout.json');
const outputPath = process.argv[2]
  ? resolve(process.argv[2])
  : resolve(repoRoot, 'build/parity/web.pdf');

const map: LayoutMap = JSON.parse(await readFile(goldenPath, 'utf8'));
const rasters = await loadFormulaRasters();

const pdf = await renderLayoutMap(map, await loadFontBytes(), rasters);
await mkdir(dirname(outputPath), { recursive: true });
await writeFile(outputPath, pdf);

const imagens = map.pages.flatMap((page) =>
  page.primitives.filter((primitive) => primitive.type === 'image'),
);
console.log(
  `PDF do web: ${outputPath} (${pdf.length} bytes, ${map.pages.length} paginas, ` +
    `${imagens.length} formulas, prova ${map.exam_id})`,
);
