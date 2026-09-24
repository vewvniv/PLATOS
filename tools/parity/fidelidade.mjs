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

/**
 * Rasteriza uma pagina do documento em cinza de 8 bits (D-1.5.7).
 *
 * A copia de `getPixels()` e obrigatoria: ela aponta para a memoria WASM do mupdf, e rasterizar a
 * proxima pagina a invalida. Sem a copia, a pagina anterior vira lixo e toda medicao depois dela
 * sai `NaN` — que e pior que falhar, porque `NaN > tolerancia` e falso e passa calado.
 */
function rasterize(index) {
  const pixmap = doc
    .loadPage(index)
    .toPixmap(mupdf.Matrix.scale(DPI / 72, DPI / 72), mupdf.ColorSpace.DeviceGray, false, true);
  return {
    width: pixmap.getWidth(),
    height: pixmap.getHeight(),
    pixels: new Uint8Array(pixmap.getPixels()),
  };
}

const page0 = rasterize(0);
const width = page0.width;
const height = page0.height;
const pixels = page0.pixels;
const dark = (x, y) => pixels[y * width + x] < 128;
const toPx = (um) => um / 1000 / MM;

const checks = [];
function check(label, observedMm, expectedMm) {
  const delta = Math.abs(observedMm - expectedMm);
  checks.push({ label, observedMm, expectedMm, delta, ok: delta <= TOLERANCE_MM });
}

/**
 * Caixa de tinta de um pixmap inteiro, em pixels dele.
 *
 * Usada sobre o PNG de origem, que e o oracle independente das formulas: ele nao passa por
 * nenhuma linha de codigo do renderizador.
 */
function inkBoxOfPixmap(pixmap) {
  const w = pixmap.getWidth();
  const h = pixmap.getHeight();
  const n = pixmap.getNumberOfComponents();
  const px = pixmap.getPixels();
  let x0 = Infinity, x1 = -1, y0 = Infinity, y1 = -1;
  for (let y = 0; y < h; y += 1) {
    for (let x = 0; x < w; x += 1) {
      const i = (y * w + x) * n;
      const value = n >= 3 ? (px[i] + px[i + 1] + px[i + 2]) / 3 : px[i];
      if (value >= 128) continue;
      if (x < x0) x0 = x;
      if (x > x1) x1 = x;
      if (y < y0) y0 = y;
      if (y > y1) y1 = y;
    }
  }
  return x1 < 0 ? null : { x0, x1, y0, y1 };
}

/**
 * Caixa de tinta de um elemento, procurada numa janela folgada ao redor do declarado.
 *
 * [marginPx] pode ser um numero — mesma folga nos quatro lados — ou `{left, right, top, bottom}`.
 * A folga por lado existe por causa da formula **em linha**: ver [roomAround].
 */
function inkBox(xUm, yUm, wUm, hUm, marginPx = 60, page = page0) {
  const { width, height, pixels } = page;
  const dark = (x, y) => pixels[y * width + x] < 128;
  const m = typeof marginPx === 'number'
    ? { left: marginPx, right: marginPx, top: marginPx, bottom: marginPx }
    : marginPx;
  const x0 = Math.max(0, Math.round(toPx(xUm)) - m.left);
  const x1 = Math.min(width - 1, Math.round(toPx(xUm + wUm)) + m.right);
  const y0 = Math.max(0, Math.round(toPx(yUm)) - m.top);
  const y1 = Math.min(height - 1, Math.round(toPx(yUm + hUm)) + m.bottom);
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

/**
 * Quanto de branco existe de cada lado da caixa declarada, antes da tinta do vizinho.
 *
 * A folga da janela **nao pode ser constante**. Uma formula em bloco tem 2,8 mm de branco acima
 * e 7,8 mm abaixo, e a coluna inteira na horizontal: 1 mm de folga nunca alcanca nada. Uma
 * formula em **linha** tem a palavra vizinha a fracao de milimetro na mesma linha de base, e a
 * mesma folga de 1 mm entra dentro dela — a medicao passa a somar a tinta do vizinho a da
 * formula, e reporta largura maior que a declarada.
 *
 * Esta base ja produziu quatro verificacoes incapazes de falhar, e **duas foram por janela mal
 * dimensionada**: uma alcancava o vizinho e diluia o desvio, outra recortava o proprio elemento
 * deslocado e lia 0,5 mm como 0,142 mm. As duas pontas importam, entao a folga aqui e a maior
 * possivel que ainda nao alcanca o vizinho: **metade da distancia ate a tinta mais proxima**,
 * limitada ao teto de 1 mm que a formula em bloco ja usava.
 *
 * Os primeiros pixels sao ignorados de proposito: o antialiasing da propria formula transborda
 * a caixa declarada em cerca de um pixel, e conta-lo como vizinho zeraria a folga.
 */
function roomAround(xUm, yUm, wUm, hUm, tetoPx, page) {
  const { width, height, pixels } = page;
  const dark = (x, y) => pixels[y * width + x] < 128;
  const bx0 = Math.round(toPx(xUm));
  const bx1 = Math.round(toPx(xUm + wUm));
  const by0 = Math.round(toPx(yUm));
  const by1 = Math.round(toPx(yUm + hUm));
  const BLEED = 2; // antialiasing da propria caixa

  const colunaTemTinta = (x) => {
    for (let y = Math.max(0, by0); y <= Math.min(height - 1, by1); y += 1) if (dark(x, y)) return true;
    return false;
  };
  const linhaTemTinta = (y) => {
    for (let x = Math.max(0, bx0); x <= Math.min(width - 1, bx1); x += 1) if (dark(x, y)) return true;
    return false;
  };

  const varrer = (inicio, passo, limite, temTinta) => {
    for (let d = BLEED; d <= tetoPx; d += 1) {
      const at = inicio + passo * d;
      if (at < 0 || at > limite) return tetoPx;
      if (temTinta(at)) return Math.max(0, Math.floor(d / 2));
    }
    return tetoPx;
  };

  return {
    left: varrer(bx0, -1, width - 1, colunaTemTinta),
    right: varrer(bx1, +1, width - 1, colunaTemTinta),
    top: varrer(by0, -1, height - 1, linhaTemTinta),
    bottom: varrer(by1, +1, height - 1, linhaTemTinta),
  };
}

// Pagina: a caixa e declarada em pontos inteiros de proposito (D-1.7), porque o `PdfDocument` do
// Android so aceita inteiro e uma caixa diferente entre as plataformas seria divergencia gratuita.
// Conferir contra os 210 x 297 nominais acusaria esse arredondamento como defeito; o alvo correto
// e a caixa que o documento deve declarar.
const pageBoxMm = (um) => (Math.round((um * 72) / 25400) * 25.4) / 72;
check('largura da pagina', width * MM, pageBoxMm(map.page_width));
check('altura da pagina', height * MM, pageBoxMm(map.page_height));

// Marcadores ArUco: lado e posicao, em TODAS as paginas.
//
// Ate a `slice-5a-regiao-discursiva` so a pagina 0 era medida, porque so ela tinha marcador. A regiao
// discursiva poe marcadores em qualquer pagina, e a versao anterior deixava um marcador da pagina 1
// deslocado 0,5 mm passar com "fidelidade OK" — medido em 2026-09-24, com as mesmas 47 verificacoes
// da folha correta. O rotulo da pagina 0 fica como era, para a saida de sempre nao mudar.
const arucos = map.pages[0].primitives.filter((p) => p.type === 'aruco');
const boxes = new Map();
for (const page of map.pages) {
  const doPage = page.primitives.filter((p) => p.type === 'aruco');
  if (doPage.length === 0) continue;
  const raster = page.index === 0 ? page0 : rasterize(page.index);
  const nome = (aruco) =>
    page.index === 0 ? `marcador ${aruco.marker_id}` : `marcador ${aruco.marker_id} (pagina ${page.index})`;
  for (const aruco of doPage) {
    const box = inkBox(aruco.x, aruco.y, aruco.side, aruco.side, 60, raster);
    if (!box) {
      console.error(`${nome(aruco)} nao encontrado no raster`);
      process.exit(1);
    }
    if (page.index === 0) boxes.set(aruco.marker_id, box);
    check(`${nome(aruco)}: lado horizontal`, box.x1 - box.x0, aruco.side / 1000);
    check(`${nome(aruco)}: lado vertical`, box.y1 - box.y0, aruco.side / 1000);
    check(`${nome(aruco)}: borda esquerda`, box.x0, aruco.x / 1000);
    check(`${nome(aruco)}: borda superior`, box.y0, aruco.y / 1000);
  }
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

// Formulas: a caixa declarada e desenhada a partir de um PNG versionado, entao da para conferir a
// posicao contra um oracle que nao compartilha codigo nenhum com o renderizador — o proprio
// arquivo. Mede-se a caixa de tinta dentro do PNG, mapeia-se para a caixa declarada na pagina, e
// compara-se com a tinta que o documento realmente traz ali.
//
// Conferir a caixa *declarada* contra a tinta nao funcionaria: o `viewBox` do MathJax inclui folga
// tipografica, entao a tinta e sempre menor que a caixa, por uma margem que depende da formula.
const imagePages = map.pages
  .map((page) => ({ index: page.index, images: page.primitives.filter((p) => p.type === 'image') }))
  .filter((page) => page.images.length > 0);

if (imagePages.length > 0) {
  const manifest = JSON.parse(
    readFileSync(new URL('../../fixtures/formulas.manifest.json', import.meta.url), 'utf8'),
  );
  const byId = new Map(manifest.formulas.map((f) => [f.id, f]));

  for (const { index, images } of imagePages) {
    // As formulas caem nas paginas de questoes, e nao na pagina do gabarito: medir so a pagina 0
    // deixaria todas elas sem verificacao nenhuma, e a saida diria "fidelidade OK" do mesmo jeito.
    const page = rasterize(index);

    for (const image of images) {
      const declared = byId.get(image.reference);
      if (!declared) {
        console.error(`o manifesto nao descreve a formula \`${image.reference}\``);
        process.exit(1);
      }

      const source = new mupdf.Image(
        readFileSync(new URL(`../../fixtures/${declared.raster}`, import.meta.url)),
      );
      const sourcePix = source.toPixmap();
      const box = inkBoxOfPixmap(sourcePix);
      if (!box) {
        console.error(`o raster de \`${image.reference}\` nao tem tinta nenhuma`);
        process.exit(1);
      }

      // Da caixa de tinta em pixels do PNG para milimetros absolutos na pagina.
      const scaleX = image.width / sourcePix.getWidth();
      const scaleY = image.height / sourcePix.getHeight();
      const expected = {
        x0: (image.x + box.x0 * scaleX) / 1000,
        x1: (image.x + (box.x1 + 1) * scaleX) / 1000,
        y0: (image.y + box.y0 * scaleY) / 1000,
        y1: (image.y + (box.y1 + 1) * scaleY) / 1000,
      };

      // Teto de 1 mm, como antes; mas a folga de cada lado encolhe ate a metade da distancia
      // ate a tinta vizinha. Em bloco nada muda — nao ha vizinho a 1 mm. Em linha, e o que
      // impede a janela de somar a palavra ao lado a tinta da formula.
      const teto = Math.round(toPx(1000));
      const margin = roomAround(image.x, image.y, image.width, image.height, teto, page);
      const observed = inkBox(image.x, image.y, image.width, image.height, margin, page);
      if (!observed) {
        console.error(`a formula \`${image.id}\` nao aparece no documento`);
        process.exit(1);
      }

      check(`formula ${image.id}: borda esquerda`, observed.x0, expected.x0);
      check(`formula ${image.id}: borda superior`, observed.y0, expected.y0);
      check(
        `formula ${image.id}: largura da tinta`,
        observed.x1 - observed.x0,
        expected.x1 - expected.x0,
      );
      check(
        `formula ${image.id}: altura da tinta`,
        observed.y1 - observed.y0,
        expected.y1 - expected.y0,
      );
    }
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
