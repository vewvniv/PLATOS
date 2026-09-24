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

/** Recuo da janela de medicao de trama, para dentro da borda do retangulo. */
const TINT_INSET_UM = 300;

/** Divergencia de cobertura admitida entre os dois documentos, em fracao de preto. */
const TINT_TOLERANCE = 0.01;

/** Distancia admitida entre a trama medida e a declarada no mapa, em fracao de preto. */
const TINT_VS_DECLARED = 0.02;

/**
 * Retangulo de **traco** — moldura e pauta da regiao discursiva (`slice-5a-regiao-discursiva`,
 * tarefa 6.1b). Os tres numeros foram fixados **antes da primeira medicao** (P11), na propria tarefa:
 *
 * - a faixa de medicao vai `stroke/2 + STROKE_SLACK_UM` para cada lado do contorno declarado;
 * - a **presenca** exige, em cada documento, tinta entre 0,5 e 1,5 vezes a area que o traco declarado
 *   ocupa. O piso pega o traco que nao foi desenhado; o teto pega o traco grosso demais **nos dois
 *   lados**, que a concordancia sozinha deixaria passar;
 * - a **concordancia** admite no maximo 0,10 dessa razao entre web e Android.
 */
const STROKE_SLACK_UM = 200;
const STROKE_PRESENCE_MIN = 0.5;
const STROKE_PRESENCE_MAX = 1.5;
const STROKE_TOLERANCE = 0.1;

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
 * Centroide dos pixels escuros em uma janela ao redor da posicao esperada.
 *
 * A janela vem do proprio `LayoutMap`, entao os dois lados sao medidos exatamente da mesma forma.
 * Um elemento deslocado alem da janela nao produz centroide — e isso conta como falha, que e o
 * comportamento certo.
 */
function centroidIn(page, centerXpx, centerYpx, halfWidthPx, halfHeightPx, darknessFloor) {
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
      if (darkness <= darknessFloor) continue;
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

/**
 * Nivel da **trama** dentro de um retangulo, de 0 (papel) a 1 (preto pleno).
 *
 * Moda dos valores de pixel, e nao media: a faixa do gabarito passa por baixo das bolhas, das
 * letras e dos numeros, e a media da area leria a trama somada a tinta preta desenhada em cima —
 * 13% para uma trama de 4,5%. A pergunta e "qual e o nivel da trama", e essa e a moda.
 *
 * A janela e recuada para dentro porque a borda de um retangulo rasterizado tem antialiasing.
 */
function tintLevelIn(page, rectPx) {
  const inset = Math.max(1, Math.round(umToPx(TINT_INSET_UM)));
  const x0 = Math.max(0, Math.ceil(rectPx.x + inset));
  const y0 = Math.max(0, Math.ceil(rectPx.y + inset));
  const x1 = Math.min(page.width - 1, Math.floor(rectPx.x + rectPx.width - inset));
  const y1 = Math.min(page.height - 1, Math.floor(rectPx.y + rectPx.height - inset));
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
  // `NaN > tolerancia` e falso e passaria calado, como ja aconteceu nesta base.
  return Number.isFinite(level) ? level : null;
}

/**
 * Retangulos com trama declarada (D-2b.5).
 *
 * Ate a fatia 2b nenhum mapa emitia `fill`, e o renderizador Android **ignorava** o campo: o web
 * preenchia, o Android nao, e as 185 comparacoes de centroide continuavam verdes porque faixa
 * ausente nao move marcador nem bolha. Quem mede trama tem de olhar tinta chapada, e nao posicao.
 */
function tintTargetsOf(map) {
  const targets = [];
  for (const page of map.pages) {
    for (const primitive of page.primitives) {
      if (primitive.type !== 'rect') continue;
      if (primitive.fill === null || primitive.fill === undefined) continue;
      targets.push({
        id: primitive.id,
        page: page.index,
        declared: primitive.fill / 1000,
        rectUm: {
          x: primitive.x,
          y: primitive.y,
          width: primitive.width,
          height: primitive.height,
        },
      });
    }
  }
  return targets;
}

/**
 * Retangulos de traco: `stroke > 0` e sem trama (tarefa 6.1b).
 *
 * Ate a `slice-5a-regiao-discursiva` nenhum oraculo olhava traco: a paridade media centroide e trama,
 * a fidelidade media marcador e circulo. A moldura e a pauta da regiao discursiva sao so traco, e um
 * renderizador que nao as desenhasse passaria verde — o mesmo modo de falha da faixa da 2b.
 */
function strokeTargetsOf(map) {
  const targets = [];
  for (const page of map.pages) {
    for (const primitive of page.primitives) {
      if (primitive.type !== 'rect') continue;
      if (!(primitive.stroke > 0)) continue;
      if (primitive.fill !== null && primitive.fill !== undefined) continue;
      targets.push({
        id: primitive.id,
        page: page.index,
        stroke: primitive.stroke,
        rectUm: { x: primitive.x, y: primitive.y, width: primitive.width, height: primitive.height },
      });
    }
  }
  return targets;
}

/**
 * Area que o traco declarado ocupa, em micrometros quadrados: o retangulo crescido de meio traco,
 * menos o retangulo encolhido de meio traco quando ele existe. Vale para o retangulo degenerado de
 * altura zero da pauta, em que o miolo nao existe e a area e `(largura + traco) x traco`.
 */
function declaredStrokeAreaUm2(rectUm, stroke) {
  const outer = (rectUm.width + stroke) * (rectUm.height + stroke);
  const iw = rectUm.width - stroke;
  const ih = rectUm.height - stroke;
  return outer - (iw > 0 && ih > 0 ? iw * ih : 0);
}

/**
 * Tinta na faixa do contorno, em micrometros quadrados: a soma da escuridao dos pixels entre o
 * retangulo crescido de `stroke/2 + folga` e o encolhido do mesmo tanto. Antialiasing conserva area,
 * entao somar a escuridao, e nao contar pixels acima de um limiar, e o que torna o numero comparavel
 * com a area declarada.
 */
function strokeInkIn(page, rectUm, stroke) {
  const grow = stroke / 2 + STROKE_SLACK_UM;
  const ox0 = umToPx(rectUm.x - grow);
  const oy0 = umToPx(rectUm.y - grow);
  const ox1 = umToPx(rectUm.x + rectUm.width + grow);
  const oy1 = umToPx(rectUm.y + rectUm.height + grow);
  const hasInner = rectUm.width - 2 * grow > 0 && rectUm.height - 2 * grow > 0;
  const ix0 = umToPx(rectUm.x + grow);
  const iy0 = umToPx(rectUm.y + grow);
  const ix1 = umToPx(rectUm.x + rectUm.width - grow);
  const iy1 = umToPx(rectUm.y + rectUm.height - grow);

  let darkness = 0;
  for (let y = Math.max(0, Math.floor(oy0)); y <= Math.min(page.height - 1, Math.ceil(oy1)); y += 1) {
    const cy = y + 0.5;
    if (cy < oy0 || cy > oy1) continue;
    for (let x = Math.max(0, Math.floor(ox0)); x <= Math.min(page.width - 1, Math.ceil(ox1)); x += 1) {
      const cx = x + 0.5;
      if (cx < ox0 || cx > ox1) continue;
      if (hasInner && cx > ix0 && cx < ix1 && cy > iy0 && cy < iy1) continue;
      darkness += 255 - page.pixels[y * page.width + x];
    }
  }
  const pxUm = MM_PER_PX * 1000;
  const area = (darkness / 255) * pxUm * pxUm;
  return Number.isFinite(area) ? area : null;
}

/**
 * Piso de escuridao do centroide: acima de **toda** tinta decorativa (D-2b.4).
 *
 * O piso e o teto de tom que o mapa declara para decoracao, e nao um numero escolhido aqui. E a
 * definicao operante de "decorativo": tinta que a paridade enxerga nao e decoracao, e geometria.
 *
 * Ate a fatia 2b o piso era 8 de 255 e nenhum mapa tinha decoracao. Com a folha de §7, a faixa de
 * 4,5% rasteriza com escuridao 11 e a letra dentro do circulo com 102: as duas entrariam no
 * centroide da bolha e o deslocariam — e o deslocamento seria lido como divergencia entre
 * renderizadores, ou pior, mascararia uma.
 */
function darknessFloorOf(map) {
  const declared = map.regions
    .map((region) => region.ink_budget?.decorative_tone_max)
    .filter((tone) => typeof tone === 'number');
  if (declared.length === 0) return 8;
  return Math.ceil((Math.max(...declared) * 255) / 1000);
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
          // tolerancia de 0,3 mm; com 1 mm de folga le 0,466 mm e falha, como tem de ser. O peso
          // de tinta na janela e o que denuncia — 1 195 519 contra 1 226 245 do lado nao
          // deslocado. A folga fica em 1 mm porque o respiro ate o texto vizinho e 3 mm.
          halfWindowUm: Math.round(primitive.width / 2) + WINDOW_SLACK_UM,
          halfHeightUm: Math.round(primitive.height / 2) + WINDOW_SLACK_UM,
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

  const darknessFloor = darknessFloorOf(map);
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
    const halfY = umToPx(target.halfHeightUm ?? target.halfWindowUm);

    const a = centroidIn(webPage, cx, cy, half, halfY, darknessFloor);
    const b = centroidIn(androidPage, cx, cy, half, halfY, darknessFloor);
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

  // Trama: cobertura, e nao posicao. Comparada entre os dois documentos **e** contra o que o mapa
  // declara — sem a segunda metade, uma faixa que sumisse dos dois lados passaria com diferenca
  // zero, que e exatamente o modo de falha que esta comparacao existe para pegar.
  const tintTargets = tintTargetsOf(map);
  let worstTint = { id: null, delta: 0 };
  for (const target of tintTargets) {
    const webPage = web[target.page];
    const androidPage = android[target.page];
    if (!webPage || !androidPage) continue;

    const rectPx = {
      x: umToPx(target.rectUm.x),
      y: umToPx(target.rectUm.y),
      width: umToPx(target.rectUm.width),
      height: umToPx(target.rectUm.height),
    };
    const a = tintLevelIn(webPage, rectPx);
    const b = tintLevelIn(androidPage, rectPx);
    if (a === null || b === null) {
      problems.push(`trama ${target.id}: janela de medicao vazia ou nao finita`);
      continue;
    }

    const delta = Math.abs(a - b);
    if (delta > worstTint.delta) worstTint = { id: target.id, delta };
    if (delta > TINT_TOLERANCE) {
      problems.push(
        `trama ${target.id} divergiu ${(delta * 100).toFixed(2)} pontos de cobertura ` +
          `(web ${(a * 100).toFixed(2)}%, android ${(b * 100).toFixed(2)}%)`,
      );
    }
    for (const [lado, medida] of [['web', a], ['android', b]]) {
      if (Math.abs(medida - target.declared) > TINT_VS_DECLARED) {
        problems.push(
          `trama ${target.id} no ${lado} mediu ${(medida * 100).toFixed(2)}% e o mapa ` +
            `declara ${(target.declared * 100).toFixed(2)}%`,
        );
      }
    }
  }

  // Traco: presenca contra a area declarada, em cada documento, e concordancia entre os dois.
  const strokeTargets = strokeTargetsOf(map);
  let worstStroke = { id: null, delta: 0 };
  const ratios = { web: [], android: [] };
  for (const target of strokeTargets) {
    const webPage = web[target.page];
    const androidPage = android[target.page];
    if (!webPage || !androidPage) continue;

    const declared = declaredStrokeAreaUm2(target.rectUm, target.stroke);
    const inkWeb = strokeInkIn(webPage, target.rectUm, target.stroke);
    const inkAndroid = strokeInkIn(androidPage, target.rectUm, target.stroke);
    if (inkWeb === null || inkAndroid === null || !(declared > 0)) {
      problems.push(`traco ${target.id}: medicao nao finita ou area declarada nao positiva`);
      continue;
    }
    const a = inkWeb / declared;
    const b = inkAndroid / declared;
    ratios.web.push(a);
    ratios.android.push(b);
    for (const [lado, razao] of [['web', a], ['android', b]]) {
      if (razao < STROKE_PRESENCE_MIN || razao > STROKE_PRESENCE_MAX) {
        problems.push(
          `traco ${target.id} no ${lado}: tinta ${razao.toFixed(3)} da area declarada ` +
            `(presenca exige ${STROKE_PRESENCE_MIN} a ${STROKE_PRESENCE_MAX})`,
        );
      }
    }
    const delta = Math.abs(a - b);
    if (delta > worstStroke.delta) worstStroke = { id: target.id, delta };
    if (delta > STROKE_TOLERANCE) {
      problems.push(
        `traco ${target.id} divergiu ${delta.toFixed(3)} da area declarada ` +
          `(web ${a.toFixed(3)}, android ${b.toFixed(3)}; tolerancia ${STROKE_TOLERANCE})`,
      );
    }
  }

  console.log(`rasterizador unico: mupdf a ${DPI} dpi (${MM_PER_PX.toFixed(4)} mm/px)`);
  console.log(`piso de escuridao do centroide: ${darknessFloor} de 255`);
  console.log(`paginas: ${web.length} | elementos comparados: ${measured} de ${targets.length}`);
  console.log(
    `maior divergencia: ${worst.mm.toFixed(3)} mm` +
      (worst.id ? ` em ${worst.id}` : '') +
      ` | tolerancia ${TOLERANCE_MM} mm | folga ${(TOLERANCE_MM - worst.mm).toFixed(3)} mm`,
  );
  console.log(
    `tramas comparadas: ${tintTargets.length}` +
      (tintTargets.length > 0
        ? ` | maior divergencia ${(worstTint.delta * 100).toFixed(2)} pontos` +
          (worstTint.id ? ` em ${worstTint.id}` : '') +
          ` | tolerancia ${(TINT_TOLERANCE * 100).toFixed(2)} pontos`
        : ''),
  );

  const faixa = (lista) =>
    lista.length ? `${Math.min(...lista).toFixed(3)} a ${Math.max(...lista).toFixed(3)}` : '-';
  console.log(
    `tracos comparados: ${strokeTargets.length}` +
      (strokeTargets.length > 0
        ? ` | razao web ${faixa(ratios.web)}, android ${faixa(ratios.android)}` +
          ` | maior divergencia ${worstStroke.delta.toFixed(3)}` +
          (worstStroke.id ? ` em ${worstStroke.id}` : '') +
          ` | tolerancia ${STROKE_TOLERANCE}`
        : ''),
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
