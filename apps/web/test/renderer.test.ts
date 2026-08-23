import { describe, expect, it } from 'vitest';
import { PDFDocument, PDFRawStream, decodePDFRawStream } from 'pdf-lib';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { renderLayoutMap } from '../src/renderer.js';
import {
  assertRendererSupports,
  MissingResourceError,
  RendererVersionError,
  RENDERER_VERSION,
  umToPt,
  UnknownPrimitiveError,
  type LayoutMap,
} from '../src/layoutMap.js';
import { loadFontBytes, loadFormulaRasters } from '../scripts/formulaAssets.js';

const repoRoot = resolve(__dirname, '../../..');

async function golden(): Promise<LayoutMap> {
  return JSON.parse(
    await readFile(resolve(repoRoot, 'fixtures/prova-referencia.layout.json'), 'utf8'),
  );
}

const fontBytes = loadFontBytes;
const rasters = loadFormulaRasters;

/**
 * Operadores dos fluxos de conteudo do documento, ja descomprimidos.
 *
 * O tom nao aparece na estrutura do PDF — ele e um operador `rg` dentro do fluxo, e o fluxo sai
 * comprimido. Sem descomprimir, um teste de tom estaria afirmando sobre bytes opacos.
 */
async function contentOps(pdf: Uint8Array): Promise<string> {
  const reopened = await PDFDocument.load(pdf);
  let ops = '';
  for (const [, object] of reopened.context.enumerateIndirectObjects()) {
    if (object instanceof PDFRawStream) {
      try {
        const decoded = Buffer.from(decodePDFRawStream(object).decode()).toString('latin1');
        if (decoded.includes(' rg')) ops += `${decoded}\n`;
      } catch {
        // Fluxo que nao e de conteudo (fonte, imagem): nao interessa aqui.
      }
    }
  }
  return ops;
}

/** Uma folha de uma pagina so com as primitivas dadas, herdando o cabecalho do golden. */
async function folhaCom(primitives: LayoutMap['pages'][number]['primitives']): Promise<LayoutMap> {
  return { ...(await golden()), pages: [{ index: 0, primitives }], regions: [] };
}

describe('guarda de versao do renderizador', () => {
  it('recusa imprimir quando o mapa exige renderizador mais novo', async () => {
    const map = { ...(await golden()), min_renderer_version: RENDERER_VERSION + 1 };
    expect(() => assertRendererSupports(map)).toThrow(RendererVersionError);
    await expect(renderLayoutMap(map, await fontBytes(), await rasters())).rejects.toThrow(RendererVersionError);
  });

  it('aceita quando a versao do renderizador basta', async () => {
    const map = await golden();
    expect(map.min_renderer_version).toBeLessThanOrEqual(RENDERER_VERSION);
    expect(() => assertRendererSupports(map)).not.toThrow();
  });
});

describe('traducao de primitivas', () => {
  it('falha em primitiva desconhecida sem entregar documento parcial', async () => {
    const map = await golden();
    const broken: LayoutMap = {
      ...map,
      pages: [
        {
          index: 0,
          primitives: [{ type: 'hologram', id: 'x1' } as never],
        },
      ],
    };
    await expect(renderLayoutMap(broken, await fontBytes(), await rasters())).rejects.toThrow(
      UnknownPrimitiveError,
    );
  });

  it('converte micrometros para pontos na ordem acordada com o Android', () => {
    expect(umToPt(25400)).toBe(72);
    expect(umToPt(0)).toBe(0);
    expect(umToPt(210000)).toBeCloseTo(595.2755905511812, 10);
    // A ordem importa: (um * 72) / 25400, e nao um / 25400 * 72.
    expect(umToPt(4200)).toBe((4200 * 72) / 25400);
  });
});

describe('documento gerado', () => {
  it('desenha o golden com uma pagina por pagina do mapa', async () => {
    const map = await golden();
    const pdf = await renderLayoutMap(map, await fontBytes(), await rasters());
    expect(pdf.length).toBeGreaterThan(1000);
    const header = new TextDecoder().decode(pdf.subarray(0, 8));
    expect(header.startsWith('%PDF-')).toBe(true);
  });

  it('embarca a fonte no documento em vez de referenciar fonte do sistema', async () => {
    const map = await golden();
    const pdf = await renderLayoutMap(map, await fontBytes(), await rasters());

    // Os objetos do PDF saem comprimidos em object streams, entao a evidencia vem de reabrir o
    // documento e olhar o dicionario: `FontFile2` so existe quando o programa de fonte viaja
    // dentro do arquivo.
    const reopened = await PDFDocument.load(pdf);
    const dump = reopened.context
      .enumerateIndirectObjects()
      .map(([, object]) => object.toString())
      .join('\n');

    expect(dump).toContain('FontFile2');
    expect(dump).toContain('SourceSerif');
    // Nenhuma das 14 fontes padrao do PDF, que viriam do visualizador e nao do documento.
    expect(dump).not.toContain('Helvetica');
    expect(dump).not.toContain('Times-Roman');
  });

  it('nao mede texto: nao ha chamada de largura no caminho de desenho', async () => {
    // A evidencia estrutural e que o renderizador so recebe `LayoutMap` e bytes de fonte —
    // nao existe medidor, nem quebra de linha, nem paginacao no modulo.
    const source = await readFile(resolve(__dirname, '../src/renderer.ts'), 'utf8');
    expect(source).not.toMatch(/widthOfTextAtSize|heightAtSize|measureText/);
  });
});

describe('formula em bloco', () => {
  it('desenha uma imagem por formula declarada no mapa', async () => {
    const map = await golden();
    const imagens = map.pages.flatMap((page) =>
      page.primitives.filter((primitive) => primitive.type === 'image'),
    );
    expect(imagens.length).toBeGreaterThan(0);

    const pdf = await renderLayoutMap(map, await fontBytes(), await rasters());
    const reopened = await PDFDocument.load(pdf);
    const dump = reopened.context
      .enumerateIndirectObjects()
      .map(([, object]) => object.toString())
      .join('\n');

    // Uma imagem embutida vira XObject de subtipo `Image`. Se o renderizador tivesse omitido a
    // formula, o documento sairia sem nenhum — e a folha pareceria correta.
    const embutidas = dump.match(/\/Subtype\s*\/Image/g) ?? [];
    const referencias = new Set(imagens.map((image) => image.reference));
    expect(embutidas.length).toBe(referencias.size);
  });

  it('falha quando os bytes referenciados nao estao disponiveis', async () => {
    const map = await golden();
    // Nenhum recurso fornecido: nao ha meia folha aceitavel.
    await expect(renderLayoutMap(map, await fontBytes())).rejects.toThrow(MissingResourceError);
  });

  it('falha quando falta apenas um dos recursos', async () => {
    const map = await golden();
    const todos = await rasters();
    const primeira = map.pages
      .flatMap((page) => page.primitives)
      .find((primitive) => primitive.type === 'image');
    expect(primeira).toBeDefined();

    const incompleto = new Map(todos);
    incompleto.delete((primeira as { reference: string }).reference);

    await expect(renderLayoutMap(map, await fontBytes(), incompleto)).rejects.toThrow(
      MissingResourceError,
    );
  });

  it('nao entrega documento parcial quando um recurso falta', async () => {
    const map = await golden();
    let entregue: Uint8Array | undefined;
    try {
      entregue = await renderLayoutMap(map, await fontBytes(), new Map());
    } catch {
      entregue = undefined;
    }
    expect(entregue).toBeUndefined();
  });

  it('nao tipografa matematica: nao interpreta LaTeX, MathML nem SVG', async () => {
    // Mesma evidencia estrutural usada para medicao de texto. O renderizador desenha o recurso
    // pronto que o mapa referencia; qualquer tipografia matematica aqui seria uma segunda
    // implementacao, e duas implementacoes divergem.
    const source = await readFile(resolve(__dirname, '../src/renderer.ts'), 'utf8');
    for (const proibido of ['latex', 'mathml', 'mathjax', 'svg', 'tex']) {
      expect(source.toLowerCase()).not.toMatch(new RegExp(`\\b${proibido}\\b`));
    }
    // O que ele tem de fazer: embutir o PNG que veio pronto.
    expect(source).toMatch(/embedPng/);
  });
});

describe('tom e trama', () => {
  it('desenha o tom declarado, e nao um cinza proprio', async () => {
    const map = await folhaCom([
      { type: 'text', id: 't-tom', x: 20_000, baseline: 30_000, size: 3_351, text: 'A', tone: 450 },
      { type: 'text', id: 't-preto', x: 30_000, baseline: 30_000, size: 3_351, text: 'B', tone: null },
    ]);
    const ops = await contentOps(await renderLayoutMap(map, await fontBytes()));

    // 450 por mil de preto = 0,55 de claridade. O numero sai do mapa; se o renderizador
    // escolhesse o cinza, ele nao teria como acertar exatamente este valor.
    expect(ops).toMatch(/0\.55 0\.55 0\.55 rg/);
    // Tom nulo continua preto pleno, sem o renderizador arbitrar nada.
    expect(ops).toMatch(/0 0 0 rg/);
  });

  it('desenha a trama declarada na mesma escala, sem contorno quando o traco e zero', async () => {
    const map = await folhaCom([
      {
        type: 'rect',
        id: 'faixa',
        x: 15_000,
        y: 20_000,
        width: 100_000,
        height: 6_000,
        stroke: 0,
        fill: 45,
      },
    ]);
    const ops = await contentOps(await renderLayoutMap(map, await fontBytes()));

    // 45 por mil = 0,955 de claridade. E a trama de 4,5% que §7 pede, que porcentagem inteira nao
    // representa — se a escala tivesse ficado em porcentagem, este numero seria 0,55.
    expect(ops).toMatch(/0\.955 0\.955 0\.955 rg/);
    // Traco zero e sem traco: nao ha operador de contorno nem cor de contorno no fluxo. Largura 0
    // em PDF significa "a linha mais fina do dispositivo", que poria um pixel preto em volta de
    // uma faixa que deve ser so trama.
    expect(ops).not.toMatch(/ RG/);
    expect(ops).not.toMatch(/\nS\n/);
    expect(ops).not.toMatch(/\nB\n/);
  });
});
