import { PDFDocument, rgb, type PDFFont, type PDFImage, type PDFPage } from 'pdf-lib';
import fontkit from '@pdf-lib/fontkit';
import {
  assertRendererSupports,
  umToPt,
  MissingResourceError,
  UnknownPrimitiveError,
  type LayoutMap,
  type Primitive,
} from './layoutMap.js';

/**
 * Renderizador web: primitiva do `LayoutMap` -> API do `pdf-lib`.
 *
 * Nao ha calculo de geometria aqui, so traducao de unidade e inversao do eixo vertical. Toda
 * decisao de posicao ja veio pronta do KMP.
 */

/** O PDF tem origem no canto inferior esquerdo; o `LayoutMap` usa o canto superior esquerdo. */
function flipY(map: LayoutMap, um: number): number {
  return umToPt(map.page_height - um);
}

const BLACK = rgb(0, 0, 0);

/** Tom e trama chegam em permilagem de preto, 0 a 1000 (D-2b.1). */
function grayOf(permille: number) {
  const level = 1 - permille / 1000;
  return rgb(level, level, level);
}

function drawModuleGrid(
  page: PDFPage,
  map: LayoutMap,
  x: number,
  y: number,
  moduleUm: number,
  modules: string[],
): void {
  // Um retangulo por modulo preto. O padrao ja veio resolvido no mapa (D-1.10): o renderizador
  // nao consulta dicionario ArUco nem codifica QR.
  const side = umToPt(moduleUm);
  modules.forEach((row, rowIndex) => {
    for (let column = 0; column < row.length; column += 1) {
      if (row[column] !== '1') continue;
      page.drawRectangle({
        x: umToPt(x + column * moduleUm),
        y: flipY(map, y + (rowIndex + 1) * moduleUm),
        width: side,
        height: side,
        color: BLACK,
      });
    }
  });
}

function drawPrimitive(
  page: PDFPage,
  map: LayoutMap,
  font: PDFFont,
  images: Map<string, PDFImage>,
  primitive: Primitive,
): void {
  switch (primitive.type) {
    case 'rect':
      page.drawRectangle({
        x: umToPt(primitive.x),
        y: flipY(map, primitive.y + primitive.height),
        width: umToPt(primitive.width),
        height: umToPt(primitive.height),
        borderWidth: umToPt(primitive.stroke),
        // Traco zero e **sem traco**, e nao traco fino: largura 0 em PDF significa "a linha mais
        // fina que o dispositivo consegue", que a 600 dpi vira um pixel de contorno em volta de
        // uma faixa que deveria ser so trama. O Android tem a mesma armadilha com `strokeWidth`.
        borderColor: primitive.stroke === 0 ? undefined : BLACK,
        color: primitive.fill === null ? undefined : grayOf(primitive.fill),
      });
      return;

    case 'circle':
      page.drawCircle({
        x: umToPt(primitive.center_x),
        y: flipY(map, primitive.center_y),
        size: umToPt(primitive.diameter / 2),
        borderWidth: umToPt(primitive.stroke),
        borderColor: BLACK,
      });
      return;

    case 'text':
      page.drawText(primitive.text, {
        x: umToPt(primitive.x),
        y: flipY(map, primitive.baseline),
        size: umToPt(primitive.size),
        font,
        // Tom nulo e preto pleno. Quem escolhe e o mapa: o renderizador nao arbitra cinza.
        color: primitive.tone === null ? BLACK : grayOf(primitive.tone),
      });
      return;

    case 'line':
      // Sem arremate alem das extremidades: `LineCapStyle.Butt` vale 0, o `pdf-lib` so emite o
      // operador de arremate quando ele e verdadeiro, e o estado inicial do PDF ja e esse. Nenhum
      // outro arremate e pedido aqui — com o redondo, a pauta passaria meio traco de cada ponta.
      page.drawLine({
        start: { x: umToPt(primitive.x1), y: flipY(map, primitive.y1) },
        end: { x: umToPt(primitive.x2), y: flipY(map, primitive.y2) },
        thickness: umToPt(primitive.stroke),
        // Tom nulo e preto pleno; a pauta chega cinza, e quem escolhe o cinza e o mapa.
        color: primitive.tone === null ? BLACK : grayOf(primitive.tone),
      });
      return;

    case 'aruco':
      drawModuleGrid(page, map, primitive.x, primitive.y, primitive.module, primitive.modules);
      return;

    case 'qr':
      drawModuleGrid(page, map, primitive.x, primitive.y, primitive.module, primitive.modules);
      return;

    case 'image': {
      const image = images.get(primitive.reference);
      if (image === undefined) {
        // Sem os bytes nao ha meia folha aceitavel: uma prova impressa sem a formula que o
        // enunciado menciona parece correta e nao da para responder.
        throw new MissingResourceError(
          `imagem \`${primitive.id}\` referencia o recurso \`${primitive.reference}\`, ` +
            'que nao foi fornecido; nenhum documento parcial e entregue',
        );
      }
      // Sem reamostrar e sem reescalar por conta propria: a caixa vem do mapa e o raster foi
      // gerado exatamente nela (D-1.5.1). O `pdf-lib` embute o PNG como esta e so o posiciona.
      page.drawImage(image, {
        x: umToPt(primitive.x),
        y: flipY(map, primitive.y + primitive.height),
        width: umToPt(primitive.width),
        height: umToPt(primitive.height),
      });
      return;
    }

    default: {
      const unknown = primitive as { type?: string; id?: string };
      throw new UnknownPrimitiveError(
        `primitiva desconhecida \`${unknown.type}\` (${unknown.id}); nenhum documento parcial e entregue`,
      );
    }
  }
}

/**
 * Desenha o mapa e devolve o PDF.
 *
 * [fontBytes] sao os bytes do TTF embarcado no KMP. A fonte acompanha o documento — nenhuma fonte
 * do sistema e referenciada (D36).
 *
 * [imageBytes] mapeia a referencia declarada no `LayoutMap` para os bytes do raster. Os **mesmos**
 * bytes vao para o renderizador Android (D-1.5.5): e disso que a paridade da formula depende, e e
 * por isso que a resolucao da referencia e responsabilidade de quem chama, e nao de cada
 * renderizador procurar o arquivo do seu jeito.
 */
export async function renderLayoutMap(
  map: LayoutMap,
  fontBytes: Uint8Array,
  imageBytes: ReadonlyMap<string, Uint8Array> = new Map(),
): Promise<Uint8Array> {
  assertRendererSupports(map);

  const document = await PDFDocument.create();
  document.registerFontkit(fontkit);
  const font = await document.embedFont(fontBytes, { subset: false });

  // Embute uma vez por referencia, e nao por ocorrencia: o mesmo raster usado em duas questoes
  // vira um objeto so no PDF.
  const images = new Map<string, PDFImage>();
  for (const [reference, bytes] of imageBytes) {
    images.set(reference, await document.embedPng(bytes));
  }

  // A pagina e declarada em pontos inteiros: o `PdfDocument` do Android so aceita inteiro, e uma
  // caixa de pagina diferente entre as duas plataformas seria uma divergencia gratuita. O conteudo
  // continua posicionado pela conversao exata, entao as medidas impressas nao mudam.
  const widthPt = Math.round(umToPt(map.page_width));
  const heightPt = Math.round(umToPt(map.page_height));

  const ordered = [...map.pages].sort((a, b) => a.index - b.index);
  for (const source of ordered) {
    const page = document.addPage([widthPt, heightPt]);
    for (const primitive of source.primitives) {
      drawPrimitive(page, map, font, images, primitive);
    }
  }

  return document.save();
}
