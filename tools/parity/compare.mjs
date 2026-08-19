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

/** Folga da janela de medicao da formula, de cada lado. Ver `targetsOf`. */
const WINDOW_SLACK_UM = 1_000;
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
 * Folga da janela ate a tinta vizinha, em pixels, medida no documento de referencia.
 *
 * Precisa ser medida **uma vez** e aplicada aos dois documentos: janelas diferentes nos dois
 * lados compararia coisas diferentes, e a paridade deixaria de significar o que diz.
 *
 * O teto e o mesmo 1 mm de sempre. O que muda e o piso do vizinho: formula em bloco nao tem
 * ninguem a 1 mm e fica com o teto; formula em **linha** tem a palavra ao lado na mesma linha
 * de base, e ai a folga encolhe ate metade da distancia ate a tinta dela. Sem isso, o centroide
 * passa a ser dominado pela massa de tinta do vizinho e a medicao deixa de falar da formula.
 */
function slackFor(page, centerXpx, centerYpx, halfWidthPx, halfHeightPx, tetoPx) {
  const dark = (x, y) => 255 - page.pixels[y * page.width + x] > 8;
  const bx0 = Math.round(centerXpx - halfWidthPx);
  const bx1 = Math.round(centerXpx + halfWidthPx);
  const by0 = Math.round(centerYpx - halfHeightPx);
  const by1 = Math.round(centerYpx + halfHeightPx);
  const BLEED = 2; // antialiasing da propria caixa transborda cerca de um pixel

  const colunaTemTinta = (x) => {
    for (let y = Math.max(0, by0); y <= Math.min(page.height - 1, by1); y += 1) if (dark(x, y)) return true;
    return false;
  };
  const linhaTemTinta = (y) => {
    for (let x = Math.max(0, bx0); x <= Math.min(page.width - 1, bx1); x += 1) if (dark(x, y)) return true;
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

  // Uma folga so, a menor dos quatro lados: a janela do centroide e simetrica por construcao,
  // e alargar de um lado so deslocaria o centroide sozinho.
  return Math.min(
    varrer(bx0, -1, page.width - 1, colunaTemTinta),
    varrer(bx1, +1, page.width - 1, colunaTemTinta),
    varrer(by0, -1, page.height - 1, linhaTemTinta),
    varrer(by1, +1, page.height - 1, linhaTemTinta),
  );
}

/**
 * Canto superior esquerdo da tinta dentro da janela.
 *
 * **O centroide e o instrumento errado para formula em linha, e isso foi medido nos dois
 * sentidos.** Com janela folgada de 1 mm, a palavra vizinha entra na janela e domina a massa:
 * dois documentos corretos — cada um dentro de 0,039 mm do mapa, pela fidelidade — apareciam
 * divergindo 0,495 mm. Com janela encolhida ate o vizinho, a formula deslocada de proposito sai
 * da janela e o centroide volta para o meio: um deslocamento real de 0,5 mm deixava de ser
 * acusado. Nao existe janela que resolva os dois: horizontalmente, uma formula em linha
 * deslocada meio milimetro invade mesmo o vizinho.
 *
 * A borda de tinta nao tem esse problema. Ela nao e media ponderada, entao tinta alheia dentro
 * da janela nao a desloca — e quando a formula anda, a borda anda junto, ainda que parte dela
 * saia da janela. E o mesmo instrumento que `fidelidade.mjs` usa para julgar formula.
 */
function inkCornerIn(page, centerXpx, centerYpx, halfWidthPx, halfHeightPx) {
  const x0 = Math.max(0, Math.floor(centerXpx - halfWidthPx));
  const x1 = Math.min(page.width - 1, Math.ceil(centerXpx + halfWidthPx));
  const y0 = Math.max(0, Math.floor(centerYpx - halfHeightPx));
  const y1 = Math.min(page.height - 1, Math.ceil(centerYpx + halfHeightPx));
  let ax = Infinity;
  let ay = Infinity;
  for (let y = y0; y <= y1; y += 1) {
    for (let x = x0; x <= x1; x += 1) {
      if (255 - page.pixels[y * page.width + x] <= 8) continue;
      if (x < ax) ax = x;
      if (y < ay) ay = y;
    }
  }
  return Number.isFinite(ax) ? { x: ax, y: ay } : null;
}

/**
 * Centroide dos pixels escuros em uma janela ao redor da posicao esperada.
 *
 * A janela vem do proprio `LayoutMap`, entao os dois lados sao medidos exatamente da mesma forma.
 * Um elemento deslocado alem da janela nao produz centroide — e isso conta como falha, que e o
 * comportamento certo.
 */
function centroidIn(page, centerXpx, centerYpx, halfWidthPx, halfHeightPx = halfWidthPx) {
  const x0 = Math.max(0, Math.floor(centerXpx - halfWidthPx));
  const x1 = Math.min(page.width - 1, Math.ceil(centerXpx + halfWidthPx));
  const y0 = Math.max(0, Math.floor(centerYpx - halfHeightPx));
  const y1 = Math.min(page.height - 1, Math.ceil(centerYpx + halfHeightPx));

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
      } else if (primitive.type === 'image') {
        targets.push({
          id: primitive.id,
          page: page.index,
          kind: 'formula',
          centerUm: {
            x: primitive.x + primitive.width / 2,
            y: primitive.y + primitive.height / 2,
          },
          // A caixa declarada mais 1 mm de folga de cada lado, e nao um quadrado.
          //
          // Retangulo porque a formula e larga e baixa: um quadrado do tamanho da largura
          // alcancaria o enunciado acima e as alternativas abaixo, que ficam a 3 mm, e tinta
          // alheia dilui o centroide.
          //
          // E com folga porque a janela justa comete o erro simetrico — ela **recorta** a propria
          // formula quando esta deslocada, e o centroide volta para o meio. Medido: com folga
          // zero, um deslocamento deliberado de 0,500 mm era lido como 0,142 mm e passava na
          // tolerancia de 0,3 mm.
          //
          // O teto de folga e 1 mm, mas ele **nao** vale para toda formula: a de bloco tem 3 mm
          // de respiro ate o texto, a em linha tem a palavra vizinha a fracao de milimetro na
          // mesma linha de base. A folga efetiva e decidida em `slackFor`, contra a tinta.
          boxUm: { width: primitive.width, height: primitive.height },
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
    let half;
    let halfY;
    if (target.boxUm) {
      // A folga sai do documento de referencia e vale para os dois lados.
      const baseX = umToPx(Math.round(target.boxUm.width / 2));
      const baseY = umToPx(Math.round(target.boxUm.height / 2));
      const folga = slackFor(webPage, cx, cy, baseX, baseY, umToPx(WINDOW_SLACK_UM));
      half = baseX + folga;
      halfY = baseY + folga;
    } else {
      half = umToPx(target.halfWindowUm);
      halfY = umToPx(target.halfHeightUm ?? target.halfWindowUm);
    }

    // Formula usa borda de tinta; o resto usa centroide. Ver `inkCornerIn`.
    const medir = target.boxUm ? inkCornerIn : centroidIn;
    const a = medir(webPage, cx, cy, half, halfY);
    const b = medir(androidPage, cx, cy, half, halfY);
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
