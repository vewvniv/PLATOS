import * as mupdf from 'mupdf';
import { readFileSync } from 'node:fs';

/**
 * Fidelidade dimensional do documento (ADR-0001).
 *
 * Mede o PDF gerado contra o que o `LayoutMap` declara, rasterizando a 1200 dpi — 0,021 mm por
 * pixel, uma ordem de grandeza abaixo da tolerancia de 0,05 mm.
 *
 * Existe porque a medicao no papel mede a impressora, nao o sistema: tres impressoras reescalaram
 * a mesma folha entre -3,4% e +4,7%, em direcoes opostas, com escala declarada em 100%. O que o
 * projeto controla e o documento, e isso aqui e deterministico e roda em CI.
 *
 * Uso: node fidelidade.mjs <arquivo.pdf> <layout.json>
 */

const DPI = 1200;
const MM = 25.4 / DPI;
const TOLERANCE_MM = 0.05;

const [, , pdfPath, mapPath] = process.argv;
if (!pdfPath || !mapPath) {
  console.error('uso: node fidelidade.mjs <arquivo.pdf> <layout.json>');
  process.exit(2);
}

const map = JSON.parse(readFileSync(mapPath, 'utf8'));
const doc = mupdf.Document.openDocument(readFileSync(pdfPath), 'application/pdf');
const pixmap = doc
  .loadPage(0)
  .toPixmap(mupdf.Matrix.scale(DPI / 72, DPI / 72), mupdf.ColorSpace.DeviceGray, false, true);
const width = pixmap.getWidth();
const height = pixmap.getHeight();
// Copia: `getPixels()` aponta para a memoria WASM e qualquer rasterizacao seguinte a invalida.
const pixels = new Uint8Array(pixmap.getPixels());
const dark = (x, y) => pixels[y * width + x] < 128;
const toPx = (um) => um / 1000 / MM;

const checks = [];
function check(label, observedMm, expectedMm) {
  const delta = Math.abs(observedMm - expectedMm);
  checks.push({ label, observedMm, expectedMm, delta, ok: delta <= TOLERANCE_MM });
}

/** Caixa de tinta de um elemento, procurada numa janela folgada ao redor do declarado. */
function inkBox(xUm, yUm, wUm, hUm, marginPx = 60) {
  const x0 = Math.max(0, Math.round(toPx(xUm)) - marginPx);
  const x1 = Math.min(width - 1, Math.round(toPx(xUm + wUm)) + marginPx);
  const y0 = Math.max(0, Math.round(toPx(yUm)) - marginPx);
  const y1 = Math.min(height - 1, Math.round(toPx(yUm + hUm)) + marginPx);
  let ax0 = Infinity, ax1 = -1, ay0 = Infinity, ay1 = -1;
  for (let y = y0; y <= y1; y += 1) {
    for (let x = x0; x <= x1; x += 1) {
      if (!dark(x, y)) continue;
      if (x < ax0) ax0 = x;
      if (x > ax1) ax1 = x;
      if (y < ay0) ay0 = y;
      if (y > ay1) ay1 = y;
    }
  }
  if (ax1 < 0) return null;
  return { x0: ax0 * MM, x1: (ax1 + 1) * MM, y0: ay0 * MM, y1: (ay1 + 1) * MM };
}

// Pagina: a caixa e declarada em pontos inteiros de proposito (D-1.7), porque o `PdfDocument` do
// Android so aceita inteiro e uma caixa diferente entre as plataformas seria divergencia gratuita.
// Conferir contra os 210 x 297 nominais acusaria esse arredondamento como defeito; o alvo correto
// e a caixa que o documento deve declarar.
const pageBoxMm = (um) => (Math.round((um * 72) / 25400) * 25.4) / 72;
check('largura da pagina', width * MM, pageBoxMm(map.page_width));
check('altura da pagina', height * MM, pageBoxMm(map.page_height));

// Marcadores ArUco: lado e posicao
const arucos = map.pages[0].primitives.filter((p) => p.type === 'aruco');
const boxes = new Map();
for (const aruco of arucos) {
  const box = inkBox(aruco.x, aruco.y, aruco.side, aruco.side);
  if (!box) {
    console.error(`marcador ${aruco.marker_id} nao encontrado no raster`);
    process.exit(1);
  }
  boxes.set(aruco.marker_id, box);
  check(`marcador ${aruco.marker_id}: lado horizontal`, box.x1 - box.x0, aruco.side / 1000);
  check(`marcador ${aruco.marker_id}: lado vertical`, box.y1 - box.y0, aruco.side / 1000);
  check(`marcador ${aruco.marker_id}: borda esquerda`, box.x0, aruco.x / 1000);
  check(`marcador ${aruco.marker_id}: borda superior`, box.y0, aruco.y / 1000);
}

// Vaos entre marcadores: e o que a regua tenta medir no papel
const tl = boxes.get(0), tr = boxes.get(1), bl = boxes.get(2);
const declared = new Map(arucos.map((a) => [a.marker_id, a]));
check(
  'vao horizontal entre marcadores',
  tr.x1 - tl.x0,
  (declared.get(1).x + declared.get(1).side - declared.get(0).x) / 1000,
);
check(
  'vao vertical entre marcadores',
  bl.y0 - tl.y0,
  (declared.get(2).y - declared.get(0).y) / 1000,
);

// Bolha: o anel cruzado no centro da dois trechos de tinta, um por lado
const circles = map.pages[0].primitives.filter((p) => p.type === 'circle');
const firstRowY = Math.min(...circles.map((c) => c.center_y));
const row = circles.filter((c) => c.center_y === firstRowY).sort((a, b) => a.center_x - b.center_x);
const cy = Math.round(toPx(firstRowY));
// Janela estreita de proposito: a vizinha comeca a 3,1 mm do centro.
const half = Math.round(toPx(circles[0].diameter * 0.6));
const centers = [];
for (const circle of row.slice(0, 4)) {
  const cx = toPx(circle.center_x);
  let x0 = Infinity, x1 = -1;
  for (let x = Math.round(cx - half); x <= Math.round(cx + half); x += 1) {
    if (!dark(x, cy)) continue;
    if (x < x0) x0 = x;
    if (x > x1) x1 = x;
  }
  centers.push(((x0 + x1 + 1) / 2) * MM);
  // O diametro externo inclui o traco, meio de cada lado.
  check(
    `bolha ${circle.id}: diametro externo`,
    (x1 + 1 - x0) * MM,
    (circle.diameter + circle.stroke) / 1000,
  );
  check(`bolha ${circle.id}: centro`, ((x0 + x1 + 1) / 2) * MM, circle.center_x / 1000);
}
for (let i = 1; i < centers.length; i += 1) {
  check(
    'passo horizontal entre bolhas',
    centers[i] - centers[i - 1],
    (row[i].center_x - row[i - 1].center_x) / 1000,
  );
}

// Passo vertical sobre dez linhas, que dilui erro de leitura por dez
const column = circles
  .filter((c) => c.center_x === row[0].center_x)
  .sort((a, b) => a.center_y - b.center_y);
if (column.length >= 10) {
  // Janela estreita tambem aqui: a 6 mm de passo, a borda da fileira vizinha esta a 3,79 mm do
  // centro, e uma janela de um diametro inteiro mediria as duas juntas.
  const topOf = (circle) => {
    const cx = Math.round(toPx(circle.center_x));
    const cyPx = toPx(circle.center_y);
    const reach = Math.round(toPx(circle.diameter * 0.6));
    for (let y = Math.round(cyPx - reach); y <= Math.round(cyPx + reach); y += 1) {
      for (let x = cx - reach; x <= cx + reach; x += 1) if (dark(x, y)) return y * MM;
    }
    return null;
  };
  const first = topOf(column[0]);
  const tenth = topOf(column[9]);
  if (first !== null && tenth !== null) {
    check(
      'passo vertical (10 linhas / 9)',
      (tenth - first) / 9,
      (column[9].center_y - column[0].center_y) / 9 / 1000,
    );
  }
}

const failed = checks.filter((c) => !c.ok);
const worst = checks.reduce((a, b) => (b.delta > a.delta ? b : a));

console.log(`documento: ${pdfPath}`);
console.log(`rasterizado a ${DPI} dpi (${MM.toFixed(4)} mm/px) | tolerancia ${TOLERANCE_MM} mm`);
console.log(`verificacoes: ${checks.length} | maior desvio: ${worst.delta.toFixed(3)} mm em "${worst.label}"`);

if (failed.length > 0) {
  console.error('\nFIDELIDADE FALHOU:');
  for (const f of failed.slice(0, 20)) {
    console.error(
      `  - ${f.label}: observado ${f.observedMm.toFixed(3)} mm, declarado ${f.expectedMm.toFixed(3)} mm (desvio ${f.delta.toFixed(3)})`,
    );
  }
  process.exit(1);
}
console.log('\nfidelidade OK');
