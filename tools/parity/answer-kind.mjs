import { readFileSync } from 'node:fs';

/**
 * Os valores de `answer_kind` concordam entre o dominio e o `check` da migration (ADR-0015).
 *
 * Eles viviam em **tres** registros que nao se conheciam: `tipoGravado()` na API, `tipoNoEnvio()`
 * no aparelho, e o `check (answer_kind in (...))` da migration. O ADR-0015 uniu os dois primeiros
 * em `com.platos.domain.transport.AnswerKind`; o terceiro **continua existindo**, de proposito.
 *
 * **Por que o `check` nao e gerado a partir do Kotlin** (ADR-0015 decisao 3): ele e a guarda do
 * banco, e uma guarda escrita por quem escreve as linhas aceitaria, por construcao, tudo o que o
 * codigo produzisse — inclusive o que ele produzisse errado. A independencia e o valor dela.
 *
 * Divergir entre os dois nao quebra teste nenhum: o Kotlin compila, os testes de literal passam
 * (eles afirmam o que o aparelho **envia**, nao o que o banco **aceita**), e o desencontro so
 * aparece como `violates check constraint` num INSERT em producao — depois de a folha ja ter sido
 * corrigida. E a mesma familia de falha silenciosa do limiar do OMR, e por isso a verificacao vive
 * aqui, num caminho que nao compartilha uma linha com o Kotlin nem com o SQL.
 *
 * Uso: node answer-kind.mjs [--esperado a,b,c,d]
 *
 * `--esperado` existe para o CI provar que esta verificacao continua capaz de falhar: passar um
 * conjunto diferente do declarado tem de reprovar.
 */

const FONTE_DOMINIO = 'packages/domain/src/commonMain/kotlin/com/platos/domain/transport/AnswerKind.kt';
const FONTE_MIGRATION = 'supabase/migrations/20260917134500_result_tables.sql';

const argv = process.argv.slice(2);
const flag = argv.indexOf('--esperado');
const forcado = flag === -1 ? null : argv[flag + 1].split(',');

const problemas = [];
const dizer = (texto) => console.log(texto);
const ler = (rel) => readFileSync(new URL(rel, new URL('../../', import.meta.url)), 'utf8');

// ---------------------------------------------------------------- o dominio

const fonte = ler(FONTE_DOMINIO);

// As quatro `const val`, na ordem em que estao declaradas. Ler `TODOS` seria ler uma lista que
// pode estar incompleta sem que nada acuse; ler as constantes e ler o que o `when` usa.
const constantes = [...fonte.matchAll(/const val [A-Z_]+: String = "([a-z_]+)"/g)].map((m) => m[1]);
if (constantes.length === 0) {
  console.error(`nao achei nenhuma \`const val\` de answer_kind em ${FONTE_DOMINIO} — a forma mudou`);
  process.exit(2);
}

// `TODOS` e o que o resto do codigo enxerga como "os valores". Se ela discordar das constantes,
// alguem acrescentou uma constante e esqueceu a lista — que e metade do defeito que isto procura.
const todos = fonte.match(/val TODOS: List<String> = listOf\(([^)]*)\)/);
if (!todos) {
  console.error(`nao achei \`TODOS\` em ${FONTE_DOMINIO}`);
  process.exit(2);
}
const nomesEmTodos = todos[1].split(',').map((s) => s.trim()).filter(Boolean);
if (nomesEmTodos.length !== constantes.length) {
  problemas.push(
    `o dominio declara ${constantes.length} constante(s) e \`TODOS\` lista ${nomesEmTodos.length}`,
  );
}

const doDominio = forcado ?? constantes;
dizer(`dominio:   ${doDominio.join(', ')}`);

// ---------------------------------------------------------------- o `check` da migration

const sql = ler(FONTE_MIGRATION);
const check = sql.match(/check \(answer_kind in \(([^)]*)\)\)/);
if (!check) {
  console.error(`nao achei o \`check (answer_kind in (...))\` em ${FONTE_MIGRATION} — a forma mudou`);
  process.exit(2);
}
const doCheck = check[1].split(',').map((s) => s.trim().replace(/^'|'$/g, ''));
dizer(`migration: ${doCheck.join(', ')}`);

// ---------------------------------------------------------------- a comparacao

// Conjunto **e** ordem. A ordem nao tem efeito no banco, mas divergir nela e o primeiro sintoma de
// que alguem editou um dos dois lados sem olhar o outro — e custa nada exigir.
const faltamNoCheck = doDominio.filter((v) => !doCheck.includes(v));
const sobramNoCheck = doCheck.filter((v) => !doDominio.includes(v));

for (const v of faltamNoCheck) {
  problemas.push(`o dominio declara '${v}' e o \`check\` da migration nao o admite`);
}
for (const v of sobramNoCheck) {
  problemas.push(`o \`check\` da migration admite '${v}' e o dominio nao o declara`);
}
if (problemas.length === 0 && doDominio.join(',') !== doCheck.join(',')) {
  problemas.push(
    `os dois trazem os mesmos valores em ordens diferentes: ` +
      `dominio [${doDominio.join(', ')}] vs migration [${doCheck.join(', ')}]`,
  );
}

if (problemas.length > 0) {
  for (const p of problemas) console.error(`::error::${p}`);
  process.exit(1);
}

dizer(`os ${doDominio.length} valores de answer_kind concordam nos dois registros`);
