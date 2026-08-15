import * as mupdf from 'mupdf';
import { readFileSync } from 'node:fs';

/**
 * Teste de paridade entre os dois renderizadores (§6, D-1.7).
 *
 * Rasteriza os dois PDFs **com o mesmo rasterizador**, na mesma maquina, e compara os centroides
 * de marcadores ArUco e bolhas. Usar o mesmo rasterizador dos dois lados e o ponto: se cada
 * plataforma rasterizasse a sua, o teste mediria a diferenca entre rasterizadores em vez da
 * diferenca entre renderizadores, e falharia ou passaria pelo motivo errado.
 *
 * A 600 dpi um pixel vale 0,042 mm, uma ordem de grandeza abaixo da tolerancia de 0,3 mm.
 *
 * Uso: node compare.mjs <web.pdf> <android.pdf> <layout.json>
 */

const DPI = 600;
const TOLERANCE_MM = 0.3;
const MM_PER_PX = 25.4 / DPI;
const UM_PER_PT = 25400 / 72;

function umToPx(um) {
  return (um / 1000 / MM_PER_PX);
}

function rasterize(path) {
  const doc = mupdf.Document.openDocument(readFileSync(path), 'application/pdf');
  const pages = [];
  const scale = DPI / 72;
  for (let index = 0; index < doc.countPages(); index += 1) {
    const page = doc.loadPage(index);
    const pixmap = page.toPixmap(
      mupdf.Matrix.scale(scale, scale),
      mupdf.ColorSpace.DeviceGray,
      false,
      true,
    );
    pages.push({
      width: pixmap.getWidth(),
      height: pixmap.getHeight(),
      // Copia obrigatoria: `getPixels()` devolve uma janela para a memoria WASM do mupdf, e
      // rasterizar o segundo documento a invalida. Sem a copia, o primeiro raster vira lixo e
      // toda medicao depois dele sai NaN — que e pior que falhar, porque NaN passa em qualquer
      // comparacao de tolerancia.
      pixels: new Uint8Array(pixmap.getPixels()),
      bounds: page.getBounds(),
    });
  }
  return pages;
}

/**
 * Centroide dos pixels escuros em uma janela ao redor da posicao esperada.
 *
 * A janela vem do proprio `LayoutMap`, entao os dois lados sao medidos exatamente da mesma forma.
 * Um elemento deslocado alem da janela nao produz centroide — e isso conta como falha, que e o
 * comportamento certo.
 */
function centroidIn(page, centerXpx, centerYpx, halfWindowPx) {
  const x0 = Math.max(0, Math.floor(centerXpx - halfWindowPx));
  const x1 = Math.min(page.width - 1, Math.ceil(centerXpx + halfWindowPx));
  const y0 = Math.max(0, Math.floor(centerYpx - halfWindowPx));
  const y1 = Math.min(page.height - 1, Math.ceil(centerYpx + halfWindowPx));

  let weight = 0;
  let sumX = 0;
  let sumY = 0;
  for (let y = y0; y <= y1; y += 1) {
    for (let x = x0; x <= x1; x += 1) {
      // 0 e preto, 255 e branco: o peso e o quanto o pixel esta escuro.
      const darkness = 255 - page.pixels[y * page.width + x];
      if (darkness <= 8) continue;
      weight += darkness;
      sumX += x * darkness;
      sumY += y * darkness;
    }
  }
  if (!Number.isFinite(weight) || weight === 0) return null;
  const x = sumX / weight;
  const y = sumY / weight;
  if (!Number.isFinite(x) || !Number.isFinite(y)) return null;
  return { x, y, weight };
}

/** Elementos cuja posicao o teste confere: marcadores e bolhas. */
function targetsOf(map) {
  const targets = [];
  for (const page of map.pages) {
    for (const primitive of page.primitives) {
      if (primitive.type === 'aruco') {
        targets.push({
          id: primitive.id,
          page: page.index,
          kind: 'aruco',
          centerUm: {
            x: primitive.x + primitive.side / 2,
            y: primitive.y + primitive.side / 2,
          },
          // 60% do lado cobre o marcador inteiro com folga e ainda para antes do vizinho mais
          // proximo. Janela maior deixaria tinta alheia entrar e diluir o centroide — foi assim
          // que um deslocamento deliberado passou despercebido na primeira versao deste script.
          halfWindowUm: Math.round(primitive.side * 0.6),
        });
      } else if (primitive.type === 'circle') {
        targets.push({
          id: primitive.id,
          page: page.index,
          kind: 'bubble',
          centerUm: { x: primitive.center_x, y: primitive.center_y },
          // A bolha tem 4,2 mm e o passo horizontal e 5,2 mm: a borda da vizinha comeca a 3,1 mm
          // do centro, entao a janela precisa ficar abaixo disso.
          halfWindowUm: Math.round(primitive.diameter * 0.6),
        });
      }
    }
  }
  return targets;
}

function compare(webPath, androidPath, mapPath) {
  const map = JSON.parse(readFileSync(mapPath, 'utf8'));
  const web = rasterize(webPath);
  const android = rasterize(androidPath);

  const problems = [];

  // Estrutura: paginas, regioes e bolhas por regiao precisam ser identicas.
  if (web.length !== android.length) {
    problems.push(`numero de paginas difere: web ${web.length}, android ${android.length}`);
  }
  if (web.length !== map.pages.length) {
    problems.push(`web tem ${web.length} paginas, o mapa declara ${map.pages.length}`);
  }
  for (let index = 0; index < Math.min(web.length, android.length); index += 1) {
    if (web[index].width !== android[index].width || web[index].height !== android[index].height) {
      problems.push(
        `pagina ${index} com raster de tamanhos diferentes: ` +
          `web ${web[index].width}x${web[index].height}, ` +
          `android ${android[index].width}x${android[index].height}`,
      );
    }
  }

  const targets = targetsOf(map);
  let worst = { id: null, mm: 0 };
  let measured = 0;
  const missing = [];

  for (const target of targets) {
    const webPage = web[target.page];
    const androidPage = android[target.page];
    if (!webPage || !androidPage) continue;

    const cx = umToPx(target.centerUm.x);
    const cy = umToPx(target.centerUm.y);
    const half = umToPx(target.halfWindowUm);

    const a = centroidIn(webPage, cx, cy, half);
    const b = centroidIn(androidPage, cx, cy, half);
    if (!a || !b) {
      missing.push(`${target.id} (${!a ? 'web' : 'android'} sem tinta na janela)`);
      continue;
    }

    const dx = (a.x - b.x) * MM_PER_PX;
    const dy = (a.y - b.y) * MM_PER_PX;
    const distance = Math.hypot(dx, dy);
    if (!Number.isFinite(distance)) {
      // Uma distancia nao finita nunca pode passar em silencio: `NaN > tolerancia` e falso, entao
      // sem esta guarda um defeito de medicao vira aprovacao.
      problems.push(`${target.kind} ${target.id} produziu distancia nao finita`);
      continue;
    }
    measured += 1;
    if (distance > worst.mm) worst = { id: target.id, mm: distance, kind: target.kind };
    if (distance > TOLERANCE_MM) {
      problems.push(
        `${target.kind} ${target.id} divergiu ${distance.toFixed(3)} mm ` +
          `(tolerancia ${TOLERANCE_MM} mm)`,
      );
    }
  }

  if (missing.length > 0) {
    problems.push(`elementos nao encontrados no raster: ${missing.slice(0, 10).join(', ')}`);
  }

  console.log(`rasterizador unico: mupdf a ${DPI} dpi (${MM_PER_PX.toFixed(4)} mm/px)`);
  console.log(`paginas: ${web.length} | elementos comparados: ${measured} de ${targets.length}`);
  console.log(
    `maior divergencia: ${worst.mm.toFixed(3)} mm` +
      (worst.id ? ` em ${worst.id}` : '') +
      ` | tolerancia ${TOLERANCE_MM} mm | folga ${(TOLERANCE_MM - worst.mm).toFixed(3)} mm`,
  );

  if (problems.length > 0) {
    console.error('\nPARIDADE FALHOU:');
    for (const problem of problems.slice(0, 20)) console.error(`  - ${problem}`);
    if (problems.length > 20) console.error(`  ... e mais ${problems.length - 20}`);
    process.exit(1);
  }
  console.log('\nparidade OK');
}

const [, , webPath, androidPath, mapPath] = process.argv;
if (!webPath || !androidPath || !mapPath) {
  console.error('uso: node compare.mjs <web.pdf> <android.pdf> <layout.json>');
  process.exit(2);
}
compare(webPath, androidPath, mapPath);
