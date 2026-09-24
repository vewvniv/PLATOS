import { afterEach, describe, expect, it } from 'vitest';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { loadPublishedLayout } from '../scripts/examPackage.js';
import type { DrawQr, LayoutMap } from '../src/layoutMap.js';

const repoRoot = resolve(__dirname, '../../..');

/**
 * A folha do aluno, pelas duas implementacoes da regra (`slice-5a-regiao-discursiva`, decisao 9).
 *
 * `fixtures/prova-discursiva.aluno.layout.json` e a folha de `tok-a` derivada pelo Kotlin
 * (`ExamPackage.folhaDaAtribuicao`, gravada pelo `GoldenWriterTest`). Aqui a mesma folha e derivada
 * do mesmo pacote pelo espelho TypeScript, pelo caminho que o `render-fixture.ts` usa, e as duas sao
 * comparadas byte a byte. E esta a conferencia que o espelho nao tinha: a paridade compara
 * centroides, e um QR com o payload de outra regiao nao move centroide nenhum.
 */
describe('a folha do aluno', () => {
  const pacoteAntes = process.env.PLATOS_PACKAGE;
  afterEach(() => {
    if (pacoteAntes === undefined) delete process.env.PLATOS_PACKAGE;
    else process.env.PLATOS_PACKAGE = pacoteAntes;
  });

  it('o espelho TypeScript produz a mesma folha que o Kotlin, byte a byte', async () => {
    process.env.PLATOS_PACKAGE = 'fixtures/prova-discursiva.package.json';
    const doTypeScript = await loadPublishedLayout('v1', 'tok-a');
    const doKotlin = (
      await readFile(resolve(repoRoot, 'fixtures/prova-discursiva.aluno.layout.json'), 'utf8')
    ).trim();

    // Guarda de vacuidade: a folha tem de ter as tres regioes, e o QR de cada uma tem de trazer o
    // aluno — senao "igual" estaria comparando duas folhas da variante, sem identidade nenhuma.
    const mapa: LayoutMap = JSON.parse(doKotlin);
    expect(mapa.regions.map((r) => r.index)).toEqual([0, 1, 2]);
    const qrs = mapa.pages.flatMap((p) => p.primitives).filter((p): p is DrawQr => p.type === 'qr');
    expect(qrs).toHaveLength(3);
    for (const qr of qrs) expect(qr.payload.split('.')[1]).toBe('tok-a');

    expect(JSON.stringify(doTypeScript)).toBe(doKotlin);
  });

  it('cada regiao da folha do aluno leva o QR que diz a regiao dela', async () => {
    process.env.PLATOS_PACKAGE = 'fixtures/prova-discursiva.package.json';
    const folha = await loadPublishedLayout('v1', 'tok-a');
    for (const regiao of folha.regions) {
      const qr = folha.pages
        .find((p) => p.index === regiao.page)!
        .primitives.find((p): p is DrawQr => p.type === 'qr' && p.id === regiao.qr_id);
      expect(qr, `regiao ${regiao.index} sem o QR ${regiao.qr_id}`).toBeDefined();
      // {exam_short_id}.{student_token}.{variant}.{region_idx}.{crc}
      expect(Number(qr!.payload.split('.')[3])).toBe(regiao.index);
    }
  });
});
