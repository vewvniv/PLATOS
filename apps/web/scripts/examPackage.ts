import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import type { LayoutMap } from '../src/layoutMap.js';
import { repoRoot } from './formulaAssets.js';

/** A unica variante da fatia 2a. Randomizacao e a fatia 7. */
const DEFAULT_VARIANT = 'v1';

interface AssignmentQr {
  payload: string;
  modules: string[];
}

interface PackageAssignment {
  student_token: string;
  variant_id: string;
  qr?: AssignmentQr | null;
}

interface ExamPackage {
  meta: { exam_id: string };
  assignments: PackageAssignment[];
  layout: Record<string, LayoutMap>;
}

/**
 * A folha de uma atribuicao: a geometria da variante dela, com o QR dela.
 *
 * **Esta e a segunda implementacao da mesma regra**, e a primeira e `ExamPackage.folhaDaAtribuicao`
 * no dominio Kotlin (D-2a.1). Ela e mecanica de proposito — trocar dois campos de uma primitiva —
 * porque duas implementacoes precisam coincidir, e a paridade entre plataformas e quem pega a
 * divergencia se nao coincidirem.
 */
function folhaDaAtribuicao(geometria: LayoutMap, qr: AssignmentQr): LayoutMap {
  const qrs = geometria.pages.flatMap((pagina) =>
    pagina.primitives.filter((primitiva) => primitiva.type === 'qr'),
  );
  if (qrs.length !== 1) {
    // A spec exige uma regiao escaneavel por folha, com um QR dentro dela. Zero ou dois e mapa que
    // a validacao do layout nao deveria ter deixado passar, e "o primeiro que aparecer" esconderia.
    throw new Error(`esperava exatamente um QR no layout, e o mapa tem ${qrs.length}`);
  }

  return {
    ...geometria,
    pages: geometria.pages.map((pagina) => ({
      ...pagina,
      primitives: pagina.primitives.map((primitiva) =>
        primitiva.type === 'qr'
          ? { ...primitiva, payload: qr.payload, modules: qr.modules }
          : primitiva,
      ),
    })),
  };
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
export async function loadPublishedLayout(
  variant: string = DEFAULT_VARIANT,
  studentToken?: string,
): Promise<LayoutMap> {
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

  // Sem aluno pedido, desenha a folha da variante — que e o caso do pacote sem atribuicao (a folha
  // avulsa do §7) e o que todo comando existente continua fazendo.
  if (studentToken === undefined) return map;

  const atribuicao = (published.assignments ?? []).find(
    (a) => a.student_token === studentToken,
  );
  if (!atribuicao) {
    const declarados = (published.assignments ?? []).map((a) => a.student_token).join(', ');
    throw new Error(
      `o pacote de ${published.meta.exam_id} nao tem atribuicao para o aluno ${studentToken}; ` +
        `tem: ${declarados || '(nenhuma)'}`,
    );
  }
  if (!atribuicao.qr) {
    throw new Error(
      `a atribuicao de ${studentToken} nao tem QR proprio; imprimir a folha da variante no lugar ` +
        `dela produziria prova sem dono`,
    );
  }

  return folhaDaAtribuicao(map, atribuicao.qr);
}

/** Os tokens que o pacote enderecou, na ordem em que ele os declara. */
export async function publishedTokens(): Promise<string[]> {
  const packagePath = resolve(
    repoRoot,
    process.env.PLATOS_PACKAGE ?? 'fixtures/prova-referencia.package.json',
  );
  const published: ExamPackage = JSON.parse(await readFile(packagePath, 'utf8'));
  return (published.assignments ?? []).map((a) => a.student_token);
}
