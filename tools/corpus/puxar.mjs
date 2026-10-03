#!/usr/bin/env node
import { spawnSync } from 'node:child_process';
import { mkdirSync, writeFileSync } from 'node:fs';
import { isAbsolute, join, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { conferirPasta } from './formato.mjs';

/**
 * Puxa por cabo a pasta do corpus de medicao do aparelho (`slice-5d-corpus-de-medicao`).
 *
 * Uso: `node tools/corpus/puxar.mjs <destino>` (com `ANDROID_SERIAL` se houver mais de um aparelho).
 *
 * So funciona em APK **de depuracao** (`run-as` exige app depuravel): e a unica saida, e o release nao tem a coleta.
 * Le `files/corpus` por `ls` e cada arquivo por `cat` (`exec-out`, binario seguro), sem `tar`. O destino **nao pode
 * estar dentro do repositorio**: letra de menor commitada e o erro mais caro desta mudanca, e a recusa e a primeira
 * rede (o `.gitignore` e a segunda). Confere o que puxou contra o formato.
 *
 * Saida: `0` integro; `1` a conferencia reprovou; `2` nao consegui puxar ou destino recusado.
 */

const RAIZ = fileURLToPath(new URL('../../', import.meta.url));
const PACOTE = 'com.platos.android';

export function destinoForaDoRepositorio(destino, raiz = RAIZ) {
  const rel = relative(resolve(raiz), resolve(destino));
  return rel.startsWith('..') || isAbsolute(rel);
}

/**
 * Os nomes que o `ls` do aparelho listou. `adb exec-out` **perde o status de saida**: se a pasta nao existe ou o app
 * nao e depuravel, a mensagem de erro chega em stdout como se fosse a listagem — e foi lida como nome de arquivo
 * (achado ao simular, no emulador, a pasta vazia). Nome valido nao tem espaco nem dois-pontos; qualquer linha que os
 * tenha e a resposta do aparelho dizendo que nao listou, e o script a mostra em vez de escreve-la como arquivo.
 */
export function nomesDaListagem(saida) {
  const linhas = saida
    .split(/\r?\n/)
    .map((n) => n.trim())
    .filter(Boolean);
  if (linhas.length === 0) throw new Error('a pasta files/corpus do aparelho esta vazia ou nao existe');
  if (linhas.some((n) => !/^[A-Za-z0-9._-]+$/.test(n))) {
    throw new Error(`o aparelho nao listou a pasta: ${linhas.join(' | ')}`);
  }
  return linhas;
}

function adb(args) {
  const r = spawnSync('adb', args, { maxBuffer: 256 * 1024 * 1024 });
  if (r.error) throw new Error(`nao consegui rodar o adb: ${r.error.message}`);
  if (r.status !== 0) throw new Error(`adb ${args.join(' ')}: ${r.stderr.toString().trim() || `status ${r.status}`}`);
  return r.stdout;
}

function main() {
  const destino = process.argv[2];
  if (!destino) {
    console.error('uso: node tools/corpus/puxar.mjs <destino fora do repositorio>');
    return 2;
  }
  if (!destinoForaDoRepositorio(destino)) {
    console.error(`::error::${resolve(destino)} esta dentro do repositorio; escolha uma pasta de fora`);
    return 2;
  }
  let nomes;
  try {
    nomes = nomesDaListagem(adb(['exec-out', 'run-as', PACOTE, 'ls', 'files/corpus']).toString());
  } catch (e) {
    console.error(`::error::${e.message}`);
    return 2;
  }
  mkdirSync(destino, { recursive: true });
  try {
    for (const nome of nomes) {
      writeFileSync(join(destino, nome), adb(['exec-out', 'run-as', PACOTE, 'cat', `files/corpus/${nome}`]));
    }
  } catch (e) {
    console.error(`::error::${e.message}`);
    return 2;
  }
  const { amostras, problemas } = conferirPasta(destino);
  console.log(`${nomes.length} arquivo(s) puxado(s) para ${resolve(destino)}; ${amostras} amostra(s).`);
  if (problemas.length > 0) {
    for (const p of problemas) console.error(`::error::${p}`);
    return 1;
  }
  console.log('Cada foto tem os seus dados, com as chaves do formato.');
  return 0;
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) process.exit(main());
