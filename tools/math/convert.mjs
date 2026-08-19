import { mathjax } from 'mathjax-full/js/mathjax.js';
import { TeX } from 'mathjax-full/js/input/tex.js';
import { MathML } from 'mathjax-full/js/input/mathml.js';
import { SVG } from 'mathjax-full/js/output/svg.js';
import { liteAdaptor } from 'mathjax-full/js/adaptors/liteAdaptor.js';
import { RegisterHTMLHandler } from 'mathjax-full/js/handlers/html.js';
// Import por efeito colateral: registra as configuracoes de pacote. Ver [TEX_PACKAGES].
import 'mathjax-full/js/input/tex/AllPackages.js';
import { Resvg } from '@resvg/resvg-js';
import { createHash } from 'node:crypto';

/**
 * Conversao de formula: LaTeX ou MathML -> SVG -> raster de impressao (D-1.5.2, D-1.5.8).
 *
 * Ferramenta de build, nao runtime de servidor: roda offline alimentando a fixture, como
 * `tools/parity` e o render do PDF ja fazem. O Ktor nao passa a depender de Node.
 *
 * O que sai daqui e uma caixa de dimensao conhecida. O Layout Engine nunca abre o SVG nem o PNG —
 * ele le largura e altura ja resolvidas do manifesto (D-1.5.3).
 */

/**
 * Corpo do texto da prova, em micrometros.
 *
 * Espelha `TextStyle.BODY_SIZE` do KMP. A formula e composta no mesmo corpo do texto ao redor, se
 * nao a matematica sairia com peso optico diferente do enunciado.
 *
 * Duplicar a constante e o risco desta linha, entao ela nao fica so aqui: o manifesto declara o
 * valor usado, e `MathManifestTest` afirma no KMP que o declarado bate com `TextStyle.BODY_SIZE`.
 * Mexer num lado sem mexer no outro derruba o teste em vez de deslocar a folha em silencio.
 */
export const BODY_SIZE_UM = 3351;

/** Resolucao do raster de impressao (D-1.5.1): o que impressora domestica e escolar entrega. */
export const RASTER_DPI = 600;

const UM_PER_INCH = 25400;

/**
 * Pacotes TeX ativos.
 *
 * `base` e `ams` cobrem tudo que D-1.5.6 poe dentro do escopo — fracao, raiz, potencia, subscrito,
 * trigonometria, logaritmo, vetor, somatorio, `pmatrix`, `vmatrix` e `cases`. Nada alem disso
 * entra: quanto menor a lista, mais estreito o que a conversao aceita, e o limite de D-1.5.6 e
 * barreira executavel em vez de intencao escrita.
 *
 * Dois pacotes ficam de fora por serem ativamente perigosos aqui, e nao apenas desnecessarios:
 *
 * - `noundefined` desenha comando desconhecido como texto vermelho em vez de falhar;
 * - `noerrors` devolve o TeX cru no lugar da formula que nao compilou.
 *
 * Os dois produzem exatamente o resultado que a spec proibe — folha silenciosamente degradada — e,
 * pior, apagam o `merror` que [toSvg] usa para detectar a falha. Com eles ligados, `\newcommand`
 * passava por esta conversao sem um ruido.
 *
 * Importar `AllPackages` **registra** as configuracoes; e a lista abaixo que escolhe entre as
 * registradas. Sem o import, `packages: ['ams']` seleciona algo que nao existe, o `ams` some
 * calado e `\begin{vmatrix}` volta como "Unknown environment".
 */
const TEX_PACKAGES = ['base', 'ams'];

/** A formula usa notacao que a conversao nao suporta (D-1.5.6). */
export class UnsupportedFormulaError extends Error {}

/**
 * Construcoes recusadas antes de o MathJax ver a entrada.
 *
 * Sao os tres itens que D-1.5.6 poe fora do escopo. A recusa e explicita em vez de delegada ao
 * MathJax porque `\newcommand` sem o pacote correspondente vira "comando desconhecido" — erro
 * verdadeiro, mensagem errada, e quem le nao descobre que o limite e deliberado.
 */
const REFUSED = [
  [/\\(re)?newcommand\b/, 'macro customizada (`\\newcommand`)'],
  [/\\(g|x)?def\b/, 'macro customizada (`\\def`)'],
  [/\\let\b/, 'macro customizada (`\\let`)'],
  [/\\usepackage\b/, 'pacote arbitrario (`\\usepackage`)'],
  [/\\require\b/, 'pacote arbitrario (`\\require`)'],
  [/\\begin\s*\{\s*tikzpicture\s*\}/, 'grafico por TikZ'],
  [/\\tikz\b/, 'grafico por TikZ'],
  [/\\usetikzlibrary\b/, 'grafico por TikZ'],
];

export function assertSupportedSource(id, source) {
  for (const [pattern, what] of REFUSED) {
    if (pattern.test(source)) {
      throw new UnsupportedFormulaError(
        `formula \`${id}\` usa ${what}, fora do escopo da conversao (D-1.5.6); ` +
          'grafico entra pelo fluxo de assets nativos, nao pela compilacao de formula',
      );
    }
  }
}

const adaptor = liteAdaptor();
RegisterHTMLHandler(adaptor);

/**
 * Um documento novo por conversao, e nao dois documentos reaproveitados.
 *
 * Nao e zelo: o registro de macros do MathJax 3 e global, e o ultimo `InputJax` construido vence.
 * Com um documento TeX e um MathML vivos ao mesmo tempo, o TeX perdia a configuracao de pacotes e
 * `\begin{vmatrix}` voltava como "Unknown environment" — matriz e sistema linear, que sao
 * justamente as estruturas que D-1.5.6 poe na fixture para exercitar a grade.
 *
 * O custo e desprezivel para uma dezena de formulas, e o ganho e que a conversao nao depende da
 * ordem em que foi chamada. Uma conversao sensivel a ordem produziria fixture diferente conforme o
 * caminho do build — exatamente o tipo de variacao que a tarefa 2.4 existe para eliminar.
 */
const INPUT_JAX = {
  // `fontCache: 'none'` inlina cada glifo como `<path>`. E o que torna o SVG autossuficiente: sem
  // `<use>` para um cache externo e, sobretudo, sem nenhuma fonte no caminho da rasterizacao — que
  // e a condicao de o raster sair igual aqui e no runner do CI (D-1.5.8).
  latex: () => new TeX({ packages: TEX_PACKAGES }),
  mathml: () => new MathML(),
};

function documentFor(notation) {
  const inputJax = INPUT_JAX[notation];
  if (!inputJax) return null;
  return mathjax.document('', {
    InputJax: inputJax(),
    OutputJax: new SVG({ fontCache: 'none' }),
  });
}

/**
 * Converte a formula em SVG determinístico.
 *
 * `display: true` porque esta fatia so aceita formula em bloco; formula em linha e a fatia 1.6, e
 * exige `InlineBox` com `baseline_offset`, que nao existe aqui.
 */
export function toSvg({ id, notation, source }) {
  const document = documentFor(notation);
  if (!document) {
    throw new UnsupportedFormulaError(
      `formula \`${id}\` declara notacao \`${notation}\`; aceitas: latex, mathml`,
    );
  }
  assertSupportedSource(id, source);

  const node = document.convert(source, {
    display: true,
    // Valores fixos: o MathJax usaria o contexto do navegador para descobri-los, e o contexto e
    // exatamente o que nao pode participar de uma conversao que precisa ser reproduzivel.
    em: 16,
    ex: 8,
    containerWidth: 1_000_000,
  });
  const html = adaptor.outerHTML(node);

  // O MathJax nao lanca em erro de sintaxe: ele devolve um `merror` com o texto do problema
  // desenhado na folha. Uma prova impressa com "Undefined control sequence" no lugar da formula e
  // o pior resultado possivel, entao isso vira falha aqui.
  if (html.includes('data-mml-node="merror"')) {
    const detail = /data-mml-node="merror"[^>]*>.*?title="([^"]*)"/.exec(html);
    throw new UnsupportedFormulaError(
      `formula \`${id}\` nao foi compilada pelo MathJax` +
        (detail ? `: ${detail[1]}` : '; a saida traz um bloco de erro'),
    );
  }

  const start = html.indexOf('<svg');
  const end = html.lastIndexOf('</svg>');
  if (start < 0 || end < 0) {
    throw new UnsupportedFormulaError(`formula \`${id}\` nao produziu SVG`);
  }

  return (
    html
      .slice(start, end + '</svg>'.length)
      // `currentColor` depende do contexto de estilo, que nao existe fora do navegador. Fixar em
      // preto tira a ultima dependencia de ambiente do arquivo.
      .replace(/currentColor/g, '#000000')
      // `vertical-align` so faz sentido para formula em linha, e e justamente o que esta fatia nao
      // desenha. Sai para que o SVG nao carregue geometria que ninguem le.
      .replace(/ style="vertical-align: [^"]*"/, '')
  );
}

/** Largura e altura do SVG em ems, lidas do `viewBox`. */
function emBoxOf(id, svg) {
  const match = /viewBox="([^"]+)"/.exec(svg);
  if (!match) {
    throw new UnsupportedFormulaError(`formula \`${id}\` produziu SVG sem viewBox`);
  }
  const box = match[1].trim().split(/\s+/).map(Number);
  if (box.length !== 4 || !box.every(Number.isFinite)) {
    throw new UnsupportedFormulaError(`formula \`${id}\` tem viewBox ilegivel: "${match[1]}"`);
  }
  // O MathJax emite o viewBox em milesimos de em.
  return { width: box[2] / 1000, height: box[3] / 1000 };
}

/**
 * Rasteriza o SVG a 600 dpi e devolve os bytes com as dimensoes que a fixture declara.
 *
 * **Quem manda na dimensao e o pixel.** O alvo em micrometros sai do `viewBox`, vira contagem de
 * pixels, e a dimensao declarada volta a ser derivada dessa contagem. A ordem importa: se o
 * micrometro mandasse, a caixa do mapa nao cairia na grade de pixels do PNG e cada renderizador
 * teria de reescalar por conta propria — que e o que a spec de `print` proibe.
 */
export function toRaster(id, svg) {
  const em = emBoxOf(id, svg);
  const targetWidthUm = em.width * BODY_SIZE_UM;
  const widthPx = Math.round((targetWidthUm * RASTER_DPI) / UM_PER_INCH);
  if (!Number.isFinite(widthPx) || widthPx < 1) {
    throw new UnsupportedFormulaError(
      `formula \`${id}\` produziu largura invalida: ${targetWidthUm} um`,
    );
  }

  const resvg = new Resvg(svg, {
    fitTo: { mode: 'width', value: widthPx },
    background: 'white',
  });
  const rendered = resvg.render();
  const png = rendered.asPng();

  // Dimensoes vindas do que foi de fato rasterizado, e nao do que se pediu: a altura sai da
  // proporcao que o proprio resvg aplicou, entao a caixa declarada e exatamente o retangulo do
  // PNG. Sem isso, largura e altura arredondariam em separado e a formula sairia esticada.
  const pixelsToUm = (px) => Math.round((px * UM_PER_INCH) / RASTER_DPI);

  return {
    png,
    widthPx: rendered.width,
    heightPx: rendered.height,
    widthUm: pixelsToUm(rendered.width),
    heightUm: pixelsToUm(rendered.height),
    sha256: createHash('sha256').update(png).digest('hex'),
  };
}

/** Conversao completa de uma formula: SVG, raster e dimensoes declaradas. */
export function convert(entry) {
  const svg = toSvg(entry);
  const raster = toRaster(entry.id, svg);
  return { id: entry.id, notation: entry.notation, source: entry.source, svg, ...raster };
}
