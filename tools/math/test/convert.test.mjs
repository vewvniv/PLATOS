import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import {
  convert,
  toSvg,
  toRaster,
  UnsupportedFormulaError,
  BODY_SIZE_UM,
  RASTER_DPI,
} from '../convert.mjs';

const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), '../../..');
const { formulas } = JSON.parse(
  readFileSync(resolve(repoRoot, 'fixtures/formulas.json'), 'utf8'),
);

/** Tarefa 2.4: a conversao precisa ser reproduzivel, senao a fixture muda sozinha entre builds. */
test('a mesma formula produz sempre os mesmos bytes e as mesmas dimensoes', () => {
  for (const entry of formulas) {
    const first = convert(entry);
    const second = convert(entry);
    assert.equal(first.svg, second.svg, `SVG de \`${entry.id}\` divergiu entre execucoes`);
    assert.ok(
      first.png.equals(second.png),
      `raster de \`${entry.id}\` divergiu entre execucoes`,
    );
    assert.equal(first.widthUm, second.widthUm);
    assert.equal(first.heightUm, second.heightUm);
  }
});

/**
 * A conversao nao pode depender da ordem em que foi chamada.
 *
 * Isto ja aconteceu: com um documento TeX e um MathML reaproveitados, o registro global de macros
 * do MathJax fazia o TeX perder os pacotes e `\begin{vmatrix}` voltar como "Unknown environment".
 */
test('converter na ordem inversa da o mesmo resultado', () => {
  const direta = formulas.map((entry) => convert(entry));
  const inversa = [...formulas].reverse().map((entry) => convert(entry));
  for (const esperado of direta) {
    const obtido = inversa.find((f) => f.id === esperado.id);
    assert.ok(obtido.png.equals(esperado.png), `\`${esperado.id}\` depende da ordem de conversao`);
  }
});

test('toda formula da fixture converte, nas duas notacoes', () => {
  const notacoes = new Set(formulas.map((f) => f.notation));
  assert.ok(notacoes.has('latex'), 'a fixture nao exercita LaTeX');
  assert.ok(notacoes.has('mathml'), 'a fixture nao exercita MathML');
  for (const entry of formulas) {
    const svg = toSvg(entry);
    assert.match(svg, /^<svg /, `\`${entry.id}\` nao produziu SVG`);
    assert.doesNotMatch(svg, /currentColor/, `\`${entry.id}\` deixou cor dependente de contexto`);
  }
});

/** Tarefa 5.3: o limite de D-1.5.6 precisa ser barreira executavel. */
const FORA_DO_ESCOPO = [
  ['macro customizada', '\\newcommand{\\dobro}[1]{2#1}\\dobro{x}'],
  ['macro por def', '\\def\\dobro#1{2#1}\\dobro{x}'],
  ['macro por let', '\\let\\a\\alpha \\a'],
  ['pacote arbitrario', '\\usepackage{amssymb} x'],
  ['pacote por require', '\\require{color}\\textcolor{red}{x}'],
  ['grafico TikZ', '\\begin{tikzpicture}\\draw (0,0) -- (1,1);\\end{tikzpicture}'],
  ['comando TikZ', '\\tikz \\draw (0,0) circle (1);'],
];

for (const [nome, source] of FORA_DO_ESCOPO) {
  test(`${nome} e recusada com erro identificavel`, () => {
    assert.throws(
      () => toSvg({ id: 'f-teste', notation: 'latex', source }),
      (error) => {
        assert.ok(
          error instanceof UnsupportedFormulaError,
          `\`${nome}\` falhou com ${error.constructor.name}, esperado UnsupportedFormulaError`,
        );
        assert.match(error.message, /f-teste/, 'a mensagem nao identifica a formula');
        return true;
      },
      `\`${nome}\` passou pela conversao`,
    );
  });
}

/**
 * O MathJax nao lanca em erro de sintaxe: ele desenha o erro na folha.
 *
 * Esta e a verificacao que ja pegou defeito real durante a implementacao — com `noundefined` e
 * `noerrors` ligados, comando indefinido virava texto impresso e a conversao passava calada.
 */
test('comando indefinido nao vira texto impresso', () => {
  assert.throws(
    () => toSvg({ id: 'f-teste', notation: 'latex', source: '\\comandoQueNaoExiste{x}' }),
    UnsupportedFormulaError,
  );
});

test('notacao desconhecida e recusada', () => {
  assert.throws(
    () => toSvg({ id: 'f-teste', notation: 'asciimath', source: 'x' }),
    UnsupportedFormulaError,
  );
});

test('SVG sem viewBox nao produz dimensao silenciosa', () => {
  assert.throws(
    () => toRaster('f-teste', '<svg xmlns="http://www.w3.org/2000/svg"></svg>'),
    UnsupportedFormulaError,
  );
  assert.throws(
    () => toRaster('f-teste', '<svg viewBox="0 0 nao numero"></svg>'),
    UnsupportedFormulaError,
  );
});

/**
 * A caixa declarada precisa ser exatamente o retangulo do PNG.
 *
 * Se divergisse, cada renderizador teria de reescalar por conta propria para caber — que e o que a
 * spec de `print` proibe, e que faria a paridade acusar diferenca de biblioteca.
 */
test('a dimensao declarada e a do raster a 600 dpi', () => {
  for (const entry of formulas) {
    const { widthPx, heightPx, widthUm, heightUm } = convert(entry);
    assert.equal(widthUm, Math.round((widthPx * 25400) / RASTER_DPI), `largura de \`${entry.id}\``);
    assert.equal(heightUm, Math.round((heightPx * 25400) / RASTER_DPI), `altura de \`${entry.id}\``);
    assert.ok(widthUm > 0 && heightUm > 0, `\`${entry.id}\` saiu sem area`);
    assert.ok(Number.isInteger(widthUm) && Number.isInteger(heightUm));
  }
});

/** O manifesto versionado precisa ser o que a conversao produz hoje. */
test('o manifesto versionado bate com a conversao', () => {
  const manifest = JSON.parse(
    readFileSync(resolve(repoRoot, 'fixtures/formulas.manifest.json'), 'utf8'),
  );
  assert.equal(manifest.body_size_um, BODY_SIZE_UM);
  assert.equal(manifest.raster_dpi, RASTER_DPI);
  assert.equal(manifest.formulas.length, formulas.length);

  for (const entry of formulas) {
    const declared = manifest.formulas.find((f) => f.id === entry.id);
    assert.ok(declared, `\`${entry.id}\` nao esta no manifesto`);
    const actual = convert(entry);
    assert.equal(declared.width_um, actual.widthUm, `largura de \`${entry.id}\``);
    assert.equal(declared.height_um, actual.heightUm, `altura de \`${entry.id}\``);
    assert.equal(declared.sha256, actual.sha256, `raster de \`${entry.id}\``);

    const versioned = readFileSync(resolve(repoRoot, 'fixtures', declared.raster));
    assert.ok(versioned.equals(actual.png), `\`${declared.raster}\` difere da conversao`);
  }
});

test('o deslocamento de linha de base sai do viewBox e cabe na caixa', () => {
  // O `viewBox` do MathJax tem origem na linha de base: `minY` e negativo para o que sobe, e
  // `minY + altura` e o que desce. O dominio exige 0 <= deslocamento <= altura, e uma formula
  // fora dessa faixa nao teria como se alinhar ao texto — ela seria recusada na entrada.
  for (const entry of formulas) {
    const { heightUm, baselineOffsetUm } = convert(entry);
    assert.ok(
      Number.isInteger(baselineOffsetUm) && baselineOffsetUm >= 0 && baselineOffsetUm <= heightUm,
      `${entry.id}: deslocamento ${baselineOffsetUm} um fora da caixa de ${heightUm} um`,
    );
  }
});

test('o deslocamento acompanha a tipografia da formula', () => {
  // Oracle independente do codigo: o que desce abaixo da linha de base e propriedade da NOTACAO,
  // e da para conferir sem abrir o conversor. Uma raiz quase nao desce; uma fracao desce cerca de
  // um terco; um somatorio, centrado no eixo matematico, desce quase metade.
  //
  // Sem esta afirmacao, um deslocamento constante — ou zero — passaria pelo teste de faixa acima
  // e so apareceria na folha impressa, como formula flutuando fora da linha.
  const fracao = (id) => {
    const { heightUm, baselineOffsetUm } = convert(formulas.find((f) => f.id === id));
    return baselineOffsetUm / heightUm;
  };

  assert.ok(fracao('f-raizes') < 0.15, 'a raiz quase nao desce abaixo da linha de base');
  assert.ok(fracao('f-fracoes') > 0.25, 'a fracao precisa descer');
  assert.ok(fracao('f-somatorio') > 0.4, 'o somatorio e centrado no eixo e desce quase metade');
  assert.ok(
    fracao('f-raizes') < fracao('f-fracoes') && fracao('f-fracoes') < fracao('f-somatorio'),
    'a ordem raiz < fracao < somatorio e o que mostra que o valor acompanha a notacao',
  );
});
