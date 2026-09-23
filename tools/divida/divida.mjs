import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

/**
 * O registro de divida do projeto e um so, e nenhuma linha dele vence sem reconciliacao
 * (`rigorous.md` P27; ETAPA 8 do plano de correcao).
 *
 * O registro e a tabela "Ponto de nao-retorno" do par. 16 de `ARQUITETURA-FINAL-v3.md`, e esta
 * verificacao le **a tabela real**, e nao uma copia -- pelo mesmo motivo que `RetentionDeclarationTest`
 * le o catalogo do banco: uma copia seria um segundo registro, que e o defeito que a P27 corrige. A
 * auditoria de 2026-09-18 achou o padrao que isto existe para impedir: os itens que entraram na
 * tabela avancaram e fecharam; os que ficaram so em prosa de `docs/cobertura-*.md` nao avancaram, e
 * nenhum deles foi lido na abertura da fatia seguinte.
 *
 * **O que se le de cada linha e so o comeco da celula "Fatia-limite"**, e a prosa depois dele e para
 * o leitor:
 *
 *     celula  := token [ marca ] prosa
 *     token   := `<fatia>` | `antes-de:<evento>` | `continuo`
 *     fatia   := <inteiro>[.<inteiro>][<letra>]      `5`  `2b`  `1.5`  `4a`
 *     evento  := [a-z0-9]+(-[a-z0-9]+)*              `piloto-nominal`
 *     marca   := `paga`
 *
 * Quando o item foi pago, o limite **fica** e `paga` vem ao lado: o leitor sabe qual era, e o evento
 * de uma linha paga continua referenciado -- e e isso que deixa esta verificacao recusar um evento
 * declarado que nenhuma linha usa (o erro de digitacao que calaria o evento certo).
 *
 * **A fatia corrente nao e digitada: e derivada** dos nomes das mudancas em `openspec/changes/`,
 * ativas e arquivadas (sem o prefixo `AAAA-MM-DD-`). A convencao e a do par. 2 do plano de correcao
 * -- "mudanca que nao e fatia nao carrega `slice-`" --, na forma
 * `slice-<maior>[-<menor>][<letra>]-<nome>`: `slice-1-5-math-rendering` e a 1.5,
 * `slice-4b-outbox-de-resultado` e a 4b. A corrente e a maior. O nome da mudanca **ja e** a
 * declaracao de que a fatia comecou; uma linha digitada seria um segundo registro do mesmo fato, e o
 * modo de falha dela seria o silencio -- ninguem atualiza, e isto segue verde contra a fatia errada.
 * **O limite, e ele e desta verificacao:** uma fatia proposta com nome fora da convencao nao move a
 * corrente, e nada acusa. Um nome que comeca com `slice-` e nao casa com a forma reprova com `2`.
 *
 * **Quando uma linha vence.** Uma fatia-limite `L` vence quando a corrente `C` vem **estritamente
 * depois** dela: numero maior, ou o mesmo numero com `L` tendo letra e `C` uma letra posterior. Um `L`
 * sem letra (`5`) cobre a fatia inteira (`5a`, `5b`, ...). **Nao e `C >= L`:** "fatia-limite 5" e
 * "paga dentro da 5", e com `>=` o CI ficaria vermelho durante toda a 5 -- justamente o trabalho que
 * paga a linha --, e vermelho permanente e vermelho que ninguem le. Isto dispara **na abertura da
 * fatia seguinte**, que e onde a auditoria situou a falha. As linhas com `L` igual a corrente saem a
 * parte, como "vence nesta fatia", sem mudar a saida: e a lista que o propose da fatia nomeia.
 * `antes-de:<evento>` vence quando o evento esta declarado na linha "Eventos que ja ocorreram", acima
 * da tabela, ou forcado por `--ocorrido`. `continuo` nunca vence; `paga` nunca vence.
 *
 * **O que ela NAO prova.** Que a divida foi paga: `paga` e afirmacao humana. Que um reagendamento
 * tem motivo: ela le o token, e nao a prosa. Que um evento aconteceu: so ve o que alguem declarou. E
 * a divida que nunca entrou no par. 16 continua invisivel -- e o que a P27 proibe, e o que esta
 * verificacao nao tem como achar; ela nao le `docs/cobertura-*.md` de proposito, porque a regra e
 * justamente que a prosa nao e o registro.
 *
 * Uso: node divida.mjs [--arquitetura <arquivo>] [--mudancas <dir>] [--ocorrido <evento>]...
 *
 * Saida: `0` nenhuma linha vencida; `1` alguma linha vencida sem reconciliacao, e cada uma vira uma
 * linha que a nomeia, com o token e o motivo; `2` a verificacao nao conseguiu ler o registro, a
 * fatia corrente ou um parametro -- inclusive o piso: tabela sem linhas, ou nenhuma mudanca `slice-*`.
 * Os tres parametros existem para o CI plantar defeitos **fora do checkout**: uma copia do documento
 * com uma linha a mais, um diretorio de mudancas vazio, um evento forcado.
 */

const RAIZ = fileURLToPath(new URL('../../', import.meta.url));
const TITULO_SECAO = '## 16.';
const TITULO_TABELA = '### Ponto de não-retorno';
const ROTULO_EVENTOS = '**Eventos que já ocorreram:**';
const COLUNA_RISCO = 'Risco';
const COLUNA_LIMITE = 'Fatia-limite';

const FATIA = /^(\d+)(?:\.(\d+))?([a-z])?$/;
const EVENTO = /^[a-z0-9]+(?:-[a-z0-9]+)*$/;
const NOME_DE_FATIA = /^slice-(\d+)(?:-(\d+))?([a-z])?(?:-|$)/;

/** Motivos de `exit 2`. */
const naoLidos = [];
const naoLeu = (motivo) => naoLidos.push(motivo);
const sairSeNaoLeu = () => {
  if (naoLidos.length === 0) return;
  for (const m of naoLidos) console.error(`::error::${m}`);
  process.exit(2);
};

// ---------------------------------------------------------------- parametros

let arquitetura = join(RAIZ, 'docs/architecture/ARQUITETURA-FINAL-v3.md');
let mudancas = join(RAIZ, 'openspec/changes');
const forcados = [];

const argv = process.argv.slice(2);
for (let i = 0; i < argv.length; i++) {
  const nome = argv[i];
  if (!['--arquitetura', '--mudancas', '--ocorrido'].includes(nome)) {
    // Parametro desconhecido nao e ignorado: um `--ocorrdo` digitado errado rodaria a conferencia
    // simples e passaria, e o passo do CI que o chamou leria o verde como "aceitou".
    naoLeu(`parametro desconhecido: ${nome}`);
    continue;
  }
  const valor = argv[++i];
  if (valor === undefined || valor.startsWith('--')) {
    naoLeu(`${nome} pede um valor`);
    continue;
  }
  if (nome === '--arquitetura') arquitetura = resolve(valor);
  else if (nome === '--mudancas') mudancas = resolve(valor);
  else if (!EVENTO.test(valor)) naoLeu(`--ocorrido: \`${valor}\` nao e nome de evento`);
  else forcados.push(valor);
}
sairSeNaoLeu();

// ---------------------------------------------------------------- a tabela do par. 16

const semMarcacao = (s) => s.replace(/\*\*|~~|`/g, '').replace(/\s+/g, ' ').trim();
const celulas = (linha) => {
  const t = linha.trim();
  if (!t.startsWith('|') || !t.endsWith('|')) return null;
  return t.slice(1, -1).split('|').map((c) => c.trim());
};

let fonte;
try {
  fonte = readFileSync(arquitetura, 'utf8');
} catch {
  naoLeu(`nao consegui ler ${arquitetura}`);
  sairSeNaoLeu();
}
const linhas = fonte.split(/\r?\n/);

const iSecao = linhas.findIndex((l) => l.startsWith(TITULO_SECAO));
let fimSecao = linhas.findIndex((l, i) => i > iSecao && /^## /.test(l));
if (fimSecao === -1) fimSecao = linhas.length;
const iTabela =
  iSecao === -1 ? -1 : linhas.findIndex((l, i) => i > iSecao && i < fimSecao && l.trim() === TITULO_TABELA);
if (iSecao === -1) naoLeu(`nao achei a secao \`${TITULO_SECAO}\` em ${arquitetura}`);
else if (iTabela === -1) naoLeu(`nao achei \`${TITULO_TABELA}\` dentro de \`${TITULO_SECAO}\``);
sairSeNaoLeu();

// A subsecao vai ate o proximo titulo de nivel 1 a 3.
let fim = linhas.findIndex((l, i) => i > iTabela && /^#{1,3} /.test(l));
if (fim === -1) fim = linhas.length;
const secao = linhas.slice(iTabela + 1, fim);

// A tabela e o primeiro bloco contiguo de linhas que comecam com `|`.
const iPrimeira = secao.findIndex((l) => l.trim().startsWith('|'));
const blocoTabela = [];
for (let i = iPrimeira; i !== -1 && i < secao.length && secao[i].trim().startsWith('|'); i++) {
  blocoTabela.push(secao[i]);
}
if (blocoTabela.length < 2) {
  naoLeu(`nao achei a tabela depois de \`${TITULO_TABELA}\``);
  sairSeNaoLeu();
}
const cabecalho = celulas(blocoTabela[0]) ?? [];
const separadora = celulas(blocoTabela[1]);
if (!separadora || !separadora.every((c) => /^:?-+:?$/.test(c))) {
  naoLeu('a segunda linha da tabela nao e a separadora do cabecalho');
}
const cRisco = cabecalho.indexOf(COLUNA_RISCO);
const cLimite = cabecalho.indexOf(COLUNA_LIMITE);
if (cRisco === -1 || cLimite === -1) {
  naoLeu(`o cabecalho da tabela nao tem as colunas \`${COLUNA_RISCO}\` e \`${COLUNA_LIMITE}\`: ${cabecalho.join(' | ')}`);
}
sairSeNaoLeu();

const registro = [];
for (const linha of blocoTabela.slice(2)) {
  const c = celulas(linha);
  const rotulo = c ? semMarcacao(c[cRisco] ?? '') : semMarcacao(linha).slice(0, 60);
  if (!c || c.length !== cabecalho.length) {
    naoLeu(`a linha "${rotulo}" tem ${c ? c.length : 0} colunas, e o cabecalho tem ${cabecalho.length}`);
    continue;
  }
  const inicio = c[cLimite].match(/^`([^`]*)`(?:\s*`([^`]*)`)?/);
  if (!inicio) {
    naoLeu(`a linha "${rotulo}" nao comeca com token entre crases na coluna \`${COLUNA_LIMITE}\``);
    continue;
  }
  const [, token, marca] = inicio;
  if (marca !== undefined && marca !== 'paga') {
    naoLeu(`a linha "${rotulo}" comeca com \`${token}\` \`${marca}\`, e a unica marca que existe e \`paga\``);
    continue;
  }
  const paga = marca === 'paga';
  let limite;
  const f = token.match(FATIA);
  if (f) {
    limite = { tipo: 'fatia', fatia: { maior: Number(f[1]), menor: f[2] === undefined ? 0 : Number(f[2]), letra: f[3] ?? '', texto: token } };
  } else if (token.startsWith('antes-de:') && EVENTO.test(token.slice('antes-de:'.length))) {
    limite = { tipo: 'evento', evento: token.slice('antes-de:'.length) };
  } else if (token === 'continuo') {
    limite = { tipo: 'continuo' };
    if (paga) {
      naoLeu(`a linha "${rotulo}" e \`continuo\` e \`paga\` ao mesmo tempo: risco continuo nao tem prazo para ser pago`);
      continue;
    }
  } else {
    naoLeu(`a linha "${rotulo}" tem o token \`${token}\`, fora da gramatica (fatia, antes-de:<evento> ou continuo)`);
    continue;
  }
  registro.push({ rotulo, token, paga, limite });
}
if (registro.length === 0 && naoLidos.length === 0) {
  naoLeu(`piso: a tabela de \`${TITULO_TABELA}\` nao tem nenhuma linha -- uma tabela vazia passaria em qualquer conferencia`);
}

// ---------------------------------------------------------------- os eventos declarados

const linhasDeEventos = secao.filter((l) => l.trim().startsWith(ROTULO_EVENTOS));
const declarados = [];
if (linhasDeEventos.length !== 1) {
  naoLeu(`a linha \`${ROTULO_EVENTOS}\` aparece ${linhasDeEventos.length} vezes em \`${TITULO_TABELA}\`, e tem de aparecer uma`);
} else {
  const resto = linhasDeEventos[0].trim().slice(ROTULO_EVENTOS.length);
  const codigos = [...resto.matchAll(/`([^`]*)`/g)].map((m) => m[1]);
  if (codigos.length === 0) {
    // Sem codigo nenhum, a linha tem de dizer `nenhum`: um evento escrito sem crase seria lido como
    // "zero eventos", em silencio.
    if (semMarcacao(resto).replace(/\.$/, '').toLowerCase() !== 'nenhum') {
      naoLeu(`a linha de eventos nao tem evento entre crases e nao diz "nenhum": "${semMarcacao(resto)}"`);
    }
  } else {
    for (const e of codigos) {
      if (EVENTO.test(e)) declarados.push(e);
      else naoLeu(`a linha de eventos declara \`${e}\`, que nao e nome de evento`);
    }
  }
}
const usados = new Set(registro.filter((r) => r.limite.tipo === 'evento').map((r) => r.limite.evento));
for (const [origem, lista] of [['declarado na linha de eventos', declarados], ['forcado por --ocorrido', forcados]]) {
  for (const e of lista) {
    if (!usados.has(e)) naoLeu(`evento \`${e}\` ${origem}, que nenhuma linha usa -- erro de digitacao cala o evento certo`);
  }
}
const ocorridos = new Set([...declarados, ...forcados]);

// ---------------------------------------------------------------- a fatia corrente

const lerNomes = (dir) => readdirSync(dir).filter((n) => statSync(join(dir, n)).isDirectory());
const nomesDeMudanca = [];
if (!existsSync(mudancas) || !statSync(mudancas).isDirectory()) {
  naoLeu(`nao achei o diretorio de mudancas ${mudancas}`);
} else {
  for (const n of lerNomes(mudancas)) {
    if (n !== 'archive') nomesDeMudanca.push({ nome: n, onde: 'ativa' });
  }
  const arquivo = join(mudancas, 'archive');
  if (existsSync(arquivo)) {
    for (const n of lerNomes(arquivo)) {
      nomesDeMudanca.push({ nome: n.replace(/^\d{4}-\d{2}-\d{2}-/, ''), onde: 'arquivada' });
    }
  }
}
const fatias = [];
for (const m of nomesDeMudanca) {
  if (!m.nome.startsWith('slice-')) continue;
  const f = m.nome.match(NOME_DE_FATIA);
  if (!f) {
    naoLeu(`a mudanca ${m.nome} comeca com \`slice-\` e nao segue \`slice-<maior>[-<menor>][<letra>]-<nome>\``);
    continue;
  }
  fatias.push({ ...m, maior: Number(f[1]), menor: f[2] === undefined ? 0 : Number(f[2]), letra: f[3] ?? '' });
}
if (fatias.length === 0 && naoLidos.length === 0) {
  naoLeu(`piso: nenhuma mudanca \`slice-*\` em ${mudancas} -- sem fatia corrente, nenhuma linha venceria nunca`);
}
sairSeNaoLeu();

const numero = (a, b) => a.maior - b.maior || a.menor - b.menor;
const ordem = (a, b) => numero(a, b) || (a.letra < b.letra ? -1 : a.letra > b.letra ? 1 : 0);
const corrente = fatias.reduce((max, f) => (ordem(f, max) > 0 ? f : max));
const textoDaFatia = (f) => `${f.maior}${f.menor ? `.${f.menor}` : ''}${f.letra}`;
const deOnde = fatias.filter((f) => ordem(f, corrente) === 0).map((f) => `${f.nome} (${f.onde})`);

/** `L` vence quando a corrente vem estritamente depois dela; `L` sem letra cobre a fatia inteira. */
const vencida = (L) => {
  const n = numero(corrente, L);
  if (n !== 0) return n > 0;
  return L.letra !== '' && corrente.letra > L.letra;
};
const nestaFatia = (L) => numero(corrente, L) === 0 && (L.letra === '' || L.letra === corrente.letra);

// ---------------------------------------------------------------- o veredito, linha por linha

console.log(`fatia corrente: ${textoDaFatia(corrente)}, de ${deOnde.join(', ')}`);
console.log(`eventos declarados: ${declarados.length ? declarados.join(', ') : 'nenhum'}`);
if (forcados.length) console.log(`eventos forcados por --ocorrido: ${forcados.join(', ')}`);
console.log('');

const vencidas = [];
const vencemNestaFatia = [];
for (const r of registro) {
  let estado;
  if (r.paga) {
    estado = 'paga';
  } else if (r.limite.tipo === 'continuo') {
    estado = 'continuo';
  } else if (r.limite.tipo === 'evento') {
    if (ocorridos.has(r.limite.evento)) {
      estado = 'VENCIDA';
      vencidas.push(`${r.rotulo} (\`${r.token}\`): o evento ${r.limite.evento} ja ocorreu`);
    } else {
      estado = 'aguarda evento';
    }
  } else if (vencida(r.limite.fatia)) {
    estado = 'VENCIDA';
    vencidas.push(
      `${r.rotulo} (\`${r.token}\`): a fatia ${r.token} ja passou, e a corrente e ${textoDaFatia(corrente)}`,
    );
  } else {
    estado = 'em dia';
    if (nestaFatia(r.limite.fatia)) vencemNestaFatia.push(`${r.rotulo} (\`${r.token}\`)`);
  }
  console.log(`  ${estado.padEnd(15)} \`${r.token}\`${r.paga ? ' `paga`' : ''}  ${r.rotulo}`);
}

console.log('');
console.log(`vence nesta fatia (${textoDaFatia(corrente)}): ${vencemNestaFatia.length ? '' : 'nenhuma'}`);
for (const v of vencemNestaFatia) console.log(`  ${v}`);

if (vencidas.length > 0) {
  console.log('');
  for (const v of vencidas) console.error(`::error::linha vencida sem reconciliacao: ${v}`);
  process.exit(1);
}
console.log(`\nnenhuma linha vencida: ${registro.length} linhas lidas`);
