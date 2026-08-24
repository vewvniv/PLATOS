import * as mupdf from 'mupdf';
import { readFileSync, writeFileSync } from 'node:fs';

/**
 * Extrai a regiao escaneavel de uma digitalizacao **ja retificada**, em cinza cru (PGM P5).
 *
 * Existe para produzir a fixture da fatia 3a. O nucleo puro do OMR recebe um buffer retificado —
 * §13 poe a retificacao no OpenCV, que so existe no Android —, entao o teste de host precisa de um
 * buffer que ja passou por ela. Esta ferramenta produz esse buffer **uma vez**, fora do caminho de
 * producao.
 *
 * Isso e uma limitacao honesta e vale dizer em voz alta: a fixture nao prova o retificador de
 * producao. Quem prova ele e a tarefa 7.4, no emulador, contra o buffer sintetico cuja resposta e
 * conhecida por construcao. Aqui o que se prova e a **medicao**, sobre papel real.
 *
 * PGM P5 porque o cabecalho e texto e o corpo e byte cru: Kotlin le sem decodificador de imagem, e
 * o arquivo e identico nos tres alvos. Um JPEG exigiria decodificador, e decodificador de JPEG nao
 * e determinístico entre plataformas.
 *
 * Uso: node recorte.mjs <digitalizacao.jpg> <layout.json> <saida.pgm> [px-por-mm]
 */

const [, , imagePath, mapPath, outPath, scaleArg] = process.argv;
if (!imagePath || !mapPath || !outPath) {
  console.error('uso: node recorte.mjs <digitalizacao.jpg> <layout.json> <saida.pgm> [px-por-mm]');
  process.exit(2);
}

/**
 * Resolucao do buffer retificado, em pixels por milimetro.
 *
 * 10 e quase exatamente a resolucao nativa da digitalizacao da 2b (9,92 px/mm), entao a
 * reamostragem e minima — o que importa porque cobertura e medicao de valor de pixel. Descer para
 * 6 economizaria metade do arquivo e mudaria o numero medido.
 */
const PX_PER_MM = scaleArg ? Number(scaleArg) : 10;
if (!Number.isFinite(PX_PER_MM) || PX_PER_MM <= 0) {
  console.error(`px-por-mm invalido: ${scaleArg}`);
  process.exit(2);
}

const doc = mupdf.Document.openDocument(readFileSync(imagePath), 'image/jpeg');
const pixmap = doc
  .loadPage(0)
  .toPixmap(mupdf.Matrix.scale(96 / 72, 96 / 72), mupdf.ColorSpace.DeviceGray, false, true);
const W = pixmap.getWidth();
const H = pixmap.getHeight();
const nc = pixmap.getNumberOfComponents();
const raw = new Uint8Array(pixmap.getPixels());
const gray = new Uint8Array(W * H);
for (let i = 0; i < W * H; i += 1) gray[i] = raw[i * nc];

// ---------------------------------------------------------------- os quatro marcadores

const MARKER_CUT = 110;
const mask = new Uint8Array(W * H);
for (let i = 0; i < W * H; i += 1) mask[i] = gray[i] < MARKER_CUT ? 1 : 0;

const seen = new Uint8Array(W * H);
const stack = new Int32Array(W * H);
const blobs = [];
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
  const w = x1 - x0 + 1;
  const h = y1 - y0 + 1;
  if (n > 3000 && Math.abs(w - h) < 0.12 * Math.max(w, h) && n / (w * h) > 0.5) {
    blobs.push({ n, cx: (x0 + x1) / 2, cy: (y0 + y1) / 2 });
  }
}
blobs.sort((a, b) => b.n - a.n);
if (blobs.length < 4) {
  console.error(`achei ${blobs.length} marcadores, e nao 4`);
  process.exit(1);
}
const found = blobs.slice(0, 4).sort((a, b) => a.cy - b.cy);
const top = found.slice(0, 2).sort((a, b) => a.cx - b.cx);
const bottom = found.slice(2).sort((a, b) => a.cx - b.cx);
const quad = [top[0], top[1], bottom[0], bottom[1]];

// ---------------------------------------------------------------- homografia

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
      const f = A[row][col] / A[col][col];
      for (let k = col; k < n; k += 1) A[row][k] -= f * A[col][k];
      b[row] -= f * b[col];
    }
  }
  return b.map((v, i) => v / A[i][i]);
}

const unit = [[0, 0], [1, 0], [0, 1], [1, 1]];
const A = [];
const rhs = [];
for (let i = 0; i < 4; i += 1) {
  const [u, v] = unit[i];
  const { cx, cy } = quad[i];
  A.push([u, v, 1, 0, 0, 0, -u * cx, -v * cx]);
  rhs.push(cx);
  A.push([0, 0, 0, u, v, 1, -u * cy, -v * cy]);
  rhs.push(cy);
}
const h = solve(A, rhs);
if (h === null) {
  console.error('marcadores degenerados');
  process.exit(1);
}
const project = (u, v) => {
  const w = h[6] * u + h[7] * v + 1;
  return { x: (h[0] * u + h[1] * v + h[2]) / w, y: (h[3] * u + h[4] * v + h[5]) / w };
};

// ---------------------------------------------------------------- reamostragem

const map = JSON.parse(readFileSync(mapPath, 'utf8'));
const region = map.regions[0];
const outW = Math.round((region.quad_width / 1000) * PX_PER_MM);
const outH = Math.round((region.quad_height / 1000) * PX_PER_MM);

/**
 * Media de area, e nao bilinear pontual.
 *
 * Bilinear amostra quatro vizinhos de **um** ponto; se a imagem de origem for mais densa que a de
 * destino, tudo que cai entre as amostras e descartado. Numa bolha de 4 mm com traco de caneta, o
 * que se descarta e tinta. A media de area integra a celula inteira, entao a cobertura sobrevive a
 * reamostragem — que e a unica coisa que esta fixture precisa preservar.
 */
const supersample = Math.max(2, Math.ceil((W / outW) * 1.5));
const out = new Uint8Array(outW * outH);
for (let oy = 0; oy < outH; oy += 1) {
  for (let ox = 0; ox < outW; ox += 1) {
    let sum = 0;
    let count = 0;
    for (let sy = 0; sy < supersample; sy += 1) {
      for (let sx = 0; sx < supersample; sx += 1) {
        const u = (ox + (sx + 0.5) / supersample) / outW;
        const v = (oy + (sy + 0.5) / supersample) / outH;
        const { x, y } = project(u, v);
        const px = Math.round(x);
        const py = Math.round(y);
        if (px < 0 || py < 0 || px >= W || py >= H) continue;
        sum += gray[py * W + px];
        count += 1;
      }
    }
    out[oy * outW + ox] = count === 0 ? 255 : Math.round(sum / count);
  }
}

const header = Buffer.from(`P5\n${outW} ${outH}\n255\n`, 'ascii');
writeFileSync(outPath, Buffer.concat([header, Buffer.from(out)]));
console.log(
  `recorte: ${outW}x${outH} px a ${PX_PER_MM} px/mm ` +
    `(regiao ${region.quad_width / 1000}x${region.quad_height / 1000} mm), ` +
    `supersample ${supersample}x${supersample} -> ${outPath}`,
);
