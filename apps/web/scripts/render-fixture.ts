import { readFile, writeFile, mkdir } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { renderLayoutMap } from '../src/renderer.js';
import type { LayoutMap } from '../src/layoutMap.js';

/**
 * Passo reproduzivel de build: desenha o `LayoutMap` golden e grava o PDF do lado web.
 *
 * E este arquivo que o job de paridade compara com o PDF gerado no Android. Le o golden
 * versionado, e nao um mapa recalculado aqui: o renderizador nunca calcula geometria.
 */
const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(here, '../../..');

const goldenPath = resolve(repoRoot, 'fixtures/prova-referencia.layout.json');
const fontPath = resolve(repoRoot, 'packages/domain/fonts/SourceSerif4-Regular.ttf');
const outputPath = process.argv[2]
  ? resolve(process.argv[2])
  : resolve(repoRoot, 'build/parity/web.pdf');

const map: LayoutMap = JSON.parse(await readFile(goldenPath, 'utf8'));
const fontBytes = new Uint8Array(await readFile(fontPath));

const pdf = await renderLayoutMap(map, fontBytes);
await mkdir(dirname(outputPath), { recursive: true });
await writeFile(outputPath, pdf);

console.log(
  `PDF do web: ${outputPath} (${pdf.length} bytes, ${map.pages.length} paginas, prova ${map.exam_id})`,
);
