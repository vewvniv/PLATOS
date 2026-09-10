import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import type { LayoutMap } from '../src/layoutMap.js';
import { repoRoot } from './formulaAssets.js';

/** A unica variante da fatia 2a. Randomizacao e a fatia 7. */
const DEFAULT_VARIANT = 'v1';

interface ExamPackage {
  meta: { exam_id: string };
  layout: Record<string, LayoutMap>;
}

/**
 * O `LayoutMap` sai de dentro do pacote publicado (D-2a.5).
 *
 * Antes desta fatia o renderizador lia o golden solto. A diferenca importa porque o pacote e o
 * artefato que o dispositivo vai receber e conferir por hash: desenhar a partir dele e o que faz a
 * paridade e a fidelidade — que ja existem e ja sabem falhar — julgarem o pacote, sem uma linha de
 * verificacao nova. Um layout dentro do pacote divergente do que se imprime deixaria de ser
 * detectavel no instante em que a folha viesse de outro lugar.
 *
 * O renderizador continua sem calcular geometria: so mudou de onde ela vem.
 */
export async function loadPublishedLayout(variant: string = DEFAULT_VARIANT): Promise<LayoutMap> {
  // O padrao e o pacote de referencia, e e ele que a paridade e a fidelidade do CI desenham: nenhum
  // comando existente muda de comportamento. A variavel existe para a fatia 4a poder imprimir a
  // `prova-2` — a folha de outra prova que a tarefa 8.5 escaneia para fechar a 6.4b, herdada da 3c.
  // Sem ela o caminho estaria fixo e a folha adversarial nao seria imprimivel.
  const packagePath = resolve(
    repoRoot,
    process.env.PLATOS_PACKAGE ?? 'fixtures/prova-referencia.package.json',
  );
  const published: ExamPackage = JSON.parse(await readFile(packagePath, 'utf8'));

  const map = published.layout[variant];
  if (!map) {
    const declaradas = Object.keys(published.layout).join(', ') || '(nenhuma)';
    throw new Error(
      `o pacote de ${published.meta.exam_id} nao declara layout para a variante ${variant}; ` +
        `declara: ${declaradas}`,
    );
  }
  return map;
}
