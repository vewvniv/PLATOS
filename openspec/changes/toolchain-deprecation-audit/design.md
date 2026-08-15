## Context

Ver `proposal.md — Why`. O levantamento de 2026-08-15 produziu estes números:

| Item | Hoje | Disponível | Situação |
|---|---|---|---|
| Gradle | 8.14 | 9.7.0 | 3 avisos, todos de dentro do AGP |
| AGP | 8.10.1 | 9.3.1 | major |
| Kotlin | 2.2.0 | 2.4.10 | sem aviso |
| Ktor | 3.2.0 | 3.5.2 | sem aviso |
| jOOQ | 3.20.3 | 3.21.7 | sem aviso |
| Ações do GitHub | `v4` / `v2` | checkout v7, setup-java v5, setup-node v7, upload-artifact v7, download-artifact v8, gradle/actions v6, emulator-runner v2.38 | Node 20 depreciado |
| React / Vite / vitest / TypeScript | 18 / 6 / 2 / 5.9 | 19 / 8 / 4 / 7.0 | sem depreciação |

Os três avisos do Gradle são `isCrunchPngs`, `isUseProguard` e `isWearAppUnbundled` — propriedades booleanas do AGP que violam as regras de Java Bean. Nada que possamos corrigir; some quando o AGP subir.

O que torna esta mudança segura é o que a fatia 1 deixou pronto: golden do `LayoutMap` comparado byte a byte nos três alvos, paridade entre renderizadores a 0,3 mm e fidelidade do documento a 0,05 mm. Uma atualização de toolchain que mexa em medição, layout ou desenho **não tem como passar despercebida**. É a primeira vez no projeto que dá para atualizar dependência com rede de segurança.

## Goals / Non-Goals

**Goals**

- Eliminar o sombreamento de `codePoints`, que hoje faz a mesma medição rodar por dois caminhos.
- Sair da faixa de depreciação ativa: Gradle, AGP e ações do GitHub.
- Provar, a cada passo, que o golden e a paridade não mudaram.

**Non-Goals**

- Atualizar tudo que está defasado. Kotlin, Ktor, jOOQ, React e companhia não avisam nada; atualizá-los é escolha, não necessidade, e cada um carrega risco próprio.
- TypeScript 7 nesta rodada. É uma reescrita nativa do compilador, não um incremento — merece avaliação própria.
- Qualquer acomodação de resultado. Se um teste mudar de valor, a atualização volta atrás.

## Decisions

### D-A.1 — Corrigir o sombreamento de `codePoints` antes de qualquer atualização

`TextMeasurer` define `private fun String.codePoints(): List<Int>`. Em JVM e Android essa extensão é sombreada por `java.lang.String.codePoints(): IntStream`, que o Kotlin aceita em `for` porque `IntStream.iterator()` serve como operador. O compilador avisa; o build passa.

Resultado: **JVM e Android usam a implementação da plataforma, o JS usa a nossa.** Duas implementações para a operação que decide quantos code points um texto tem — dentro da função de medição que D-1.1 exige ser idêntica entre alvos.

Hoje elas concordam, e o golden prova isso para o corpus da fixture. Mas a concordância é sobre entrada bem formada. Um surrogate solto, ou qualquer divergência futura entre as duas, apareceria como layout diferente por plataforma — a classe de defeito mais cara desta arquitetura, porque a fatia 2 congela geometria em pacote com hash.

A correção é renomear a extensão para um nome que nada sombreie. Vem primeiro justamente para que as atualizações seguintes sejam medidas contra uma base de um caminho só.

*Alternativa descartada:* declarar `@Suppress` no aviso. Silenciaria o sintoma e manteria as duas implementações.

### D-A.2 — Gradle e AGP sobem juntos, e sozinhos

Os avisos de Gradle 9 vêm do AGP, então subir o Gradle sem o AGP não resolve nada, e subir o AGP sem o Gradle pode não ser suportado. As duas versões movem no mesmo passo, sem nenhuma outra mudança junto, para que qualquer quebra tenha uma causa só.

O salto é grande — AGP 8.10 → 9.x atravessa uma major. O módulo Android do projeto é mínimo (um renderizador, sem UI, sem CameraX), o que torna este o momento mais barato possível para atravessar: quanto mais tarde, mais superfície.

### D-A.3 — Cada atualização é validada pela tríade da fatia 1

Depois de cada passo, nesta ordem:

1. `./gradlew build` — inclui o golden do `LayoutMap` byte a byte nos três alvos
2. `tools/parity/fidelidade.mjs` nos dois PDFs — 0,05 mm
3. `tools/parity/compare.mjs` web × Android — 0,3 mm

O passo 1 sozinho já pega mudança de medição ou de paginação. Os passos 2 e 3 pegam mudança de desenho, que é o que uma atualização de AGP ou de `pdf-lib` poderia causar sem tocar em nenhum teste de unidade.

### D-A.4 — Ações do GitHub sobem em bloco, e por último

São independentes do build local e falham de forma barulhenta e imediata. Subir todas de uma vez custa uma execução de CI e não arrisca nada do domínio.

`reactivecircus/android-emulator-runner` continua em `v2` — é a major corrente, e a versão fixada já é a atual.

## Risks / Trade-offs

**AGP 9 quebrar o alvo Android do KMP** → é o risco maior. Mitigação: o módulo Android é casca fina e o teste instrumentado roda em minutos. Se não fechar, reverter e registrar o motivo; ficar no AGP 8.10 é sustentável por enquanto, já que os avisos são informativos.

**Gradle 9 exigir mudança em `buildSrc`** → `EmbedFontTask`, `EmbedFixturesTask` e `GenerateJooqTask` usam API de Provider já moderna, então o risco é baixo. Se aparecer, é correção pontual.

**Atualização mudar o golden** → não é risco a acomodar, é sinal de regressão. O golden só muda por decisão de geometria, nunca por efeito colateral de dependência.

**Vite 8 e vitest 4 exigirem Node mais novo** → o CI usa Node 22 e o ambiente local usa 24. Se algum exigir mais, avisar antes de mexer no ambiente.

## Migration Plan

Aditivo e reversível: cada passo é um commit isolado, e reverter é `git revert`. Nada de banco, nada de contrato, nada de geometria.

## Open Questions

- Kotlin 2.4 vale nesta rodada? Não avisa nada hoje, mas ficar duas majors atrás encarece o salto futuro. Decidível quando Gradle e AGP estiverem estáveis.
- TypeScript 7 tem risco próprio, por ser reescrita de compilador. Fica para uma avaliação separada, com a suíte do `apps/web` como critério.
