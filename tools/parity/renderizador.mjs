import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';

/**
 * A versao do renderizador concorda nos tres registros que nao se conhecem (D24, ETAPA 7.1).
 *
 * O numero aparece em tres lugares, e nenhum deles verifica os outros:
 *
 * - `LayoutMap.MIN_RENDERER_VERSION`, no dominio KMP — o que a publicacao **escreve** no mapa;
 * - `RendererContract.RENDERER_VERSION`, no Android — o que o renderizador e o **gate de captura**
 *   leem (`PreparoDaProva.kt`);
 * - `RENDERER_VERSION`, em `apps/web/src/layoutMap.ts` — o que o renderizador web le.
 *
 * **A direcao silenciosa e a pior, e e a que nenhum teste pega.** Um renderizador que ganha
 * capacidade e sobe a propria constante sem o mapa subir `MIN` faz clientes antigos desenharem
 * mapas novos — que e o que D24 existe para impedir. Medido em 2026-09-23: com o web ou o Android
 * em `2` e o resto em `1`, as suites que leem o registro passam inteiras, 14 de 14 e 308 de 308
 * (`docs/cobertura-versao-do-renderizador-conferida.md`, Parte I). A direcao oposta, `MIN` acima de
 * um renderizador, ja derruba teste — o pino de `LayoutEngineTest` e o `<=` de
 * `RendererContractTest` —, e por isso **a propriedade aqui e igualdade, e nao `MIN <= versao`**:
 * `<=` e o que as suites ja afirmam, e aceita por definicao a direcao silenciosa. Android contra web
 * nao tem ordem nenhuma: sao duas implementacoes do mesmo conjunto de capacidades.
 *
 * **O valor tem de ser literal inteiro, e um registro que referencia outro reprova.** Escrever
 * `RENDERER_VERSION = LayoutMap.MIN_RENDERER_VERSION` no Android compila e faz os dois nunca mais
 * divergirem — e e o erro: `MIN` e o que o mapa **exige**, `RENDERER_VERSION` e o que o renderizador
 * **sabe fazer**, e amarrar o segundo ao primeiro faria toda subida de `MIN` declarar, sozinha, uma
 * capacidade que ninguem implementou. Aceitar a referencia faria esta verificacao comparar um valor
 * com ele mesmo, verde para sempre. Numa conferencia cruzada a independencia dos lados e o valor
 * dela — o argumento com que o ADR-0015, decisao 3, manteve o `check` da migration fora do Kotlin.
 *
 * **Os registros sao tres e nomeados, e nao uma varredura.** Nenhum outro numero de versao entra
 * aqui — nem `ENGINE_VERSION`, nem o `min_renderer_version` das fixtures, nem os literais dos
 * testes. A forma que esta verificacao nao sabe ler reprova com `2`, nomeando o registro, o arquivo e
 * o motivo: pular o que nao entende seria passar em silencio justamente na edicao que mudou a forma.
 *
 * **O que ela NAO prova:** que os tres numeros estao certos. Ela nao sabe se o motor passou a emitir
 * algo que exige renderizador novo — quem sobe `MIN` e quem muda o motor —, nem se um renderizador
 * desenha o que a versao dele declara, que e da paridade e dos testes de renderizacao. Tres registros
 * subindo juntos para um numero errado passam.
 *
 * Uso: node renderizador.mjs [--divergir dominio|android|web]
 *
 * Saida: `0` os tres concordam; `1` algum par discorda, e cada par vira uma linha que nomeia os dois
 * registros e os dois valores; `2` a verificacao nao conseguiu ler um registro, ou recebeu parametro
 * invalido. `--divergir` soma 1 ao valor lido do registro dado — a direcao silenciosa, um registro
 * que subiu sozinho — e existe para o CI provar, registro por registro, que a comparacao continua
 * incluindo os tres.
 */

const RAIZ = fileURLToPath(new URL('../../', import.meta.url));

// Os tres registros, na ordem em que os pares sao comparados e ditos.
const REGISTROS = [
  {
    chave: 'dominio',
    rotulo: 'LayoutMap.MIN_RENDERER_VERSION',
    papel: 'o que a publicacao escreve no mapa',
    arquivo: 'packages/domain/src/commonMain/kotlin/com/platos/domain/layout/LayoutMap.kt',
    nome: 'MIN_RENDERER_VERSION',
    declaracao: /^[ \t]*const val MIN_RENDERER_VERSION(?:[ \t]*:[ \t]*Int)?[ \t]*=(.*)$/gm,
  },
  {
    chave: 'android',
    rotulo: 'RendererContract.RENDERER_VERSION',
    papel: 'o que o renderizador e o gate de captura do aparelho leem',
    arquivo: 'apps/android/src/main/kotlin/com/platos/android/render/RendererContract.kt',
    nome: 'RENDERER_VERSION',
    declaracao: /^[ \t]*const val RENDERER_VERSION(?:[ \t]*:[ \t]*Int)?[ \t]*=(.*)$/gm,
  },
  {
    chave: 'web',
    rotulo: 'RENDERER_VERSION de layoutMap.ts',
    papel: 'o que o renderizador web le',
    arquivo: 'apps/web/src/layoutMap.ts',
    nome: 'RENDERER_VERSION',
    declaracao: /^export const RENDERER_VERSION(?:[ \t]*:[ \t]*number)?[ \t]*=(.*)$/gm,
  },
];
const CHAVES = REGISTROS.map((r) => r.chave);

// ---------------------------------------------------------------- parametros

const argv = process.argv.slice(2);
let divergir = null;
for (let i = 0; i < argv.length; i++) {
  if (argv[i] === '--divergir') {
    divergir = argv[++i];
    if (!CHAVES.includes(divergir)) {
      console.error(`::error::--divergir pede uma das chaves: ${CHAVES.join(', ')}`);
      process.exit(2);
    }
  } else {
    // Parametro desconhecido nao e ignorado: um `--divergi` digitado errado rodaria a conferencia
    // simples e passaria, e o passo do CI que o chamou leria o verde como "aceitou".
    console.error(`::error::parametro desconhecido: ${argv[i]}`);
    process.exit(2);
  }
}

// ---------------------------------------------------------------- leitura

/** Motivos de `exit 2`: um registro que a verificacao nao conseguiu ler. */
const naoLidos = [];
const lidos = [];

for (const r of REGISTROS) {
  const naoLeu = (motivo) =>
    naoLidos.push(`nao consegui ler o registro ${r.chave} (${r.rotulo}) em ${r.arquivo}: ${motivo}`);

  let fonte;
  try {
    fonte = readFileSync(join(RAIZ, r.arquivo), 'utf8');
  } catch {
    naoLeu('o arquivo nao existe');
    continue;
  }

  const declaracoes = [...fonte.matchAll(r.declaracao)];
  if (declaracoes.length === 0) {
    naoLeu(`zero declaracoes de \`${r.nome}\` — a constante mudou de forma ou saiu daqui`);
    continue;
  }
  if (declaracoes.length > 1) {
    naoLeu(`${declaracoes.length} declaracoes de \`${r.nome}\` — nao sei qual vale`);
    continue;
  }

  // O comentario de fim de linha nao faz parte do valor, nem o `;` do TypeScript.
  const valor = declaracoes[0][1].replace(/\/\/.*$/, '').trim().replace(/;$/, '').trim();
  if (!/^\d+$/.test(valor)) {
    naoLeu(
      `o valor \`${valor}\` nao e literal inteiro — um registro que referencia outro seria ` +
        'comparado com ele mesmo (design da versao-do-renderizador-conferida, decisao 3)',
    );
    continue;
  }
  lidos.push({ ...r, valor: Number(valor) });
}

if (naoLidos.length > 0) {
  for (const m of naoLidos) console.error(`::error::${m}`);
  process.exit(2);
}

// ---------------------------------------------------------------- o que foi lido

for (const r of lidos) {
  console.log(`${r.chave.padEnd(8)} ${r.rotulo} = ${r.valor}  (${r.papel})`);
}
if (divergir !== null) {
  const r = lidos.find((l) => l.chave === divergir);
  r.valor += 1;
  console.log(`\n--divergir ${divergir}: ${r.rotulo} forcado a ${r.valor}, como se tivesse subido sozinho`);
}

// ---------------------------------------------------------------- a comparacao, par a par

// Pares, e nao "o registro que destoa": com tres registros nao ha maioria que esteja certa — se o
// web subiu sozinho, pode ser que ele esteja certo e os outros dois atrasados. A verificacao nao
// escolhe; ela diz quais dois discordam.
const discordancias = [];
for (let i = 0; i < lidos.length; i++) {
  for (let j = i + 1; j < lidos.length; j++) {
    const [x, y] = [lidos[i], lidos[j]];
    if (x.valor !== y.valor) {
      discordancias.push(
        `os registros ${x.chave} e ${y.chave} discordam: ${x.chave} diz ${x.valor}, ${y.chave} diz ${y.valor}`,
      );
    }
  }
}

if (discordancias.length > 0) {
  for (const d of discordancias) console.error(`::error::${d}`);
  process.exit(1);
}

console.log(`\nos tres registros concordam: versao ${lidos[0].valor}`);
