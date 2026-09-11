import { writeFile, mkdir } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { renderLayoutMap } from '../src/renderer.js';
import type { LayoutMap } from '../src/layoutMap.js';
import { loadFontBytes, loadFormulaRasters, repoRoot } from './formulaAssets.js';
import { loadPublishedLayout } from './examPackage.js';

/**
 * Passo reproduzivel de build: desenha o `LayoutMap` do pacote publicado e grava o PDF do web.
 *
 * E este arquivo que o job de paridade compara com o PDF gerado no Android. A geometria sai de
 * dentro do pacote versionado (D-2a.5), e nao de um mapa recalculado aqui: o renderizador nunca
 * calcula geometria — mudou de onde ela vem, e nao o que ele faz com ela.
 */

const outputPath = process.argv[2]
  ? resolve(process.argv[2])
  : resolve(repoRoot, 'build/parity/web.pdf');

// **Sem aluno pedido, desenha a folha da variante**, e e isso que a paridade compara: nenhum
// comando existente muda de comportamento. Com `PLATOS_STUDENT`, desenha a folha daquele aluno —
// mesma geometria, o QR dele. E a mesma disciplina de `PLATOS_PACKAGE`: o caminho de producao e
// parametro, e nao um segundo script que duplicaria como uma folha e desenhada.
const studentToken = process.env.PLATOS_STUDENT || undefined;

const map: LayoutMap = await loadPublishedLayout(undefined, studentToken);
const rasters = await loadFormulaRasters();

const pdf = await renderLayoutMap(map, await loadFontBytes(), rasters);
await mkdir(dirname(outputPath), { recursive: true });
await writeFile(outputPath, pdf);

const imagens = map.pages.flatMap((page) =>
  page.primitives.filter((primitive) => primitive.type === 'image'),
);
console.log(
  `PDF do web: ${outputPath} (${pdf.length} bytes, ${map.pages.length} paginas, ` +
    `${imagens.length} formulas, prova ${map.exam_id}` +
    `${studentToken ? `, aluno ${studentToken}` : ''})`,
);
