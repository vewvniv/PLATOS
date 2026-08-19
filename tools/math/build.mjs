import { readFileSync, writeFileSync, mkdirSync, rmSync, readdirSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { convert, BODY_SIZE_UM, RASTER_DPI } from './convert.mjs';

/**
 * Passo reproduzivel de build: converte `fixtures/formulas.json` em SVG, raster e manifesto.
 *
 * O manifesto e a entrada pura do Layout Engine (D-1.5.3): ele traz identificador e dimensoes ja
 * resolvidas, para que o calculo do `LayoutMap` nunca precise abrir imagem. As dimensoes tambem
 * sao gravadas em `prova-referencia.json`, nas questoes que referenciam formula — o autor escreve
 * a referencia, a conversao completa a medida.
 *
 *   node build.mjs            # regrava fixtures/formulas/ e o manifesto
 *   node build.mjs --check    # nao escreve; falha se a saida divergir do que esta versionado
 */
const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), '../..');
const sourcePath = resolve(repoRoot, 'fixtures/formulas.json');
const outputDir = resolve(repoRoot, 'fixtures/formulas');
const manifestPath = resolve(repoRoot, 'fixtures/formulas.manifest.json');
const examPath = resolve(repoRoot, 'fixtures/prova-referencia.json');

const check = process.argv.includes('--check');
const { formulas } = JSON.parse(readFileSync(sourcePath, 'utf8'));

const converted = formulas.map((entry) => convert(entry));

const manifest = {
  comment:
    'Gerado por tools/math/build.mjs a partir de fixtures/formulas.json. Nao edite a mao. ' +
    'As dimensoes sao a caixa que o Layout Engine reserva; o raster e o que os dois ' +
    'renderizadores desenham, byte a byte igual dos dois lados (D-1.5.1).',
  raster_dpi: RASTER_DPI,
  // Declarado para que o KMP possa afirmar que a formula foi composta no mesmo corpo do texto.
  // Sem isto, um corpo divergente entre a conversao e o Layout Engine passaria despercebido.
  body_size_um: BODY_SIZE_UM,
  formulas: converted.map((f) => ({
    id: f.id,
    notation: f.notation,
    width_um: f.widthUm,
    height_um: f.heightUm,
    baseline_offset_um: f.baselineOffsetUm,
    width_px: f.widthPx,
    height_px: f.heightPx,
    raster: `formulas/${f.id}.png`,
    sha256: f.sha256,
  })),
};

const manifestText = `${JSON.stringify(manifest, null, 2)}\n`;

// A referencia da questao ganha a medida que a conversao resolveu.
const exam = JSON.parse(readFileSync(examPath, 'utf8'));
const byId = new Map(converted.map((f) => [f.id, f]));
let attached = 0;
for (const question of exam.questions) {
  if (!question.formula) continue;
  const formula = byId.get(question.formula.reference);
  if (!formula) {
    console.error(
      `questao \`${question.id}\` referencia a formula \`${question.formula.reference}\`, ` +
        'que nao existe em fixtures/formulas.json',
    );
    process.exit(1);
  }
  question.formula = {
    reference: formula.id,
    width: formula.widthUm,
    height: formula.heightUm,
  };
  attached += 1;
}
const examText = `${JSON.stringify(exam, null, 2)}\n`;

if (check) {
  const problems = [];
  if (readFileSync(manifestPath, 'utf8') !== manifestText) {
    problems.push('fixtures/formulas.manifest.json esta desatualizado');
  }
  if (readFileSync(examPath, 'utf8') !== examText) {
    problems.push('as dimensoes em fixtures/prova-referencia.json estao desatualizadas');
  }
  for (const formula of converted) {
    const path = resolve(outputDir, `${formula.id}.png`);
    let versioned;
    try {
      versioned = readFileSync(path);
    } catch {
      problems.push(`${formula.id}.png nao esta versionado`);
      continue;
    }
    if (!versioned.equals(formula.png)) {
      problems.push(`${formula.id}.png difere do que a conversao produz agora`);
    }
  }
  if (problems.length > 0) {
    console.error('CONVERSAO DESATUALIZADA:');
    for (const problem of problems) console.error(`  - ${problem}`);
    console.error('\nrode `node build.mjs` e versione a saida');
    process.exit(1);
  }
  console.log(`conversao em dia: ${converted.length} formulas`);
  process.exit(0);
}

// Limpa antes de escrever: uma formula removida de `formulas.json` nao pode deixar o raster orfao
// para tras, senao a fixture continuaria carregando peso que nada referencia.
rmSync(outputDir, { recursive: true, force: true });
mkdirSync(outputDir, { recursive: true });
for (const formula of converted) {
  writeFileSync(resolve(outputDir, `${formula.id}.svg`), `${formula.svg}\n`);
  writeFileSync(resolve(outputDir, `${formula.id}.png`), formula.png);
}
writeFileSync(manifestPath, manifestText);
writeFileSync(examPath, examText);

const totalBytes = converted.reduce((sum, f) => sum + f.png.length, 0);
const svgBytes = readdirSync(outputDir)
  .filter((name) => name.endsWith('.svg'))
  .reduce((sum, name) => sum + readFileSync(resolve(outputDir, name)).length, 0);

console.log(`corpo ${BODY_SIZE_UM} um | raster a ${RASTER_DPI} dpi`);
for (const f of converted) {
  console.log(
    `  ${f.id.padEnd(16)} ${String(f.widthPx).padStart(4)} x ${String(f.heightPx).padStart(3)} px` +
      `  ${String(f.widthUm).padStart(6)} x ${String(f.heightUm).padStart(5)} um` +
      `  ${String(f.png.length).padStart(6)} B`,
  );
}
console.log(
  `\n${converted.length} formulas | ${attached} anexadas a questoes | ` +
    `raster ${(totalBytes / 1024).toFixed(1)} KiB | svg ${(svgBytes / 1024).toFixed(1)} KiB`,
);
