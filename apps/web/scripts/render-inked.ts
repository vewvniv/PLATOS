import { writeFile, mkdir } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { renderLayoutMap } from '../src/renderer.js';
import type { LayoutMap } from '../src/layoutMap.js';
import { loadFontBytes, loadFormulaRasters, repoRoot } from './formulaAssets.js';
import { loadPublishedLayout } from './examPackage.js';

/**
 * Gera PDFs com defeitos de tinta deliberados, para provar que `tinta.mjs` continua capaz de
 * falhar — a mesma razao de `render-shifted.ts` existir para a paridade e a fidelidade.
 *
 * Tinta e o caso em que uma verificacao decorativa seria mais perigosa que a ausencia dela: os
 * defeitos aqui **nao quebram** golden, fidelidade nem paridade. Eles chegariam ao papel, e dali ao
 * OMR da fatia 3, como acerto ou erro que ninguem marcou.
 *
 *   npx tsx scripts/render-inked.ts
 *   node ../../tools/parity/tinta.mjs ../../build/parity/tinta-faixa.pdf <layout>   # deve sair 1
 */

/** Trama que passa do teto de 8% de §7 e do orcamento de 12% de ADR-0010. */
const TRAMA_PESADA = 200;

/** Tom que faz a letra dentro do circulo competir com uma caneta. */
const LETRA_OPACA = 900;

const base: LayoutMap = await loadPublishedLayout();
const fontBytes = await loadFontBytes();
const rasters = await loadFormulaRasters();

const clone = (): LayoutMap => JSON.parse(JSON.stringify(base)) as LayoutMap;

async function emit(name: string, map: LayoutMap, what: string): Promise<void> {
  const output = resolve(repoRoot, `build/parity/${name}.pdf`);
  await mkdir(dirname(output), { recursive: true });
  await writeFile(output, await renderLayoutMap(map, fontBytes, rasters));
  console.log(`${output} — ${what}`);
}

// 1. Faixa pesada: trama chapada muito acima do teto, sob as bolhas.
const comFaixaPesada = clone();
let faixas = 0;
for (const page of comFaixaPesada.pages) {
  for (const primitive of page.primitives) {
    if (primitive.type === 'rect' && primitive.fill !== null) {
      primitive.fill = TRAMA_PESADA;
      faixas += 1;
    }
  }
}
await emit('tinta-faixa', comFaixaPesada, `${faixas} faixas a ${TRAMA_PESADA} por mil`);

// 2. Faixa ausente: o defeito que o Android tinha desde a fatia 1, quando ignorava `fill`. Ele nao
//    move centroide nenhum, entao as 185 comparacoes de posicao continuam verdes — quem o pega e a
//    comparacao de trama, e so ela.
const semFaixa = clone();
let removidas = 0;
for (const page of semFaixa.pages) {
  for (const primitive of page.primitives) {
    if (primitive.type === 'rect' && primitive.fill !== null) {
      primitive.fill = null;
      removidas += 1;
    }
  }
}
await emit('tinta-sem-faixa', semFaixa, `${removidas} faixas removidas`);

// 3. Letra opaca: a decoracao dentro da bolha vira quase resposta.
const comLetraOpaca = clone();
let letras = 0;
for (const page of comLetraOpaca.pages) {
  for (const primitive of page.primitives) {
    if (primitive.type === 'text' && primitive.tone !== null) {
      primitive.tone = LETRA_OPACA;
      letras += 1;
    }
  }
}
await emit('tinta-letra', comLetraOpaca, `${letras} letras a ${LETRA_OPACA} por mil`);

// 3. Cor: o defeito que **nenhuma** medicao em cinza pode achar, porque o rasterizador descarta a
//    informacao antes de ela ser julgada. A folha e monocromatica por §7, e impressora de escola
//    imprime o azul como um cinza qualquer.
const rastersComCor = new Map(rasters);
const primeiraFormula = base.pages
  .flatMap((page) => page.primitives)
  .find((primitive) => primitive.type === 'image');
if (primeiraFormula && primeiraFormula.type === 'image') {
  rastersComCor.set(primeiraFormula.reference, pngVermelho());
}
{
  const output = resolve(repoRoot, 'build/parity/tinta-cor.pdf');
  await mkdir(dirname(output), { recursive: true });
  await writeFile(output, await renderLayoutMap(clone(), fontBytes, rastersComCor));
  console.log(`${output} — uma formula trocada por um raster vermelho`);
}

/** PNG 8x8 vermelho puro, escrito a mao para nao depender de nada. */
function pngVermelho(): Uint8Array {
  const width = 8;
  const height = 8;
  const raw: number[] = [];
  for (let y = 0; y < height; y += 1) {
    raw.push(0); // filtro `None` na linha
    for (let x = 0; x < width; x += 1) raw.push(255, 0, 0);
  }

  const crcTable = new Int32Array(256);
  for (let n = 0; n < 256; n += 1) {
    let c = n;
    for (let k = 0; k < 8; k += 1) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    crcTable[n] = c;
  }
  const crc = (bytes: Uint8Array): number => {
    let c = 0xffffffff;
    for (const byte of bytes) c = crcTable[(c ^ byte) & 0xff] ^ (c >>> 8);
    return (c ^ 0xffffffff) >>> 0;
  };
  const chunk = (type: string, data: Uint8Array): Uint8Array => {
    const typeBytes = new TextEncoder().encode(type);
    const body = new Uint8Array(typeBytes.length + data.length);
    body.set(typeBytes);
    body.set(data, typeBytes.length);
    const out = new Uint8Array(body.length + 8);
    const view = new DataView(out.buffer);
    view.setUint32(0, data.length);
    out.set(body, 4);
    view.setUint32(out.length - 4, crc(body));
    return out;
  };

  const ihdr = new Uint8Array(13);
  const ihdrView = new DataView(ihdr.buffer);
  ihdrView.setUint32(0, width);
  ihdrView.setUint32(4, height);
  ihdr[8] = 8; // profundidade
  ihdr[9] = 2; // cor verdadeira, sem alfa
  const deflate = zlibStore(new Uint8Array(raw));
  const parts = [
    new Uint8Array([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', ihdr),
    chunk('IDAT', deflate),
    chunk('IEND', new Uint8Array(0)),
  ];
  const total = parts.reduce((sum, part) => sum + part.length, 0);
  const png = new Uint8Array(total);
  let at = 0;
  for (const part of parts) {
    png.set(part, at);
    at += part.length;
  }
  return png;
}

/** Fluxo zlib sem compressao: blocos `stored`, que todo decodificador aceita. */
function zlibStore(data: Uint8Array): Uint8Array {
  const out: number[] = [0x78, 0x01];
  let at = 0;
  while (at < data.length) {
    const size = Math.min(0xffff, data.length - at);
    const last = at + size >= data.length ? 1 : 0;
    out.push(last, size & 0xff, size >> 8, ~size & 0xff, (~size >> 8) & 0xff);
    for (let i = 0; i < size; i += 1) out.push(data[at + i]);
    at += size;
  }
  let a = 1;
  let b = 0;
  for (const byte of data) {
    a = (a + byte) % 65521;
    b = (b + a) % 65521;
  }
  out.push((b >> 8) & 0xff, b & 0xff, (a >> 8) & 0xff, a & 0xff);
  return new Uint8Array(out);
}
