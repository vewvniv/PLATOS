import { readFileSync } from 'node:fs';

/**
 * O limiar do OMR concorda em tres lugares que nao se conhecem (ADR-0011, tarefa 8.4).
 *
 * O numero que decide se uma bolha esta marcada aparece em tres registros, e nenhum deles verifica
 * os outros:
 *
 * - a **constante do aplicativo**, em `OmrThreshold.MEDIDO_NA_FATIA_3B`;
 * - o **ADR-0011**, que declara a regra e registra o valor apurado sobre o corpus;
 * - o **corredor** que cada folha publicada declara no `ink_budget` do seu `LayoutMap`.
 *
 * Divergir entre eles nao quebra teste nenhum: o codigo compila, o golden nao muda, o hash do
 * pacote continua igual, e a folha segue sendo lida — com o numero errado. E a mesma familia de
 * falha silenciosa que a medicao de tinta tem, e por isso a verificacao vive aqui, num caminho que
 * nao compartilha uma linha com o Kotlin nem com o engine.
 *
 * Uso: node limiar.mjs [--esperado N]
 *
 * `--esperado` existe para o CI provar que esta verificacao continua capaz de falhar: passar um
 * numero diferente do declarado tem de reprovar.
 */

const argv = process.argv.slice(2);
const flag = argv.indexOf('--esperado');
const forcado = flag === -1 ? null : Number(argv[flag + 1]);

const problemas = [];
const dizer = (texto) => console.log(texto);

// ---------------------------------------------------------------- a constante do aplicativo

const fonte = readFileSync(
  'packages/domain/src/commonMain/kotlin/com/platos/domain/capture/BubbleVerdict.kt',
  'utf8',
);
const declarado = fonte.match(
  /MEDIDO_NA_FATIA_3B\s*=\s*OmrThreshold\(\s*value\s*=\s*(\d+)\s*,\s*margin\s*=\s*(\d+)\s*\)/,
);
if (!declarado) {
  console.error('nao achei `MEDIDO_NA_FATIA_3B` em BubbleVerdict.kt — a constante mudou de forma');
  process.exit(2);
}
const limiar = forcado ?? Number(declarado[1]);
const margem = Number(declarado[2]);
dizer(`aplicativo: limiar ${limiar}, margem ${margem}`);

// ---------------------------------------------------------------- o ADR

const adr = readFileSync('docs/adr/0011-criterio-do-limiar-do-omr.md', 'utf8');
const noAdr = adr.match(/`M\s*=\s*(\d+)`/);
if (!noAdr) {
  console.error('nao achei a margem declarada em ADR-0011');
  process.exit(2);
}
if (Number(noAdr[1]) !== margem) {
  problemas.push(
    `a margem do aplicativo e ${margem} e o ADR-0011 declara ${noAdr[1]}`,
  );
}
dizer(`ADR-0011: margem ${noAdr[1]}`);

// ---------------------------------------------------------------- o valor apurado, no registro

const registro = readFileSync('docs/cobertura-fatia-3b.md', 'utf8');
const apurado = registro.match(/\|\s*`T = \(V \+ C\) \/ 2`\s*\|[^|]*?→\s*\*\*(\d+)‰\*\*/);
if (!apurado) {
  console.error('nao achei o `T` apurado em docs/cobertura-fatia-3b.md');
  process.exit(2);
}
if (Number(apurado[1]) !== limiar) {
  problemas.push(
    `o aplicativo usa ${limiar} e o registro do corpus apurou ${apurado[1]}`,
  );
}
dizer(`registro do corpus: T apurado ${apurado[1]}`);

// ---------------------------------------------------------------- o corredor de cada folha

for (const mapa of ['fixtures/prova-referencia.layout.json', 'fixtures/folha-de-teste.layout.json']) {
  const doc = JSON.parse(readFileSync(mapa, 'utf8'));
  for (const regiao of doc.regions) {
    const { threshold_floor: piso, threshold_ceiling: teto } = regiao.ink_budget;
    dizer(`${mapa.split('/').pop()} regiao ${regiao.index}: corredor ${piso} a ${teto}`);
    if (limiar < piso || limiar > teto) {
      problemas.push(
        `o limiar ${limiar} cai fora do corredor ${piso}..${teto} que ${mapa} declara`,
      );
    }
    // A margem tem de caber no corredor dos dois lados, ou a faixa de indecisao encosta na borda
    // e o criterio de ADR-0011 deixa de ser verificavel na propria folha.
    if (limiar - margem < 0 || limiar + margem > 1000) {
      problemas.push(`a faixa ${limiar} +- ${margem} sai da escala de permilagem`);
    }
  }
}

if (problemas.length > 0) {
  console.error(`\n${problemas.length} problema(s):`);
  for (const p of problemas) console.error(`  - ${p}`);
  process.exit(1);
}

dizer('\nlimiar: os tres registros concordam.');
