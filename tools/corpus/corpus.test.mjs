import assert from 'node:assert/strict';
import { mkdtempSync, rmSync, writeFileSync, readFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { test } from 'node:test';
import { fileURLToPath } from 'node:url';
import { EXEMPLO, chavesDoFormato, conferirPasta, contarValidas } from './formato.mjs';
import { destinoForaDoRepositorio, nomesDaListagem } from './puxar.mjs';

const RAIZ = fileURLToPath(new URL('../../', import.meta.url));
const ID_A = '3f2c9a52-1111-4000-8000-0000000000d1';
const ID_B = '3f2c9a52-2222-4000-8000-0000000000d2';

function pasta() {
  return mkdtempSync(join(tmpdir(), 'corpus-'));
}

function amostra(dir, id, { foto = Buffer.from([1, 2, 3]), dados = readFileSync(EXEMPLO, 'utf8') } = {}) {
  if (foto !== null) writeFileSync(join(dir, `${id}.png`), foto);
  if (dados !== null) writeFileSync(join(dir, `${id}.json`), dados);
}

function com(fn) {
  const dir = pasta();
  try {
    return fn(dir);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
}

test('as chaves do formato saem do literal do repositorio, na ordem em que o aparelho as escreve', () => {
  assert.deepEqual(chavesDoFormato(), ['versao', 'pontos', 'maximo', 'pacote', 'item', 'referencia', 'descartar']);
});

test('uma pasta integra e aprovada, e conta as amostras', () =>
  com((dir) => {
    amostra(dir, ID_A);
    amostra(dir, ID_B);
    assert.deepEqual(conferirPasta(dir), { amostras: 2, problemas: [] });
  }));

test('foto sem arquivo de dados reprova nomeando a amostra', () =>
  com((dir) => {
    amostra(dir, ID_A, { dados: null });
    const { problemas } = conferirPasta(dir);
    assert.deepEqual(problemas, [`${ID_A}: falta o arquivo de dados`]);
  }));

test('arquivo de dados sem foto reprova nomeando a amostra', () =>
  com((dir) => {
    amostra(dir, ID_A, { foto: null });
    assert.deepEqual(conferirPasta(dir).problemas, [`${ID_A}: falta a foto`]);
  }));

test('chave a mais ou a menos reprova, e a mensagem diz o que difere', () =>
  com((dir) => {
    amostra(dir, ID_A, { dados: '{"versao":1,"pontos":"1.50"}' });
    const { problemas } = conferirPasta(dir);
    assert.equal(problemas.length, 1);
    assert.match(problemas[0], /chaves .* diferem do formato/);
  }));

test('a ordem das chaves nao importa (o mantenedor edita o arquivo)', () =>
  com((dir) => {
    const reordenado = JSON.stringify(Object.fromEntries(Object.entries(JSON.parse(readFileSync(EXEMPLO, 'utf8'))).reverse()));
    amostra(dir, ID_A, { dados: reordenado });
    assert.deepEqual(conferirPasta(dir).problemas, []);
  }));

test('foto vazia reprova', () =>
  com((dir) => {
    amostra(dir, ID_A, { foto: Buffer.alloc(0) });
    assert.deepEqual(conferirPasta(dir).problemas, [`${ID_A}: a foto esta vazia`]);
  }));

test('pasta sem nenhuma amostra reprova (piso)', () =>
  com((dir) => {
    assert.deepEqual(conferirPasta(dir).problemas, ['a pasta nao tem nenhuma amostra']);
  }));

test('pasta que nao existe reprova em vez de passar', () => {
  const { problemas } = conferirPasta(join(tmpdir(), 'nao-existe-corpus-xyz'));
  assert.equal(problemas.length, 1);
  assert.match(problemas[0], /nao consegui ler/);
});

test('arquivo estranho e nome fora do formato reprovam', () =>
  com((dir) => {
    amostra(dir, ID_A);
    writeFileSync(join(dir, 'anotacoes.txt'), 'x');
    writeFileSync(join(dir, 'curto.png'), 'x');
    const { problemas } = conferirPasta(dir);
    assert.ok(problemas.includes('arquivo estranho: anotacoes.txt'), problemas.join('\n'));
    assert.ok(problemas.some((p) => p.includes('curto.png')), problemas.join('\n'));
  }));

test('so e valida a amostra com referencia preenchida e sem descarte (ADR-0022, item 1)', () =>
  com((dir) => {
    const exemplo = JSON.parse(readFileSync(EXEMPLO, 'utf8'));
    const com_ = (extra) => JSON.stringify({ ...exemplo, ...extra });
    amostra(dir, '3f2c9a52-1111-4000-8000-0000000000a1', { dados: com_({ referencia: 'o texto certo' }) });
    amostra(dir, '3f2c9a52-1111-4000-8000-0000000000a2', { dados: com_({ referencia: 'outro texto' }) });
    amostra(dir, '3f2c9a52-1111-4000-8000-0000000000a3', { dados: com_({ referencia: 'tinha nome', descartar: true }) });
    amostra(dir, '3f2c9a52-1111-4000-8000-0000000000a4', { dados: com_({ referencia: '' }) });
    amostra(dir, '3f2c9a52-1111-4000-8000-0000000000a5');
    assert.deepEqual(contarValidas(dir), { amostras: 5, validas: 2, descartadas: 1, semReferencia: 2 });
  }));

test('um destino dentro do repositorio e recusado, o de fora e aceito', () => {
  assert.equal(destinoForaDoRepositorio(join(RAIZ, 'corpus-de-medicao')), false);
  assert.equal(destinoForaDoRepositorio(RAIZ), false);
  assert.equal(destinoForaDoRepositorio(join(RAIZ, '..', 'corpus-de-medicao-fora')), true);
});

test('a listagem do aparelho que e mensagem de erro nao vira nome de arquivo', () => {
  // `adb exec-out` perde o status de saida: a mensagem de erro do `ls` chega como se fosse a listagem.
  assert.throws(() => nomesDaListagem('ls: files/corpus: No such file or directory\n'), /nao listou a pasta.*No such file/);
  assert.throws(() => nomesDaListagem('run-as: package not debuggable: com.platos.android\n'), /nao listou a pasta/);
  assert.throws(() => nomesDaListagem(''), /vazia ou nao existe/);
  assert.deepEqual(nomesDaListagem(`${ID_A}.json\r\n${ID_A}.png\r\n`), [`${ID_A}.json`, `${ID_A}.png`]);
});
