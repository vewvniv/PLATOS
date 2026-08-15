import { PDFDocument, rgb, type PDFFont, type PDFPage } from 'pdf-lib';
import fontkit from '@pdf-lib/fontkit';
import {
  assertRendererSupports,
  umToPt,
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

function grayOf(percent: number) {
  const level = 1 - percent / 100;
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
        borderColor: BLACK,
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
        color: BLACK,
      });
      return;

    case 'aruco':
      drawModuleGrid(page, map, primitive.x, primitive.y, primitive.module, primitive.modules);
      return;

    case 'qr':
      drawModuleGrid(page, map, primitive.x, primitive.y, primitive.module, primitive.modules);
      return;

    case 'image':
      throw new UnknownPrimitiveError(
        `primitiva \`image\` (${primitive.id}) nao e desenhada nesta fatia`,
      );

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
 */
export async function renderLayoutMap(
  map: LayoutMap,
  fontBytes: Uint8Array,
): Promise<Uint8Array> {
  assertRendererSupports(map);

  const document = await PDFDocument.create();
  document.registerFontkit(fontkit);
  const font = await document.embedFont(fontBytes, { subset: false });

  // A pagina e declarada em pontos inteiros: o `PdfDocument` do Android so aceita inteiro, e uma
  // caixa de pagina diferente entre as duas plataformas seria uma divergencia gratuita. O conteudo
  // continua posicionado pela conversao exata, entao as medidas impressas nao mudam.
  const widthPt = Math.round(umToPt(map.page_width));
  const heightPt = Math.round(umToPt(map.page_height));

  const ordered = [...map.pages].sort((a, b) => a.index - b.index);
  for (const source of ordered) {
    const page = document.addPage([widthPt, heightPt]);
    for (const primitive of source.primitives) {
      drawPrimitive(page, map, font, primitive);
    }
  }

  return document.save();
}
