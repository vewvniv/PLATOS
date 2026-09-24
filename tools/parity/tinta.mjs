import * as mupdf from 'mupdf';
import { readFileSync } from 'node:fs';

/**
 * Tinta do documento: orcamento nas bolhas, teto de trama e monocromia (ADR-0010, D-2b.6).
 *
 * Mede o que foi **impresso**, e nao o que o mapa declara. A distincao e a razao de este arquivo
 * existir: a validacao do `LayoutMap` nao rasteriza e nao tem como conhecer a tinta de um glifo, e
 * o que o OMR le e tinta.
 *
 * Nenhuma linha aqui compartilha codigo com o Layout Engine que produziu a geometria. O mapa entra
 * so como declaracao — onde estao as bolhas e quanto de tinta elas podem ter.
 *
 * Uso: node tinta.mjs <arquivo.pdf> <layout.json>
 */

/** Cobertura e medida fina: 1200 dpi, como a fidelidade (ADR-0001). */
const DPI = 1200;

/** Cor nao precisa de resolucao — precisa existir. 300 dpi acha qualquer pixel cromatico. */
const COLOR_DPI = 300;

/** Recuo da janela de medicao de trama, para dentro da borda, fugindo do antialiasing. */
const TINT_INSET_UM = 300;

/** Distancia admitida entre a trama medida no papel e a declarada no mapa. */
const TINT_VS_DECLARED = 0.02;

const [, , pdfPath, mapPath] = process.argv;
if (!pdfPath || !mapPath) {
  console.error('uso: node tinta.mjs <arquivo.pdf> <layout.json>');
  process.exit(2);
}

const map = JSON.parse(readFileSync(mapPath, 'utf8'));
const doc = mupdf.Document.openDocument(readFileSync(pdfPath), 'application/pdf');
const problems = [];

/**
 * Rasteriza uma pagina.
 *
 * A copia de `getPixels()` e obrigatoria: ela aponta para a memoria WASM do mupdf, e rasterizar a
 * proxima pagina a invalida. Sem a copia, toda medicao depois dela sai `NaN` — que e pior que
 * falhar, porque `NaN > teto` e falso e passa calado.
 */
function rasterize(index, dpi, colorspace) {
  const pixmap = doc
    .loadPage(index)
    .toPixmap(mupdf.Matrix.scale(dpi / 72, dpi / 72), colorspace, false, true);
  return {
    width: pixmap.getWidth(),
    height: pixmap.getHeight(),
    components: pixmap.getNumberOfComponents(),
    pixels: new Uint8Array(pixmap.getPixels()),
  };
}

const umToPx = (um, dpi = DPI) => (um / 1000 / 25.4) * dpi;

/** Desnormaliza (u,v) em ppm de volta para micrometros sobre o lado do quadrilatero. */
const denorm = (ppm, extent) => (ppm * extent) / 1_000_000;

/** Cobertura media dentro de um disco, de 0 (papel) a 1 (preto pleno). */
function coverageInDisc(page, cxPx, cyPx, rPx) {
  if (!(rPx > 0)) return null;
  let sum = 0;
  let count = 0;
  for (let y = Math.ceil(cyPx - rPx); y <= Math.floor(cyPx + rPx); y += 1) {
    if (y < 0 || y >= page.height) return null;
    for (let x = Math.ceil(cxPx - rPx); x <= Math.floor(cxPx + rPx); x += 1) {
      if (x < 0 || x >= page.width) return null;
      if ((x - cxPx) ** 2 + (y - cyPx) ** 2 > rPx * rPx) continue;
      sum += 255 - page.pixels[y * page.width + x];
      count += 1;
    }
  }
  if (count === 0) return null;
  const coverage = sum / count / 255;
  return Number.isFinite(coverage) ? coverage : null;
}

/**
 * Nivel da **trama** dentro de um retangulo, de 0 (papel) a 1 (preto pleno).
 *
 * E a moda dos valores de pixel, e nao a media, e a razao aparece na primeira execucao: a faixa do
 * gabarito passa por baixo das bolhas, das letras e dos numeros, e a media da area inteira leu
 * 13% — trama de 4,5% mais a tinta preta desenhada por cima. Media responde "quanta tinta ha
 * nesta area"; a pergunta de §7 e "qual e o nivel da trama", e essa e a moda.
 *
 * Trama ausente continua sendo pega: a moda vira 255 e a comparacao com o valor declarado falha.
 */
function tintLevelInRect(page, rectUm) {
  const inset = Math.max(1, Math.round(umToPx(TINT_INSET_UM)));
  const x0 = Math.max(0, Math.ceil(umToPx(rectUm.x) + inset));
  const y0 = Math.max(0, Math.ceil(umToPx(rectUm.y) + inset));
  const x1 = Math.min(page.width - 1, Math.floor(umToPx(rectUm.x + rectUm.width) - inset));
  const y1 = Math.min(page.height - 1, Math.floor(umToPx(rectUm.y + rectUm.height) - inset));
  if (x1 < x0 || y1 < y0) return null;

  const histogram = new Int32Array(256);
  let count = 0;
  for (let y = y0; y <= y1; y += 1) {
    for (let x = x0; x <= x1; x += 1) {
      histogram[page.pixels[y * page.width + x]] += 1;
      count += 1;
    }
  }
  if (count === 0) return null;

  let mode = 0;
  for (let value = 1; value < 256; value += 1) {
    if (histogram[value] > histogram[mode]) mode = value;
  }
  const level = (255 - mode) / 255;
  return Number.isFinite(level) ? level : null;
}

// ---------------------------------------------------------------- orcamento nas bolhas

let measuredBubbles = 0;
let worstBubble = { id: null, coverage: 0 };
let corridorFloor = 1;

let essayRegions = 0;
for (const region of map.regions) {
  // A regiao discursiva nao tem bolha por construcao (`slice-5a-regiao-discursiva`), e o orcamento de
  // tinta decorativa e sobre bolha. Ela sai desta medicao pelo `kind`, e SO por ele: qualquer outra
  // regiao sem bolha continua caindo em "nenhuma bolha desenhada" abaixo, que e a guarda de vacuidade.
  if (region.kind === 'essay' && region.bubbles.length === 0) {
    essayRegions += 1;
    continue;
  }
  const budget = region.ink_budget;
  if (!budget) {
    problems.push(`regiao ${region.index} nao declara orcamento de tinta`);
    continue;
  }
  corridorFloor = Math.min(corridorFloor, budget.threshold_floor / 1000);

  const page = rasterize(region.page, DPI, mupdf.ColorSpace.DeviceGray);

  // O raio vem do circulo desenhado, descontado o traco: o que interessa e a tinta **dentro** da
  // bolha, e nao o contorno preto que a delimita.
  const circles = (map.pages.find((p) => p.index === region.page)?.primitives ?? []).filter(
    (primitive) => primitive.type === 'circle',
  );
  if (circles.length === 0) {
    problems.push(`regiao ${region.index}: nenhuma bolha desenhada na pagina ${region.page}`);
    continue;
  }
  const radiusUm = Math.min(...circles.map((c) => c.diameter / 2 - c.stroke));

  for (const bubble of region.bubbles) {
    const cx = umToPx(region.quad_x + denorm(bubble.u, region.quad_width));
    const cy = umToPx(region.quad_y + denorm(bubble.v, region.quad_height));
    const coverage = coverageInDisc(page, cx, cy, umToPx(radiusUm));
    const id = `${bubble.question_id}/${bubble.option}`;

    if (coverage === null) {
      problems.push(`bolha ${id}: janela de medicao vazia, fora da pagina ou nao finita`);
      continue;
    }
    measuredBubbles += 1;
    if (coverage > worstBubble.coverage) worstBubble = { id, coverage };
    if (coverage > budget.decorative_max / 1000) {
      problems.push(
        `bolha ${id} tem ${(coverage * 100).toFixed(2)}% de tinta, acima do orcamento ` +
          `decorativo de ${(budget.decorative_max / 10).toFixed(1)}%`,
      );
    }
  }

  const declared = region.bubbles.length;
  if (measuredBubbles < declared) {
    problems.push(`regiao ${region.index}: ${measuredBubbles} de ${declared} bolhas medidas`);
  }
}

// O corredor do limiar precisa continuar livre: se a decoracao encostasse no piso declarado, a
// fatia 3 herdaria uma folha sem lugar onde por o limiar.
if (worstBubble.id !== null && worstBubble.coverage >= corridorFloor) {
  problems.push(
    `a bolha ${worstBubble.id} chega a ${(worstBubble.coverage * 100).toFixed(2)}% e invade o ` +
      `corredor do limiar, que comeca em ${(corridorFloor * 100).toFixed(1)}%`,
  );
}

// ---------------------------------------------------------------- teto de trama no documento

let tints = 0;
let worstTint = { id: null, coverage: 0 };

/**
 * Teto de §7 mais **um passo de quantizacao**.
 *
 * Um raster de 8 bits so representa multiplos de 1/255 = 0,39 ponto, e uma trama declarada
 * exatamente no teto cai no degrau de cima: 80 por mil vira o nivel 234 de 255, que mede 8,24%. Sem
 * a folga, a amostra que existe para **mostrar** o teto reprovaria por arredondamento.
 *
 * A folga nao afrouxa a regra, porque ela e afirmada em dois lugares: a validacao do `LayoutMap`
 * compara o valor **declarado** com o teto, em inteiro exato, e recusa 81 por mil. Aqui se mede o
 * papel, e o que o papel pega e desvio grosso — a faixa a 200 por mil mediu 20,39%.
 */
const QUANTIZATION = 1 / 255;
const ceiling = 0.08 + QUANTIZATION;

for (const page of map.pages) {
  const rects = page.primitives.filter(
    (primitive) => primitive.type === 'rect' && primitive.fill !== null,
  );
  if (rects.length === 0) continue;
  const raster = rasterize(page.index, DPI, mupdf.ColorSpace.DeviceGray);

  for (const rect of rects) {
    const coverage = tintLevelInRect(raster, rect);
    if (coverage === null) {
      problems.push(`trama ${rect.id}: janela de medicao vazia ou nao finita`);
      continue;
    }
    tints += 1;
    if (coverage > worstTint.coverage) worstTint = { id: rect.id, coverage };
    if (coverage > ceiling) {
      problems.push(
        `trama ${rect.id} mediu ${(coverage * 100).toFixed(2)}%, acima do teto de 8% de §7 ` +
          `(mais um passo de quantizacao)`,
      );
    }
    if (Math.abs(coverage - rect.fill / 1000) > TINT_VS_DECLARED) {
      problems.push(
        `trama ${rect.id} mediu ${(coverage * 100).toFixed(2)}% e o mapa declara ` +
          `${(rect.fill / 10).toFixed(1)}%`,
      );
    }
  }
}

// ---------------------------------------------------------------- monocromia

let colored = 0;
for (const page of map.pages) {
  const raster = rasterize(page.index, COLOR_DPI, mupdf.ColorSpace.DeviceRGB);
  if (raster.components < 3) {
    problems.push('o raster de cor voltou sem tres componentes: a guarda nao mediria nada');
    break;
  }
  const step = raster.components;
  for (let at = 0; at + 2 < raster.pixels.length; at += step) {
    const r = raster.pixels[at];
    const g = raster.pixels[at + 1];
    const b = raster.pixels[at + 2];
    if (r !== g || g !== b) {
      colored += 1;
      if (colored === 1) {
        const index = at / step;
        problems.push(
          `pagina ${page.index} tem pixel cromatico em ` +
            `(${index % raster.width}, ${Math.floor(index / raster.width)}): ` +
            `rgb(${r}, ${g}, ${b})`,
        );
      }
    }
  }
}
if (colored > 1) {
  problems.push(`ao todo, ${colored} pixels cromaticos no documento`);
}

// ---------------------------------------------------------------- relatorio

console.log(`documento: ${pdfPath}`);
console.log(
  `bolhas medidas: ${measuredBubbles} | maior cobertura ` +
    `${(worstBubble.coverage * 100).toFixed(2)}%` +
    (worstBubble.id ? ` em ${worstBubble.id}` : '') +
    ` | corredor do limiar comeca em ${(corridorFloor * 100).toFixed(1)}%`,
);
console.log(
  `tramas medidas: ${tints}` +
    (tints > 0
      ? ` | maior ${(worstTint.coverage * 100).toFixed(2)}%` +
        (worstTint.id ? ` em ${worstTint.id}` : '') +
        ` | teto ${(ceiling * 100).toFixed(2)}%`
      : ''),
);
console.log(`monocromia: ${colored === 0 ? 'nenhum pixel cromatico' : `${colored} pixels em cor`}`);
if (essayRegions > 0) {
  console.log(`regioes discursivas fora do orcamento de bolha: ${essayRegions} (sem bolha por construcao)`);
}

if (problems.length > 0) {
  console.error('\nTINTA FALHOU:');
  for (const problem of problems.slice(0, 20)) console.error(`  - ${problem}`);
  if (problems.length > 20) console.error(`  ... e mais ${problems.length - 20}`);
  process.exit(1);
}
console.log('\ntinta OK');
