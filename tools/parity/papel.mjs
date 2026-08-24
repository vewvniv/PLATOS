import * as mupdf from 'mupdf';
import { readFileSync, writeFileSync } from 'node:fs';

/**
 * Tinta na folha **digitalizada**: cobertura da bolha preenchida a caneta (ADR-0010, tarefa 8.3).
 *
 * `tinta.mjs` mede o PDF; este mede o papel. A diferenca nao e de precisao, e de pergunta: o PDF
 * nao tem toner, nem espalhamento, nem caneta. O piso de 50% que ADR-0010 declarou **antes** de
 * medir so pode ser conferido aqui.
 *
 * Nenhuma linha compartilha codigo com o Layout Engine. O mapa entra so como declaracao — onde
 * estao as bolhas, que tamanho elas tem e quanto de tinta cada uma pode ter. A geometria do papel
 * vem dos quatro ArUcos achados na propria imagem, e nao de um dpi informado: **o dpi da
 * digitalizacao nao precisa ser conhecido**, porque cobertura e razao e a escala sai dos
 * marcadores.
 *
 * Uso: node papel.mjs <digitalizacao.jpg> <layout.json> [indice-da-regiao] [--json <saida>]
 *
 * `--json` grava o que esta ferramenta mediu — cantos dos marcadores e cobertura bolha a bolha —
 * para que outra implementacao possa ser conferida contra ela. E o unico papel que ela tem depois
 * da fatia 2b: **oracle independente**, em outra linguagem, sobre papel que nao existe mais.
 */

const argv = process.argv.slice(2);
let jsonPath = null;
const jsonFlag = argv.indexOf('--json');
if (jsonFlag !== -1) {
  jsonPath = argv[jsonFlag + 1];
  if (!jsonPath) {
    console.error('--json exige um caminho de saida');
    process.exit(2);
  }
  argv.splice(jsonFlag, 2);
}
const [imagePath, mapPath, regionArg] = argv;
if (!imagePath || !mapPath) {
  console.error('uso: node papel.mjs <digitalizacao.jpg> <layout.json> [indice-da-regiao] [--json <saida>]');
  process.exit(2);
}

const problems = [];
const fail = (message) => problems.push(message);

// ---------------------------------------------------------------- imagem

/**
 * Dimensoes verdadeiras do JPEG, lidas do proprio arquivo.
 *
 * Existe para uma coisa so: o mupdf trata imagem como documento de 96 dpi e devolve pagina em
 * pontos. Rasterizar com a escala errada reamostra a imagem em silencio, e toda medicao seguinte
 * sai de uma imagem que nao e a que o scanner produziu. O tamanho declarado no SOF e o arbitro.
 */
function jpegSize(bytes) {
  let i = 2;
  while (i + 9 < bytes.length) {
    if (bytes[i] !== 0xff) {
      i += 1;
      continue;
    }
    const marker = bytes[i + 1];
    const isFrame =
      marker >= 0xc0 && marker <= 0xcf && marker !== 0xc4 && marker !== 0xc8 && marker !== 0xcc;
    if (isFrame) {
      return { width: (bytes[i + 7] << 8) | bytes[i + 8], height: (bytes[i + 5] << 8) | bytes[i + 6] };
    }
    i += 2 + ((bytes[i + 2] << 8) | bytes[i + 3]);
  }
  return null;
}

const bytes = readFileSync(imagePath);
const isJpeg = /\.jpe?g$/i.test(imagePath);
const declaredSize = isJpeg ? jpegSize(bytes) : null;

const doc = mupdf.Document.openDocument(bytes, isJpeg ? 'image/jpeg' : 'image/png');
const pixmap = doc
  .loadPage(0)
  .toPixmap(mupdf.Matrix.scale(96 / 72, 96 / 72), mupdf.ColorSpace.DeviceGray, false, true);

const W = pixmap.getWidth();
const H = pixmap.getHeight();
const components = pixmap.getNumberOfComponents();
const raw = new Uint8Array(pixmap.getPixels());
const gray = new Uint8Array(W * H);
for (let i = 0; i < W * H; i += 1) gray[i] = raw[i * components];

if (declaredSize && (declaredSize.width !== W || declaredSize.height !== H)) {
  fail(
    'a imagem foi reamostrada: o arquivo declara ' +
      `${declaredSize.width}x${declaredSize.height} e o raster saiu ${W}x${H}`,
  );
}

// ---------------------------------------------------------------- fundo (o branco do papel)

/**
 * Campo de branco local, por percentil sobre blocos.
 *
 * O papel de uma digitalizacao nao e 255, e nao e o mesmo em toda a folha: a lampada cai nas
 * bordas e a sombra do vinco escurece faixas inteiras. Medir cobertura contra 255 fixo somaria
 * essa sombra a tinta da caneta. O percentil alto de cada bloco e o papel daquele pedaco.
 */
const BLOCK = 128;
const WHITE_PERCENTILE = 0.9;
const blocksX = Math.ceil(W / BLOCK);
const blocksY = Math.ceil(H / BLOCK);
const whiteField = new Float64Array(blocksX * blocksY);
{
  for (let by = 0; by < blocksY; by += 1) {
    for (let bx = 0; bx < blocksX; bx += 1) {
      const bucket = [];
      const x1 = Math.min(W, (bx + 1) * BLOCK);
      const y1 = Math.min(H, (by + 1) * BLOCK);
      for (let y = by * BLOCK; y < y1; y += 2) {
        for (let x = bx * BLOCK; x < x1; x += 2) bucket.push(gray[y * W + x]);
      }
      bucket.sort((a, b) => a - b);
      whiteField[by * blocksX + bx] = bucket.length
        ? bucket[Math.min(bucket.length - 1, Math.floor(bucket.length * WHITE_PERCENTILE))]
        : 255;
    }
  }
}

/** Branco no ponto, interpolado entre os centros dos blocos. */
function whiteAt(x, y) {
  const fx = Math.min(blocksX - 1, Math.max(0, x / BLOCK - 0.5));
  const fy = Math.min(blocksY - 1, Math.max(0, y / BLOCK - 0.5));
  const x0 = Math.floor(fx);
  const y0 = Math.floor(fy);
  const x1 = Math.min(blocksX - 1, x0 + 1);
  const y1 = Math.min(blocksY - 1, y0 + 1);
  const tx = fx - x0;
  const ty = fy - y0;
  const a = whiteField[y0 * blocksX + x0] * (1 - tx) + whiteField[y0 * blocksX + x1] * tx;
  const b = whiteField[y1 * blocksX + x0] * (1 - tx) + whiteField[y1 * blocksX + x1] * tx;
  return a * (1 - ty) + b * ty;
}

// ---------------------------------------------------------------- os quatro marcadores

/** Corte para achar marcador: o ArUco e preto pleno, longe de qualquer tinta decorativa. */
const MARKER_CUT = 0.45;

const isDark = new Uint8Array(W * H);
for (let y = 0; y < H; y += 1) {
  for (let x = 0; x < W; x += 1) {
    const i = y * W + x;
    isDark[i] = gray[i] < whiteAt(x, y) * MARKER_CUT ? 1 : 0;
  }
}

function connectedComponents(mask) {
  const seen = new Uint8Array(W * H);
  const stack = new Int32Array(W * H);
  const found = [];
  for (let start = 0; start < W * H; start += 1) {
    if (!mask[start] || seen[start]) continue;
    let sp = 0;
    stack[sp++] = start;
    seen[start] = 1;
    let n = 0;
    let x0 = W;
    let x1 = -1;
    let y0 = H;
    let y1 = -1;
    while (sp) {
      const p = stack[--sp];
      const x = p % W;
      const y = (p / W) | 0;
      n += 1;
      if (x < x0) x0 = x;
      if (x > x1) x1 = x;
      if (y < y0) y0 = y;
      if (y > y1) y1 = y;
      if (x > 0 && mask[p - 1] && !seen[p - 1]) { seen[p - 1] = 1; stack[sp++] = p - 1; }
      if (x < W - 1 && mask[p + 1] && !seen[p + 1]) { seen[p + 1] = 1; stack[sp++] = p + 1; }
      if (y > 0 && mask[p - W] && !seen[p - W]) { seen[p - W] = 1; stack[sp++] = p - W; }
      if (y < H - 1 && mask[p + W] && !seen[p + W]) { seen[p + W] = 1; stack[sp++] = p + W; }
    }
    found.push({ n, x0, x1, y0, y1, w: x1 - x0 + 1, h: y1 - y0 + 1 });
  }
  return found;
}

const map = JSON.parse(readFileSync(mapPath, 'utf8'));
const regionIndex = regionArg === undefined ? 0 : Number(regionArg);
const region = map.regions.find((r) => r.index === regionIndex);
if (!region) {
  console.error(`regiao ${regionIndex} nao existe no mapa`);
  process.exit(2);
}
const pagePrimitives = map.pages.find((p) => p.index === region.page)?.primitives ?? [];
const arucos = pagePrimitives.filter((p) => p.type === 'aruco');
if (arucos.length !== 4) {
  console.error(`a pagina ${region.page} declara ${arucos.length} ArUcos; esperados 4`);
  process.exit(2);
}

// O lado esperado em pixels ainda e desconhecido — a escala vem dos proprios marcadores. O filtro
// aqui e de forma, nao de tamanho: quadrado, cheio, e grande perto da folha.
const minSide = Math.round(Math.min(W, H) * 0.02);
const maxSide = Math.round(Math.min(W, H) * 0.12);
const candidates = connectedComponents(isDark).filter((b) => {
  if (b.w < minSide || b.h < minSide || b.w > maxSide || b.h > maxSide) return false;
  if (Math.abs(b.w - b.h) > 0.12 * Math.max(b.w, b.h)) return false;
  return b.n / (b.w * b.h) > 0.5;
});
candidates.sort((a, b) => b.n - a.n);
const markers = candidates.slice(0, 4);
if (markers.length !== 4) {
  console.error(`achei ${markers.length} marcador(es) na imagem, e nao 4 — nao da para medir nada`);
  process.exit(1);
}

const centerOf = (b) => ({ x: (b.x0 + b.x1) / 2, y: (b.y0 + b.y1) / 2 });
const byY = [...markers].sort((a, b) => centerOf(a).y - centerOf(b).y);
const top = byY.slice(0, 2).sort((a, b) => centerOf(a).x - centerOf(b).x);
const bottom = byY.slice(2).sort((a, b) => centerOf(a).x - centerOf(b).x);
const corners = { tl: top[0], tr: top[1], bl: bottom[0], br: bottom[1] };

/**
 * Le os 7x7 modulos de um marcador e compara com o declarado no mapa.
 *
 * Cobre "Marcador incompleto reprova" no papel: borrao que une dois modulos, ou falha branca
 * dentro de um modulo preto, trocam um bit — e o bit trocado aparece aqui. A olho, §4 do protocolo
 * pede a mesma coisa; mas o olho nao conta 49 modulos vezes quatro.
 */
function readModules(box, size) {
  const stepX = box.w / size;
  const stepY = box.h / size;
  const half = Math.max(1, Math.floor(Math.min(stepX, stepY) / 4));
  const rows = [];
  for (let row = 0; row < size; row += 1) {
    let line = '';
    for (let col = 0; col < size; col += 1) {
      const cx = box.x0 + (col + 0.5) * stepX;
      const cy = box.y0 + (row + 0.5) * stepY;
      let sum = 0;
      let count = 0;
      for (let dy = -half; dy <= half; dy += 1) {
        for (let dx = -half; dx <= half; dx += 1) {
          const x = Math.round(cx + dx);
          const y = Math.round(cy + dy);
          if (x < 0 || y < 0 || x >= W || y >= H) continue;
          sum += gray[y * W + x];
          count += 1;
        }
      }
      if (count === 0) return null;
      line += sum / count < whiteAt(cx, cy) * MARKER_CUT ? '1' : '0';
    }
    rows.push(line);
  }
  return rows;
}

const cornerOrder = ['tl', 'tr', 'bl', 'br'];
const declaredByCorner = {};
{
  const sorted = [...arucos].sort((a, b) => a.y - b.y || a.x - b.x);
  cornerOrder.forEach((corner, i) => {
    declaredByCorner[corner] = sorted[i];
  });
}
let modulesChecked = 0;
for (const corner of cornerOrder) {
  const declared = declaredByCorner[corner];
  const size = declared.modules.length;
  const read = readModules(corners[corner], size);
  if (read === null) {
    fail(`marcador ${declared.id}: nao consegui ler os modulos dentro da imagem`);
    continue;
  }
  let wrong = 0;
  for (let row = 0; row < size; row += 1) {
    for (let col = 0; col < size; col += 1) {
      modulesChecked += 1;
      if (read[row][col] !== declared.modules[row][col]) wrong += 1;
    }
  }
  if (wrong > 0) {
    fail(
      `marcador ${declared.id} (${corner}) tem ${wrong} modulo(s) diferentes do declarado: ` +
        `li ${read.join('/')}, esperado ${declared.modules.join('/')}`,
    );
  }
}

// ---------------------------------------------------------------- homografia do quadrilatero

/** O quadrilatero da regiao e o retangulo dos **centros** dos marcadores; e o que o mapa declara. */
const quad = cornerOrder.map((c) => centerOf(corners[c]));

/** Resolve A x = b por eliminacao de Gauss com pivotamento parcial. */
function solve(A, b) {
  const n = b.length;
  for (let col = 0; col < n; col += 1) {
    let pivot = col;
    for (let row = col + 1; row < n; row += 1) {
      if (Math.abs(A[row][col]) > Math.abs(A[pivot][col])) pivot = row;
    }
    if (Math.abs(A[pivot][col]) < 1e-12) return null;
    [A[col], A[pivot]] = [A[pivot], A[col]];
    [b[col], b[pivot]] = [b[pivot], b[col]];
    for (let row = 0; row < n; row += 1) {
      if (row === col) continue;
      const factor = A[row][col] / A[col][col];
      for (let k = col; k < n; k += 1) A[row][k] -= factor * A[col][k];
      b[row] -= factor * b[col];
    }
  }
  return b.map((value, i) => value / A[i][i]);
}

/** Projetiva do quadrado unitario (u,v) para o pixel, pelos quatro centros de marcador. */
function homography(points) {
  const unit = [[0, 0], [1, 0], [0, 1], [1, 1]];
  const A = [];
  const b = [];
  for (let i = 0; i < 4; i += 1) {
    const [u, v] = unit[i];
    const { x, y } = points[i];
    A.push([u, v, 1, 0, 0, 0, -u * x, -v * x]);
    b.push(x);
    A.push([0, 0, 0, u, v, 1, -u * y, -v * y]);
    b.push(y);
  }
  const h = solve(A, b);
  if (h === null) return null;
  return (u, v) => {
    const w = h[6] * u + h[7] * v + 1;
    return { x: (h[0] * u + h[1] * v + h[2]) / w, y: (h[3] * u + h[4] * v + h[5]) / w };
  };
}

const project = homography(quad);
if (project === null) {
  console.error('os quatro marcadores sao degenerados; nao da para montar a homografia');
  process.exit(1);
}

const dist = (a, b) => Math.hypot(a.x - b.x, a.y - b.y);
const spanX = (dist(quad[0], quad[1]) + dist(quad[2], quad[3])) / 2;
const spanY = (dist(quad[0], quad[2]) + dist(quad[1], quad[3])) / 2;
const pxPerUmX = spanX / region.quad_width;
const pxPerUmY = spanY / region.quad_height;

// ---------------------------------------------------------------- cobertura das bolhas

const circles = pagePrimitives.filter((p) => p.type === 'circle');
if (circles.length === 0) {
  console.error(`a pagina ${region.page} nao desenha bolha nenhuma`);
  process.exit(2);
}
// Mesma definicao de `tinta.mjs`: o raio do circulo desenhado menos o traco. O que interessa e a
// tinta **dentro** da bolha, e nao o contorno preto que a delimita.
const radiusUm = Math.min(...circles.map((c) => c.diameter / 2 - c.stroke));
const radiusPx = (radiusUm * (pxPerUmX + pxPerUmY)) / 2;
if (!(radiusPx > 2)) {
  console.error(`o raio de medicao saiu em ${radiusPx.toFixed(2)} px — imagem pequena demais`);
  process.exit(1);
}

/** Cobertura media dentro do disco, de 0 (papel local) a 1 (preto pleno). */
function coverage(cx, cy, r) {
  let sum = 0;
  let count = 0;
  for (let y = Math.ceil(cy - r); y <= Math.floor(cy + r); y += 1) {
    if (y < 0 || y >= H) return null;
    for (let x = Math.ceil(cx - r); x <= Math.floor(cx + r); x += 1) {
      if (x < 0 || x >= W) return null;
      if ((x - cx) ** 2 + (y - cy) ** 2 > r * r) continue;
      const white = whiteAt(x, y);
      if (!(white > 40)) return null;
      const value = 1 - gray[y * W + x] / white;
      sum += value > 0 ? value : 0;
      count += 1;
    }
  }
  if (count === 0) return null;
  const result = sum / count;
  return Number.isFinite(result) ? result : null;
}

const budget = region.ink_budget;
if (!budget) {
  console.error(`a regiao ${region.index} nao declara orcamento de tinta`);
  process.exit(2);
}
const decorativeMax = budget.decorative_max / 1000;
const corridorFloor = budget.threshold_floor / 1000;
const corridorCeiling = budget.threshold_ceiling / 1000;
/** Piso da caneta — ADR-0010, declarado antes de qualquer medicao no papel. */
const PEN_FLOOR = 0.5;

const measured = [];
for (const bubble of region.bubbles) {
  const { x, y } = project(bubble.u / 1_000_000, bubble.v / 1_000_000);
  const value = coverage(x, y, radiusPx);
  const id = `${bubble.question_id}/${bubble.option}`;
  if (value === null) {
    fail(`bolha ${id}: janela de medicao vazia, fora da imagem ou nao finita`);
    continue;
  }
  measured.push({ id, question: bubble.question_id, value });
}
if (measured.length !== region.bubbles.length) {
  fail(`medi ${measured.length} de ${region.bubbles.length} bolhas declaradas`);
}

/**
 * Marcada e a bolha mais escura da questao, e so quando ela se destaca das outras tres.
 *
 * Nao existe gabarito de quem preencheu o que: quem respondeu foi uma pessoa com caneta. O
 * criterio e a separacao — se a mais escura nao se destaca, a questao entra como ambigua e nao
 * como marcada, porque classificar no chute inventaria o resultado que a tarefa foi medir.
 */
const SEPARATION = 0.12;
const byQuestion = new Map();
for (const item of measured) {
  if (!byQuestion.has(item.question)) byQuestion.set(item.question, []);
  byQuestion.get(item.question).push(item);
}
const marked = [];
const blanks = [];
const ambiguous = [];
for (const [question, items] of byQuestion) {
  const sorted = [...items].sort((a, b) => b.value - a.value);
  if (sorted.length < 2 || sorted[0].value - sorted[1].value < SEPARATION) {
    ambiguous.push(question);
    continue;
  }
  marked.push(sorted[0]);
  blanks.push(...sorted.slice(1));
}

function stats(list) {
  if (list.length === 0) return null;
  const sorted = [...list].sort((a, b) => a.value - b.value);
  return {
    n: sorted.length,
    lowest: sorted[0],
    highest: sorted[sorted.length - 1],
    median: sorted[Math.floor(sorted.length / 2)].value,
    mean: sorted.reduce((acc, i) => acc + i.value, 0) / sorted.length,
  };
}

const pen = stats(marked);
const blank = stats(blanks);
const pct = (v) => `${(v * 100).toFixed(2)}%`;

if (jsonPath !== null) {
  // Seis casas: a cobertura vai de 0 a 1, entao seis casas guardam mais resolucao do que qualquer
  // tolerancia razoavel vai comparar, e o arquivo continua legivel por gente.
  const round = (v) => Number(v.toFixed(6));
  writeFileSync(
    jsonPath,
    `${JSON.stringify(
      {
        ferramenta: 'papel.mjs',
        imagem: imagePath.split(/[\\/]/).pop(),
        largura: W,
        altura: H,
        regiao: region.index,
        // Cantos na ordem tl, tr, bl, br — a mesma do quadrilatero da regiao.
        cantos: quad.map((p) => ({ x: round(p.x), y: round(p.y) })),
        raio_px: round(radiusPx),
        // A medicao e feita na imagem **nao** retificada, amostrando um circulo. E o que torna a
        // comparacao com a implementacao oficial uma comparacao com tolerancia, e nao igualdade.
        metodo: 'circulo na imagem original, branco local por percentil',
        bolhas: measured.map((b) => ({ id: b.id, cobertura: round(b.value) })),
      },
      null,
      1,
    )}\n`,
  );
  console.log(`medicao gravada em ${jsonPath}`);
}
const markerSide = cornerOrder.reduce((acc, c) => acc + (corners[c].w + corners[c].h) / 2, 0) / 4;

console.log(`imagem: ${W}x${H} px`);
console.log(
  `marcadores: 4 achados, lado medio ${markerSide.toFixed(1)} px, ${modulesChecked} modulos ` +
    'conferidos contra o mapa',
);
console.log(`vao dos centros: ${spanX.toFixed(1)} x ${spanY.toFixed(1)} px`);
console.log(
  `escala: ${(pxPerUmX * 1000).toFixed(3)} px/mm em x, ${(pxPerUmY * 1000).toFixed(3)} px/mm em y ` +
    `— ${(pxPerUmX * 25400).toFixed(0)} dpi efetivos se a impressao estiver em 100%`,
);
console.log(`raio de medicao: ${radiusPx.toFixed(2)} px (${(radiusUm / 1000).toFixed(2)} mm declarados)`);
console.log(`bolhas medidas: ${measured.length} de ${region.bubbles.length}`);
if (pen) {
  console.log(
    `caneta: ${pen.n} bolhas — min ${pct(pen.lowest.value)} (${pen.lowest.id}), mediana ` +
      `${pct(pen.median)}, media ${pct(pen.mean)}, max ${pct(pen.highest.value)}`,
  );
}
if (blank) {
  console.log(
    `vazias: ${blank.n} bolhas — min ${pct(blank.lowest.value)}, mediana ${pct(blank.median)}, ` +
      `media ${pct(blank.mean)}, max ${pct(blank.highest.value)} (${blank.highest.id})`,
  );
}
if (ambiguous.length) {
  console.log(`ambiguas (menos de ${pct(SEPARATION)} de separacao): ${ambiguous.join(', ')}`);
}
if (pen && blank) {
  console.log(
    `corredor observado: de ${pct(blank.highest.value)} a ${pct(pen.lowest.value)} — ` +
      `${((pen.lowest.value - blank.highest.value) * 100).toFixed(2)} pontos livres`,
  );
  console.log(`corredor declarado (ADR-0010): de ${pct(corridorFloor)} a ${pct(corridorCeiling)}`);
}

// ---------------------------------------------------------------- vereditos

const anisotropy = Math.abs(pxPerUmX - pxPerUmY) / ((pxPerUmX + pxPerUmY) / 2);
if (!Number.isFinite(anisotropy) || anisotropy > 0.02) {
  fail(`a folha saiu esticada: ${(anisotropy * 100).toFixed(2)}% de diferenca entre x e y`);
}
if (pen === null) {
  fail('nenhuma bolha marcada foi identificada — nao da para conferir o piso da caneta');
} else if (pen.lowest.value < PEN_FLOOR) {
  fail(
    `bolha ${pen.lowest.id}, preenchida a caneta, cobre ${pct(pen.lowest.value)} — abaixo do piso ` +
      `de ${pct(PEN_FLOOR)} que ADR-0010 declarou`,
  );
}
if (blank !== null && blank.highest.value > decorativeMax) {
  fail(
    `bolha vazia ${blank.highest.id} cobre ${pct(blank.highest.value)} no papel, acima do ` +
      `orcamento decorativo de ${pct(decorativeMax)}`,
  );
}
if (pen !== null && blank !== null) {
  if (blank.highest.value >= corridorFloor) {
    fail(
      `as vazias chegam a ${pct(blank.highest.value)} e invadem o corredor do limiar, que comeca ` +
        `em ${pct(corridorFloor)}`,
    );
  }
  if (pen.lowest.value <= corridorCeiling) {
    fail(
      `a caneta desce a ${pct(pen.lowest.value)} e invade o corredor do limiar, que termina em ` +
        `${pct(corridorCeiling)}`,
    );
  }
}

if (problems.length === 0) {
  console.log('\npapel: tudo dentro do declarado.');
  process.exit(0);
}
console.error(`\n${problems.length} problema(s):`);
for (const problem of problems) console.error(`  - ${problem}`);
process.exit(1);
