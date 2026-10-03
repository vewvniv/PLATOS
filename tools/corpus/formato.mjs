import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

/**
 * A conferencia de uma pasta de amostras do corpus de medicao (`slice-5d-corpus-de-medicao`).
 *
 * **O formato nao e digitado aqui.** As chaves saem de `fixtures/corpus/amostra-exemplo.json`, o literal que o teste da
 * coleta Kotlin compara com a serializacao (`ColetaDoCorpusEmArquivoTest`): os dois lados prendem o mesmo arquivo, e
 * mudar um campo num sem mudar o literal reprova (P28). A ordem das chaves nao conta; o conjunto, sim.
 */

const RAIZ = fileURLToPath(new URL('../../', import.meta.url));
export const EXEMPLO = join(RAIZ, 'fixtures', 'corpus', 'amostra-exemplo.json');

const NOME = /^(.+)\.(png|json)$/;
const ID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;

export function chavesDoFormato(arquivo = EXEMPLO) {
  return Object.keys(JSON.parse(readFileSync(arquivo, 'utf8')));
}

const igualAoFormato = (a, b) => JSON.stringify([...a].sort()) === JSON.stringify([...b].sort());

/**
 * Cada foto tem o seu arquivo de dados e vice-versa, com as chaves do formato, e a pasta tem ao menos uma amostra
 * (piso, P13: uma pasta vazia passaria em qualquer conferencia). Devolve os problemas, e nao lanca.
 */
export function conferirPasta(dir, chaves = chavesDoFormato()) {
  let nomes;
  try {
    nomes = readdirSync(dir);
  } catch {
    return { amostras: 0, problemas: [`nao consegui ler ${dir}`] };
  }
  const problemas = [];
  const porId = new Map();
  for (const nome of nomes) {
    const m = NOME.exec(nome);
    if (!m) {
      problemas.push(`arquivo estranho: ${nome}`);
      continue;
    }
    const [, id, extensao] = m;
    if (!ID.test(id)) problemas.push(`nome fora do formato de identificador: ${nome}`);
    porId.set(id, { ...(porId.get(id) ?? {}), [extensao]: true });
  }
  if (porId.size === 0) problemas.push('a pasta nao tem nenhuma amostra');
  for (const [id, partes] of [...porId].sort()) {
    if (!partes.png) problemas.push(`${id}: falta a foto`);
    else if (statSync(join(dir, `${id}.png`)).size === 0) problemas.push(`${id}: a foto esta vazia`);
    if (!partes.json) {
      problemas.push(`${id}: falta o arquivo de dados`);
      continue;
    }
    let dados;
    try {
      dados = JSON.parse(readFileSync(join(dir, `${id}.json`), 'utf8'));
    } catch {
      problemas.push(`${id}: o arquivo de dados nao e JSON`);
      continue;
    }
    const doArquivo = Object.keys(dados);
    if (!igualAoFormato(doArquivo, chaves)) {
      problemas.push(`${id}: chaves ${doArquivo.join(',')} diferem do formato ${chaves.join(',')}`);
    }
  }
  return { amostras: porId.size, problemas };
}

/**
 * A grandeza do ADR-0022 (item 1): amostra **valida** e a de `referencia` preenchida (nao vazia) e `descartar` falso.
 * `descartadas` e `semReferencia` sao disjuntas: a descartada nao conta como "sem referencia" (nao falta transcrever
 * o que nao entra). Arquivo de dados ilegivel conta como sem referencia; `conferirPasta` e quem o acusa.
 */
export function contarValidas(dir) {
  let validas = 0;
  let descartadas = 0;
  let semReferencia = 0;
  let amostras = 0;
  for (const nome of readdirSync(dir)) {
    const m = /^(.+)\.json$/.exec(nome);
    if (!m) continue;
    amostras++;
    let dados = {};
    try {
      dados = JSON.parse(readFileSync(join(dir, nome), 'utf8'));
    } catch {
      /* conta como sem referencia */
    }
    if (dados.descartar === true) descartadas++;
    else if (typeof dados.referencia === 'string' && dados.referencia.trim() !== '') validas++;
    else semReferencia++;
  }
  return { amostras, validas, descartadas, semReferencia };
}

// `node tools/corpus/formato.mjs <pasta>`: confere o formato e conta as validas (o piso do ADR-0022 e 60).
if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const pasta = process.argv[2];
  if (!pasta) {
    console.error('uso: node tools/corpus/formato.mjs <pasta com as amostras>');
    process.exit(2);
  }
  const { problemas } = conferirPasta(pasta);
  for (const p of problemas) console.error(`::error::${p}`);
  console.log(JSON.stringify(contarValidas(pasta)));
  process.exit(problemas.length > 0 ? 1 : 0);
}
