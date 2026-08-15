import { describe, expect, it } from 'vitest';
import { PDFDocument } from 'pdf-lib';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { renderLayoutMap } from '../src/renderer.js';
import {
  assertRendererSupports,
  RendererVersionError,
  RENDERER_VERSION,
  umToPt,
  UnknownPrimitiveError,
  type LayoutMap,
} from '../src/layoutMap.js';

const repoRoot = resolve(__dirname, '../../..');

async function golden(): Promise<LayoutMap> {
  return JSON.parse(
    await readFile(resolve(repoRoot, 'fixtures/prova-referencia.layout.json'), 'utf8'),
  );
}

async function fontBytes(): Promise<Uint8Array> {
  return new Uint8Array(
    await readFile(resolve(repoRoot, 'packages/domain/fonts/SourceSerif4-Regular.ttf')),
  );
}

describe('guarda de versao do renderizador', () => {
  it('recusa imprimir quando o mapa exige renderizador mais novo', async () => {
    const map = { ...(await golden()), min_renderer_version: RENDERER_VERSION + 1 };
    expect(() => assertRendererSupports(map)).toThrow(RendererVersionError);
    await expect(renderLayoutMap(map, await fontBytes())).rejects.toThrow(RendererVersionError);
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
    await expect(renderLayoutMap(broken, await fontBytes())).rejects.toThrow(
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
    const pdf = await renderLayoutMap(map, await fontBytes());
    expect(pdf.length).toBeGreaterThan(1000);
    const header = new TextDecoder().decode(pdf.subarray(0, 8));
    expect(header.startsWith('%PDF-')).toBe(true);
  });

  it('embarca a fonte no documento em vez de referenciar fonte do sistema', async () => {
    const map = await golden();
    const pdf = await renderLayoutMap(map, await fontBytes());

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
