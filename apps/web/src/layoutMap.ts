/**
 * Espelho em TypeScript do `LayoutMap` produzido pelo KMP.
 *
 * Este arquivo descreve o contrato, nao o recalcula. Nenhuma funcao aqui mede texto, quebra linha
 * ou decide posicao: o renderizador so traduz o que o mapa manda (§6).
 */

export interface DrawRect {
  type: 'rect';
  id: string;
  x: number;
  y: number;
  width: number;
  height: number;
  stroke: number;
  /** Preenchimento em permilagem de preto, 0 a 1000. Nulo e sem preenchimento (D-2b.1). */
  fill: number | null;
}

export interface DrawCircle {
  type: 'circle';
  id: string;
  center_x: number;
  center_y: number;
  diameter: number;
  stroke: number;
}

export interface DrawText {
  type: 'text';
  id: string;
  x: number;
  baseline: number;
  size: number;
  text: string;
  /** Tom em permilagem de preto, 0 a 1000. Nulo e preto pleno (D-2b.1). */
  tone: number | null;
}

export interface DrawImage {
  type: 'image';
  id: string;
  x: number;
  y: number;
  width: number;
  height: number;
  reference: string;
}

export interface DrawAruco {
  type: 'aruco';
  id: string;
  marker_id: number;
  x: number;
  y: number;
  side: number;
  module: number;
  modules: string[];
}

export interface DrawQr {
  type: 'qr';
  id: string;
  x: number;
  y: number;
  side: number;
  module: number;
  payload: string;
  modules: string[];
}

export type Primitive =
  | DrawRect
  | DrawCircle
  | DrawText
  | DrawImage
  | DrawAruco
  | DrawQr;

export interface Bubble {
  question_id: string;
  option: string;
  u: number;
  v: number;
}

export interface NormalizedRect {
  u: number;
  v: number;
  u_size: number;
  v_size: number;
}

export interface ScannableRegion {
  index: number;
  kind: string;
  page: number;
  quad_x: number;
  quad_y: number;
  quad_width: number;
  quad_height: number;
  marker_ids: number[];
  qr: NormalizedRect;
  bubbles: Bubble[];
  /** O `id` da primitiva `qr` desta regiao, na pagina dela: a ligacao regiao -> QR e declarada. */
  qr_id: string;
  /** A questao da regiao discursiva; nulo no gabarito e na folha de teste. */
  question_id: string | null;
  /** A area de resposta da regiao discursiva; nula no gabarito e na folha de teste. */
  answer_area: NormalizedRect | null;
}

export interface Page {
  index: number;
  primitives: Primitive[];
}

export interface LayoutMap {
  layout_engine_version: number;
  min_renderer_version: number;
  exam_id: string;
  page_width: number;
  page_height: number;
  font_sha256: string;
  pages: Page[];
  regions: ScannableRegion[];
}

/** Versao deste renderizador (D24). */
export const RENDERER_VERSION = 1;

export class RendererVersionError extends Error {}

export class UnknownPrimitiveError extends Error {}

/** O mapa referencia um recurso cujos bytes nao foram fornecidos. */
export class MissingResourceError extends Error {}

/**
 * Recusa desenhar um mapa que exige renderizador mais novo (D24).
 *
 * Falhar aqui e o comportamento correto: uma folha desenhada por um cliente desatualizado pode sair
 * com geometria que o OMR nao le, e isso so seria descoberto depois de impressa.
 */
export function assertRendererSupports(map: LayoutMap): void {
  if (map.min_renderer_version > RENDERER_VERSION) {
    throw new RendererVersionError(
      `mapa exige renderizador versao ${map.min_renderer_version}, este e o ${RENDERER_VERSION}; ` +
        'atualize o aplicativo para imprimir esta prova',
    );
  }
}

/**
 * Converte micrometros para pontos PostScript.
 *
 * A ordem das operacoes e parte do contrato: o renderizador Android faz exatamente a mesma conta,
 * na mesma ordem, em IEEE 754 de 64 bits. Trocar por `um / 25400 * 72` mudaria o ultimo bit em
 * alguns valores e a paridade passaria a acusar diferenca onde nao ha nenhuma.
 */
export function umToPt(um: number): number {
  return (um * 72) / 25400;
}
