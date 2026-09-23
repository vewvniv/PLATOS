import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join, relative, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

/**
 * Todo contrato de transporte tem literal escrito a mao nos dois lados do fio (ADR-0015, ETAPA 7.3).
 *
 * A decisao 4 do ADR-0015 fixou o criterio: um `@SerialName` trocado no dominio tem de derrubar os
 * testes dos **dois** lados, porque o que prende o nome de um campo e um JSON com esse nome
 * digitado. Um teste que desserializa o corpo com o proprio tipo nao prende nada — a rota escreve
 * `"nome"`, o teste le `"nome"`, com o mesmo codigo dos dois lados da igualdade (P4). Foi assim que
 * organizacao e prova ficaram presas so do lado do aparelho, e so a mutacao mostrou
 * (`docs/cobertura-o-fio-preso-nos-dois-lados.md`, Parte I).
 *
 * O caminho mais curto para testar uma rota nova e `json.decodeFromString<NovoDto>(corpo)`, com o
 * tipo a um `import` de distancia. Por isso esta guarda le **todo** tipo `@Serializable` de
 * `com.platos.domain.transport`, direto dos arquivos de origem — e nao de uma lista mantida a mao,
 * que seria um segundo registro, e o contrato que alguem esquece de por na lista e o mesmo que
 * esquece de ganhar literal.
 *
 * **O que conta como literal:** uma string Kotlin, num arquivo de teste daquele lado, que traga
 * **juntas** todas as chaves do tipo, cada uma como `"chave":`. Juntas, e nao espalhadas pelo
 * arquivo: `"kind":"personal"` num teste de isolamento nao e o corpo de uma organizacao. Os
 * comentarios nao contam, os escapes sao resolvidos (`\"id\":` conta), e o que esta dentro de um
 * template `${...}` nao e texto do literal — valor pode ser interpolado, chave nao.
 *
 * **A forma que ela nao sabe ler reprova** (`exit 2`), nomeando o tipo e o motivo. Pular o que nao
 * entende seria passar em silencio justamente no contrato de forma nova. Ensinar uma forma nova e
 * uma edicao visivel deste arquivo, com o canario dela.
 *
 * **O que ela NAO prova:** que o literal e comparado contra a rota, nem que ele prende o fio. Ela
 * prova que ele existe. Quem prova que prende e a mutacao de `@SerialName`, e ela so roda quando
 * alguem a roda — o contrato novo continua devendo a mutacao dele (P16: esta guarda e a camada
 * vizinha). E ela aceita qualquer string que traga as chaves, inclusive uma de outro assunto: a
 * lista impressa no fim diz quem satisfez cada par, e ler essa lista e parte da conferencia.
 *
 * Uso: node fio.mjs [--transport <dir>]
 *
 * Saida: `0` todo tipo esta preso nos dois lados; `1` algum par (tipo, lado) nao tem literal; `2` a
 * guarda nao conseguiu conferir — piso ou forma que ela nao le. `--transport` troca a raiz dos
 * contratos, e existe para o CI provar que esta verificacao continua capaz de falhar.
 */

const RAIZ = fileURLToPath(new URL('../../', import.meta.url));
const TRANSPORT = 'packages/domain/src/commonMain/kotlin/com/platos/domain/transport';

// Os dois lados do fio, e so eles: os conjuntos de teste unitario que `./gradlew build` roda. O
// `androidTest` nao conta — nenhum literal de contrato mora la, e conta-lo alargaria o que
// satisfaz esta guarda sem caso que o peca.
const LADOS = [
  ['servidor', 'apps/api/src/test'],
  ['aparelho', 'apps/android/src/test'],
];

const argv = process.argv.slice(2);
const flag = argv.indexOf('--transport');
if (flag !== -1 && !argv[flag + 1]) {
  console.error('::error::--transport pede um diretorio');
  process.exit(2);
}
const raizDoTransporte = flag === -1 ? join(RAIZ, TRANSPORT) : resolve(argv[flag + 1]);

const dizer = (texto) => console.log(texto);
const naRaiz = (p) => {
  const r = relative(RAIZ, p);
  return (r.startsWith('..') ? p : r).split(sep).join('/');
};

/** Motivos de `exit 2`: a guarda nao conseguiu conferir. */
const naoConferiu = [];
/** Motivos de `exit 1`: um par (tipo, lado) sem literal. */
const soltos = [];

// ---------------------------------------------------------------- leitura de Kotlin

/** Todos os `.kt` abaixo de `dir`, ou `null` se `dir` nao for diretorio. */
function arquivosKt(dir) {
  try {
    if (!statSync(dir).isDirectory()) return null;
  } catch {
    return null;
  }
  const achados = [];
  const andar = (d) => {
    for (const e of readdirSync(d).sort()) {
      const p = join(d, e);
      if (statSync(p).isDirectory()) andar(p);
      else if (e.endsWith('.kt')) achados.push(p);
    }
  };
  andar(dir);
  return achados;
}

/** Ocupa o lugar de um template `${...}` no texto de um literal: nao e chave, nem parte de uma. */
const TEMPLATE = '\u0000';

/**
 * Separa uma fonte Kotlin em **codigo sem comentarios** e **strings literais**.
 *
 * Sabe o minimo de Kotlin para nao se enganar: comentario de linha e de bloco (os de bloco se
 * aninham), nome entre crases (`fun \`nome do teste\`` pode ter aspas ou apostrofo), literal de
 * caractere (`'"'` nao abre string), string crua `"""..."""`, string comum com escapes, e template
 * `${...}`, que e codigo e pode conter strings sem encerrar a de fora.
 *
 * No codigo devolvido, cada string vira um marcador com o indice dela em `literais` — e isso que
 * permite ler `@SerialName("...")` sem confundir virgula ou aspas de dentro da string com sintaxe.
 */
function lexar(s) {
  const literais = [];
  const inicioDeLinha = [0];
  for (let k = 0; k < s.length; k++) if (s[k] === '\n') inicioDeLinha.push(k + 1);
  const linhaDe = (pos) => {
    let lo = 0;
    let hi = inicioDeLinha.length - 1;
    while (lo < hi) {
      const meio = (lo + hi + 1) >> 1;
      if (inicioDeLinha[meio] <= pos) lo = meio;
      else hi = meio - 1;
    }
    return lo + 1;
  };

  const fimDeBloco = (i) => {
    let prof = 0;
    while (i < s.length) {
      if (s[i] === '/' && s[i + 1] === '*') {
        prof++;
        i += 2;
      } else if (s[i] === '*' && s[i + 1] === '/') {
        prof--;
        i += 2;
        if (prof === 0) return i;
      } else i++;
    }
    return i;
  };

  const fimDeCaractere = (i) => {
    let j = i + 1;
    if (s[j] === '\\') j += s[j + 1] === 'u' ? 6 : 2;
    else j += 1;
    return s[j] === "'" ? j + 1 : -1;
  };

  const ESCAPES = { t: '\t', b: '\b', n: '\n', r: '\r', "'": "'", '"': '"', '\\': '\\', $: '$' };

  function string(i) {
    const inicio = i;
    const cru = s.startsWith('"""', i);
    let texto = '';
    i += cru ? 3 : 1;
    while (i < s.length) {
      const c = s[i];
      if (cru && s.startsWith('"""', i)) {
        // `""""` fecha com as tres ultimas: as aspas a mais sao conteudo.
        let n = 3;
        while (s[i + n] === '"') n++;
        texto += '"'.repeat(n - 3);
        i += n;
        break;
      }
      if (!cru && c === '"') {
        i++;
        break;
      }
      if (!cru && c === '\n') break; // string comum nao atravessa linha; mal formada, para aqui
      if (!cru && c === '\\') {
        const e = s[i + 1];
        if (e === 'u') {
          texto += String.fromCharCode(parseInt(s.slice(i + 2, i + 6), 16));
          i += 6;
        } else {
          texto += ESCAPES[e] ?? `\\${e}`;
          i += 2;
        }
        continue;
      }
      if (c === '$' && s[i + 1] === '{') {
        i = codigo(i + 2, true)[0];
        texto += TEMPLATE;
        continue;
      }
      texto += c;
      i++;
    }
    literais.push({ texto, linha: linhaDe(inicio) });
    return [i, literais.length - 1];
  }

  /** Le codigo a partir de `i`. Dentro de template, para no `}` que fecha o `${`. */
  function codigo(i, dentroDeTemplate) {
    let out = '';
    let prof = 0;
    while (i < s.length) {
      const c = s[i];
      if (c === '/' && s[i + 1] === '/') {
        while (i < s.length && s[i] !== '\n') i++;
        continue;
      }
      if (c === '/' && s[i + 1] === '*') {
        i = fimDeBloco(i);
        out += ' ';
        continue;
      }
      if (c === '`') {
        const j = s.indexOf('`', i + 1);
        const fim = j === -1 ? s.length : j + 1;
        out += s.slice(i, fim);
        i = fim;
        continue;
      }
      if (c === "'") {
        const j = fimDeCaractere(i);
        if (j !== -1) {
          out += ' ';
          i = j;
          continue;
        }
      }
      if (c === '"') {
        const [fim, indice] = string(i);
        out += `\u0002${indice}\u0003`;
        i = fim;
        continue;
      }
      if (dentroDeTemplate) {
        if (c === '{') prof++;
        else if (c === '}') {
          if (prof === 0) return [i + 1, out];
          prof--;
        }
      }
      out += c;
      i++;
    }
    return [i, out];
  }

  const [, cod] = codigo(0, false);
  return { codigo: cod, literais };
}

// ---------------------------------------------------------------- os contratos

const MARCADOR = /^\u0002(\d+)\u0003$/;
const SERIAL_NAME = /^@(?:kotlinx\.serialization\.)?SerialName$/;

/** Divide por virgula no nivel de fora — generico, chamada e colecao nao partem um parametro. */
function porVirgula(texto) {
  const partes = [];
  let prof = 0;
  let atual = '';
  for (let i = 0; i < texto.length; i++) {
    const c = texto[i];
    if (c === '-' && texto[i + 1] === '>') {
      atual += '->';
      i++;
      continue;
    }
    if ('([{<'.includes(c)) prof++;
    else if (')]}>'.includes(c)) prof--;
    if (c === ',' && prof === 0) {
      partes.push(atual);
      atual = '';
    } else atual += c;
  }
  partes.push(atual);
  return partes.map((p) => p.trim()).filter(Boolean);
}

/** As anotacoes do comeco de `texto`, e o resto. */
function anotacoes(texto) {
  const achadas = [];
  let resto = texto.trimStart();
  for (;;) {
    const m = resto.match(/^(@[\w.]+)(\s*\(([^()]*)\))?\s*/);
    if (!m) return { achadas, resto };
    achadas.push({ nome: m[1], args: m[3] });
    resto = resto.slice(m[0].length);
  }
}

/**
 * Os contratos de um arquivo: `{ nome, chaves }` para cada `@Serializable` na forma que esta
 * guarda sabe ler, e um motivo em `naoConferiu` para cada um que nao esta.
 */
function contratosDe(arquivo) {
  const { codigo, literais } = lexar(readFileSync(arquivo, 'utf8'));
  const onde = naRaiz(arquivo);
  const tipos = [];
  const forma = (quem, motivo) => naoConferiu.push(`forma: ${quem} (${onde}) ${motivo}`);

  const alias = codigo.match(/import\s+kotlinx\.serialization\.Serializable\s+as\s+(\w+)/);
  if (alias) forma(`o import de Serializable`, `usa o apelido \`${alias[1]}\`, e esta guarda procura o nome`);

  for (const m of codigo.matchAll(/@(?:kotlinx\.serialization\.)?Serializable(?![\w])/g)) {
    let p = m.index + m[0].length;
    if (codigo[p] === '(') {
      forma('um `@Serializable(...)`', 'tem argumento (serializador proprio): as chaves nao saem dos parametros');
      continue;
    }
    const resto = codigo.slice(p);
    const cabecalho = resto.match(
      /^\s*((?:(?:@[\w.]+(?:\s*\([^()]*\))?|[a-z]+)\s+)*?)(class|object|interface)\s+(\w+)/,
    );
    if (!cabecalho) {
      forma('um `@Serializable`', 'nao esta sobre uma declaracao de classe que esta guarda reconheca');
      continue;
    }
    const nome = cabecalho[3];
    const { achadas, resto: modificadores } = anotacoes(cabecalho[1]);
    const estranhas = achadas.filter((a) => !SERIAL_NAME.test(a.nome)).map((a) => a.nome);
    if (estranhas.length > 0) {
      forma(nome, `tem anotacao de classe que esta guarda nao le: ${estranhas.join(', ')}`);
      continue;
    }
    const mods = modificadores.split(/\s+/).filter(Boolean);
    const alheios = mods.filter((x) => !['public', 'internal', 'private', 'data'].includes(x));
    if (cabecalho[2] !== 'class' || alheios.length > 0 || !mods.includes('data')) {
      forma(nome, `e \`${[...mods, cabecalho[2]].join(' ')}\`, e esta guarda so le \`data class\``);
      continue;
    }

    p += cabecalho[0].length;
    const depois = codigo.slice(p).trimStart();
    if (!depois.startsWith('(')) {
      forma(nome, `nao tem construtor primario logo apos o nome (${depois.slice(0, 20).trim()}...)`);
      continue;
    }
    const abre = codigo.indexOf('(', p);
    let prof = 0;
    let fecha = -1;
    for (let i = abre; i < codigo.length; i++) {
      if (codigo[i] === '(') prof++;
      else if (codigo[i] === ')' && --prof === 0) {
        fecha = i;
        break;
      }
    }
    if (fecha === -1) {
      forma(nome, 'tem o construtor primario sem fechar');
      continue;
    }
    const seguinte = codigo.slice(fecha + 1).trimStart()[0];
    if (seguinte === ':') {
      forma(nome, 'declara supertipo: propriedade herdada nao aparece nos parametros');
      continue;
    }
    if (seguinte === '{') {
      forma(nome, 'tem corpo: propriedade declarada no corpo tambem e serializada, e esta guarda nao a ve');
      continue;
    }

    const chaves = [];
    let legivel = true;
    for (const parametro of porVirgula(codigo.slice(abre + 1, fecha))) {
      const { achadas: anots, resto: semAnot } = anotacoes(parametro);
      const decl = semAnot.match(/^((?:(?:public|internal|private)\s+)*)(val|var)\s+(\w+)\s*:/);
      if (!decl) {
        forma(nome, `tem parametro que nao e \`val\`/\`var\` simples: ${parametro.replace(/\s+/g, ' ')}`);
        legivel = false;
        break;
      }
      let chave = decl[3];
      for (const a of anots) {
        const marcador = SERIAL_NAME.test(a.nome) ? (a.args ?? '').trim().match(MARCADOR) : null;
        if (!marcador) {
          forma(nome, `tem, no parametro \`${decl[3]}\`, anotacao que esta guarda nao le: ${a.nome}`);
          legivel = false;
          break;
        }
        chave = literais[Number(marcador[1])].texto;
        if (chave.includes(TEMPLATE)) {
          forma(nome, `tem \`@SerialName\` com template no parametro \`${decl[3]}\``);
          legivel = false;
          break;
        }
      }
      if (!legivel) break;
      chaves.push(chave);
    }
    if (!legivel) continue;
    if (chaves.length === 0) {
      naoConferiu.push(`piso: ${nome} (${onde}) nao tem chave nenhuma — qualquer literal o satisfaria`);
      continue;
    }
    tipos.push({ nome, arquivo: onde, chaves });
  }
  return tipos;
}

// ---------------------------------------------------------------- o piso dos contratos

const arquivosDoTransporte = arquivosKt(raizDoTransporte);
if (arquivosDoTransporte === null || arquivosDoTransporte.length === 0) {
  console.error(
    `::error::piso: ${naRaiz(raizDoTransporte)} ${arquivosDoTransporte === null ? 'nao e um diretorio' : 'nao tem nenhum .kt'} — ` +
      'uma raiz vazia passaria em qualquer conferencia',
  );
  process.exit(2);
}

const contratos = arquivosDoTransporte.flatMap(contratosDe);
dizer(`contratos: ${contratos.length} tipo(s) @Serializable em ${naRaiz(raizDoTransporte)} (${arquivosDoTransporte.length} arquivo(s))`);
if (contratos.length === 0 && naoConferiu.length === 0) {
  naoConferiu.push(`piso: nenhum tipo @Serializable em ${naRaiz(raizDoTransporte)} — nada a conferir nao e o mesmo que tudo conferido`);
}

// ---------------------------------------------------------------- os literais de cada lado

const literaisPorLado = new Map();
for (const [lado, raizDoLado] of LADOS) {
  const arquivos = arquivosKt(join(RAIZ, raizDoLado));
  if (arquivos === null) {
    naoConferiu.push(`piso: o lado do ${lado} (${raizDoLado}) nao e um diretorio`);
    continue;
  }
  const literais = arquivos.flatMap((a) =>
    lexar(readFileSync(a, 'utf8')).literais.map((l) => ({ ...l, arquivo: naRaiz(a) })),
  );
  dizer(`${lado}: ${raizDoLado} — ${arquivos.length} arquivo(s), ${literais.length} literal(is)`);
  if (literais.length === 0) {
    naoConferiu.push(`piso: o lado do ${lado} (${raizDoLado}) nao tem nenhum literal lido — a leitura pode estar quebrada`);
    continue;
  }
  literaisPorLado.set(lado, literais);
}

// ---------------------------------------------------------------- a conferencia

const escapar = (t) => t.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');

dizer('');
for (const tipo of contratos) {
  const padroes = tipo.chaves.map((k) => new RegExp(`"${escapar(k)}"\\s*:`));
  dizer(`${tipo.nome} [${tipo.chaves.join(', ')}] — ${tipo.arquivo}`);
  for (const [lado, raizDoLado] of LADOS) {
    const literais = literaisPorLado.get(lado);
    if (!literais) continue;
    const presos = literais.filter((l) => padroes.every((re) => re.test(l.texto)));
    const porArquivo = new Map();
    for (const l of presos) if (!porArquivo.has(l.arquivo)) porArquivo.set(l.arquivo, l.linha);
    if (porArquivo.size === 0) {
      dizer(`  ${lado}: NENHUM`);
      soltos.push(
        `${tipo.nome} (${tipo.arquivo}) nao tem literal escrito a mao do lado do ${lado}: ` +
          `nenhuma string de ${raizDoLado} traz juntas as chaves ${tipo.chaves.map((k) => `"${k}"`).join(', ')}`,
      );
    } else {
      for (const [arquivo, linha] of porArquivo) dizer(`  ${lado}: ${arquivo}:${linha}`);
    }
  }
}

if (naoConferiu.length > 0) {
  for (const p of naoConferiu) console.error(`::error::${p}`);
  for (const p of soltos) console.error(`::error::${p}`);
  process.exit(2);
}
if (soltos.length > 0) {
  for (const p of soltos) console.error(`::error::${p}`);
  process.exit(1);
}

dizer(`\nfio: os ${contratos.length} contratos estao presos por literal nos dois lados`);
