# Cobertura — `slice-5d-corpus-de-medicao`

> Como cada verificação desta mudança foi **vista falhar**, e o que ainda não foi verificado. Os números abaixo são
> desta sessão (2026-10-03, UTC); nenhum foi copiado do plano.

**Plano:** `docs/superpowers/plans/2026-10-02-slice-5d-corpus-de-medicao.md` · **Desenho:** `docs/superpowers/specs/2026-10-02-slice-5d-corpus-de-medicao-design.md` · **Critério:** `docs/adr/0022-o-criterio-do-corpus-de-medicao.md`
**Janela:** agregado `04:55:06Z`–`04:58:59Z`; instrumentados `05:06:52Z`–`05:10:07Z`; saída por cabo `~05:12Z`.
**Ambiente:** Docker Desktop iniciado e depois encerrado pelo mantenedor (autorização dele para subir o emulador, P22); emulador `platos-atd34` (API 34, sem janela), desligado ao fim; Node v24.19.0. **Base** das mudanças: `c293395` (o plano); a última `83d2de5` antes da correção do `puxar.mjs`.

---

## 1. A linha de base desta sessão

- `./gradlew build --rerun-tasks --continue`, `BUILD SUCCESSFUL in 3m 52s`, **`184 actionable tasks: 184 executed`**.
  Pelo `timestamp` de dentro dos XML (todos entre `04:57:51Z` e `04:58:55Z`):

  | Módulo / tarefa | Testes | Falhas | Pulados |
  |---|---|---|---|
  | `apps/android` `testDebugUnitTest` | 487 | 0 | 0 |
  | `apps/android` `testReleaseUnitTest` | 470 | 0 | 0 |
  | `apps/api` `test` | 204 | 0 | 0 |
  | `packages/domain` `jvmTest` | 470 | 0 | 0 |
  | `packages/domain` `jsNodeTest` | 461 | 0 | 0 |
  | `packages/domain` `testAndroidHostTest` | 461 | 0 | 0 |

  A diferença 487 − 470 = 17 é `ColetaDoCorpusEmArquivoTest`, que só existe no debug: **a coleta não compila no release**.
- `./gradlew -p buildSrc test --rerun-tasks`, `6 de 6 executadas`, 1 teste, `timestamp 04:59:42Z`.
- `node --test "tools/corpus/*.test.mjs"`: 13 de 13. `node tools/divida/divida.mjs`: `exit 0`. `openspec validate slice-5d-corpus-de-medicao --strict`: válida.
- `verificarApkSemColeta` (dentro do `check`, no agregado): `debug: android-debug.apk, assinaturas da coleta: [com/platos/android/corpus/ColetaDoCorpusEmArquivo, coleta-ligada]` e `release: android-release-unsigned.apk, assinaturas da coleta: nenhuma`.
- `./gradlew :apps:android:connectedDebugAndroidTest`, **sem filtro**, no emulador: `Starting 176 tests`, **176 casos, 0 falhas, 2 pulados** (`AcumuloDeInstanciasProbe`, as duas sondas que já eram puladas), `timestamp 05:10:03Z`. As três de `corpus/` **passaram** (nenhuma pulada): `ColetaNaNotaInstrumentedTest` 2/2 (15,9 s: dirigiu a `ScanActivity` de verdade) e `VarreduraEliminaOCorpusInstrumentedTest` 1/1.

## 2. Como cada verificação foi vista falhar

Cada mutação foi aplicada sozinha, lida pela mensagem (P12), revertida e a reversão **rodada de novo** (P10). Quando a coluna "caiu" tem um só nome, a mutação isolou a camada.

### Regra da cópia (`CopiaDaNotaTest`, `src/test`, nas duas variantes)

| Garantia | Mutação | Caiu |
|---|---|---|
| A cópia vem antes de gravar a nota | `copiarSeLigada` movida para depois de `gravar()` | `a copia vem antes de gravar a nota` |
| Nota não gravada não deixa amostra | apagado o `if (!gravou …) coleta.eliminar` | `nota nao gravada elimina as amostras copiadas por ela` |
| A falha da cópia não derruba a nota | removido o `try/catch` de `copiarSeLigada` | `excecao da copia nao impede a nota` + `excecao ao montar as amostras nao impede a nota` |
| Desligada, não copia nem calcula o hash | `ligada()` ignorado | `coleta desligada nao copia nada…` + `a lista de amostras so e calculada com a coleta ligada` |

### A coleta no disco (`ColetaDoCorpusEmArquivoTest`, só debug)

| Garantia | Mutação | Caiu |
|---|---|---|
| 30 dias é o limite (`>=`) | `>=` → `>` | `29 dias mantem, exatamente 30 elimina, 31 elimina` |
| O nome não deriva da origem | foto nomeada por `amostra.arquivo` | `o nome nao deriva do arquivo de origem…` (+ 7 em cascata: os nomes são usados por vários testes) |
| O prazo não toca arquivo estranho | filtro `NOME_DA_AMOSTRA` desligado | `arquivo estranho na pasta nao e tocado pelo prazo` |
| Disco cheio não deixa resíduo | removida a limpeza do `catch` | `disco cheio no meio nao deixa foto sem dados nem temporario…` |
| O formato bate com o literal | campo `extra` em `AmostraDoCorpus` | `o formato bate com o literal do repositorio` + `copiar grava a foto com os mesmos bytes…` |
| Relógio anterior à criação mantém | `abs(agora − lastModified)` | `relogio anterior a criacao mantem` + `foto sem dados, dados sem foto e temporario…` |

**Um teste meu estava fraco, e a mutação o achou.** Com o relógio 5 dias à frente, a mutação do módulo **não** derrubava `relogio anterior a criacao mantem` (5 dias < 30), e só caía num teste vizinho por outro motivo. O teste foi reforçado para 40 dias à frente; com ele a mutação o derruba (P11/P12: o teste era o defeituoso, e se provou pela mensagem).

### Ligações

| Garantia | Mutação | Caiu |
|---|---|---|
| Sair elimina o corpus | chamada removida de `sair` | `sair_elimina_as_amostras_do_corpus` |
| Revogar elimina o corpus | chamada removida de `revogar` | `revogacao_observada_elimina_as_amostras_do_corpus` |
| A falha do corpus não impede sair | removido o `try/catch` | `sair_nao_para_se_a_eliminacao_do_corpus_falha` |
| Nenhum código de rede cita o corpus | `import …corpus.coletaDoCorpus` em `api/ApiPlatos.kt` | `NenhumEnvioLeOCorpusTest` |
| …e a varredura tem piso (P13) | raiz errada | o mesmo teste, na asserção `>= 10` arquivos |
| `ScanActivity.darNota` copia (emulador) | `SemColeta` no lugar de `coleta` | `com_a_coleta_ligada_dar_a_nota_deixa_uma_amostra_por_discursiva` (a `desligada` segue verde) |
| A varredura elimina o corpus (emulador) | bloco removido de `varrerAgora` | `a_varredura_elimina_a_amostra_vencida_e_mantem_a_recente` |

Reversão das duas últimas rodada no emulador: `Starting 3 tests`, `exit 0`.

### Achado da revisão final: a varredura em segundo plano comia a cópia em curso

A revisão (revisor com contexto novo, sobre a branch inteira) achou, em `VarreduraDoAparelho.kt`, que o bloco do corpus **ignorava** `escaneamentoAberto`, que a varredura das respostas, no mesmo método, já recebia. Com o escaneamento aberto, a cópia de `darNota` passa por estados que a regra de resíduo lê como lixo (a foto já no nome final com os dados ainda `.tmp`, ou só a foto): se o worker de 24 h rodasse nessa janela, apagava uma amostra boa, e a cópia terminava como "falha". Falha segura (sem vazamento, sem nota perdida), mas perda silenciosa de dado da coleta, e **não estava registrada em lugar nenhum**. Corrigido com TDD (o requisito foi refinado em `measurement-corpus`: "Cópia em curso não é resíduo"):

| Garantia | Mutação | Caiu |
|---|---|---|
| Com o escaneamento aberto o resíduo fica e a vencida sai | marca ignorada | `com o escaneamento aberto o residuo fica…` |
| …o sentido da marca | marca invertida | o mesmo + `foto sem dados, dados sem foto e temporario sao residuo e saem` |
| …a vencida sai mesmo com a marca aberta | regra de vencida removida | o mesmo + `29 dias mantem, exatamente 30 elimina, 31 elimina` + `arquivo estranho na pasta nao e tocado pelo prazo` |
| `varrerAgora` passa a marca ao corpus (emulador) | argumento removido | `com_o_escaneamento_aberto_a_varredura_poupa_a_copia_em_curso` |

Reversão rodada: `Starting 2 tests`, `exit 0`. **Reexecução depois da correção** (só `apps/android` mudou; `git diff --stat 4c502ef` não toca `apps/api` nem `packages/`): `./gradlew :apps:android:testDebugUnitTest :apps:android:testReleaseUnitTest :apps:android:verificarApkSemColeta :apps:android:connectedDebugAndroidTest --rerun-tasks`, `163 de 163 executadas`, `05:26:43Z`–`05:31:06Z`: debug **488**, release **470**, instrumentados **177 casos, 0 falhas, 2 pulados** (as mesmas sondas), `timestamp 05:31:01Z`; `ColetaNaNotaInstrumentedTest` 2/2 e `VarreduraEliminaOCorpusInstrumentedTest` 2/2. **O agregado cheio com `api` e `domain` não foi repetido depois da correção** (Docker encerrado); o resultado de 184/184 da seção 1 é de antes dela.

### O release não contém a coleta (`verificarApkSemColeta`)

| Mutação | Resultado |
|---|---|
| Coleta real copiada para `src/release` e a fábrica trocada | **FAIL**: `android-release-unsigned.apk (release) contem a coleta do corpus: [classe, coleta-ligada]`. Reversão rodada: `exit 0`, `release: … nenhuma`. |
| Literal `MARCADOR` renomeado no debug (canário) | **FAIL**: `android-debug.apk (debug) nao traz o literal da coleta: a verificacao nao distingue o release do debug`. |

### `tools/corpus` (`node --test`, 13 casos)

| Mutação | Caiu |
|---|---|
| comparação de chaves desligada | `chave a mais ou a menos reprova…` |
| piso (pasta vazia) removido | `pasta sem nenhuma amostra reprova (piso)` |
| `destinoForaDoRepositorio` sempre `true` | `um destino dentro do repositorio e recusado…` |
| referência vazia contada como válida | `so e valida a amostra com referencia preenchida…` |
| validação de nome em `nomesDaListagem` desligada | `a listagem do aparelho que e mensagem de erro nao vira nome de arquivo` |
| **o mesmo literal nos dois lados:** `"extra":0` em `fixtures/corpus/amostra-exemplo.json` | Node: `as chaves do formato saem do literal…`; Kotlin (com `--rerun`): `o formato bate com o literal do repositorio`. Os dois lados prendem o mesmo arquivo (P28). |
| `.gitignore` sem a linha | `git status` lista `?? corpus-de-medicao/`; com a linha, `git check-ignore -v` imprime a regra. |

## 3. A saída por cabo, ponta a ponta, no emulador

Amostras **sintéticas** plantadas em `files/corpus` do APK debug instalado (um JSON = o literal do repositório, uma foto = `fixtures/corpus-3b-prova1-frontal.jpg`, 7.385.843 bytes), e `node tools/corpus/puxar.mjs <destino>`:

- `exit 0`, `2 arquivo(s) puxado(s) … 1 amostra(s)`. **`cmp` idêntico nos dois arquivos**: os bytes atravessaram `exec-out`/`run-as cat` sem corrupção (era o risco). `node tools/corpus/formato.mjs`: `{"amostras":1,"validas":0,"descartadas":0,"semReferencia":1}`, `exit 0`.
- **Ver falhar:** foto removida do aparelho → `exit 1`, `falta a foto`. Destino dentro do repositório → `exit 2`, e **nenhuma pasta criada**. Pasta do aparelho inexistente → ver o achado abaixo.

**Um defeito real do `puxar.mjs`, achado por essa terceira mutação.** `adb exec-out` **perde o status de saída**: com a pasta inexistente, `ls: files/corpus: No such file or directory` chegou em stdout e foi lido como **nome de arquivo** (só não escreveu nada porque `:` não é válido no nome no Windows: `ENOENT`, mensagem sem sentido). Corrigido com TDD: `nomesDaListagem` recusa linha que não seja nome válido e mostra a resposta do aparelho; visto falhar sem a validação; reexecutado no emulador: `::error::o aparelho nao listou a pasta: ls: files/corpus: No such file or directory`, `exit 2`.

## 4. Achados fora do escopo (registrados, não corrigidos — P19)

- **`fixtures/` não é entrada declarada das tarefas `Test` do Gradle.** Editar `fixtures/corpus/amostra-exemplo.json` deixa `testDebugUnitTest` **`UP-TO-DATE`** (`exit 0` com o teste **sem rodar**, P2): a mutação do literal só derrubou o lado Kotlin com `--rerun`. O CI (checkout limpo) não é afetado. Vale para todas as fixtures, não só a nova.
- **O plano errou dois comandos**, corrigidos na execução e no livro-razão: `node --test tools/corpus/` (no Node 24 o diretório é carregado como módulo; vale o glob entre aspas) e os caminhos `/data/local/tmp/...` no Git Bash (exigem `MSYS_NO_PATHCONV=1`; sem ele o `adb push` gravou num caminho do Windows). Um `$HOME` do Git Bash passado ao Node criou, por acidente, `C:\c\Users\Leon\corpus-5d-ensaio`: continha só o ensaio, foi conferido e removido.

## 5. Corpus coletado

**Nenhuma amostra de aluno coletada.** A coleta com turma real é do mantenedor, no fim, com a prova final, e este documento não afirma nada sobre ela. Quando houver, o archive registra: turmas, matérias, instrumento (lápis ou caneta), amostras válidas (`node tools/corpus/formato.mjs`, `validas`) e falhas de cópia (ADR-0022, item 5).

## 6. O que ainda não foi verificado (P8)

- **A coleta com a câmera real em aparelho físico.** Os instrumentados alimentam a `ScanActivity` pelo analisador, sem `CameraX`/`ImageProxy`; o Xiaomi exige o toque manual de permissão. Do mantenedor, no fim.
- **"Cancelar a nota não copia"** é verdade por construção (a cópia só é chamada dentro de `darNota`, na confirmação) e **não tem teste próprio**: conhecido, não mitigado.
- **A leitura do corpus fora de `api/`, `outbox/`, `net/` e `auth/`:** `NenhumEnvioLeOCorpusTest` olha quatro pacotes; um caminho de rede novo em outro pacote não seria visto. `ARespostaNaoSaiDoAparelhoTest` segue afirmando sobre o corpo do envio, mas é a camada vizinha (P16).
- **R8:** não há. O literal `coleta-ligada` sobreviveria a ele; o descritor da classe não. A verificação aceita os dois sinais.
- **Node 22 (CI):** localmente é a v24.19.0; `node --test "glob"` é suportado desde a v21, mas o passo do CI não foi rodado.
- **A regra de extração de dados** (`path="."` no domínio `file`) cobre `corpus/` por construção; `RegrasDeExtracaoInstrumentedTest` passou (5/5), mas não foi estendido a `corpus/` com uma amostra presente.

## 7. Dívida (P27) — o que o archive dirá de cada linha

- **`LGPD com dados de menores` (`antes-de:primeiro-piloto`):** reescrita no archive: letra de menor sai do aparelho **por cabo, só pelo mantenedor, em APK de depuração**; continua **aberta (jurídico externo)**. Não é paga.
- **`Acurácia em manuscrito` (`6`):** esta mudança **coleta**, não mede; segue `6`. O archive diz quantas amostras, de quantas turmas, ou que nenhuma foi coletada.
- **`A política §10.8 diverge do comportamento` (`antes-de:publicacao-da-politica`):** a §10.8 não existe na política v2.0; o archive diz se a linha muda de referência ou sai, conforme o mantenedor decidir.
- **`A guarda de dívida não lê a tabela "Aberto"` (`5`):** não é tomada aqui.
- **`O limiar do OMR … um aparelho e uma impressora` (`6`):** é medida de impressão, não de letra; entra na sessão de papel. Não é paga.
