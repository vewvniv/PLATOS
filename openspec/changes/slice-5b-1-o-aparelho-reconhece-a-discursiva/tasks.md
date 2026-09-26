## 0. Antes do primeiro commit

- [x] 0.1 Linha de base na árvore de `main`:
  - `./gradlew build --rerun-tasks` e `./gradlew :apps:android:connectedDebugAndroidTest`, sem filtro,
    no `platos-atd34`;
  - `npx vitest run` em `apps/web`.

  Anotar em `docs/cobertura-slice-5b-1-o-aparelho-reconhece-a-discursiva.md` a contagem de testes por
  suíte, o `timestamp` de cada relatório e as tasks **executadas** (P2, P3). Verificação: os relatórios
  são desta sessão.
- [x] 0.2 Registrar a saída de `node tools/divida/divida.mjs`: "fatia corrente: 5b", e as quatro linhas
  sob "vence nesta fatia". Verificação: a saída na cobertura, com `exit 0`.

## 1. A captura por região (`capture-omr`)

- [x] 1.1 `declaredMarkersOf` filtra pelos `marker_ids` da região (decisão 2). Verificação: um teste
  instrumentado retifica a região 0 da fixture discursiva, que divide a página 0 com a região de `d1`,
  e a lê. **Ver falhar:** sem o filtro, o teste cai com "declara 6 ArUcos; esperados 4". Reverter e
  rodar.

  **Reaberta ao aplicar `/opsx:update`, em 2026-09-25, depois do archive da `slice-5b-0-a-regiao-
  discursiva-compacta`.** Estava `[x]`, verificada contra a fixture antiga, de região discursiva com
  quatro marcadores (página 0 declarava 8 ArUcos). A 5b-0 reduziu a região discursiva a dois
  marcadores, na diagonal: a página 0 da fixture atual declara **6** (gabarito, 4, mais `d1`, 2). O
  "ver falhar" muda de "declara 8" para "declara 6", e a tarefa precisa ser reexecutada contra a
  fixture nova antes de voltar a `[x]`.

  **Reexecutada em 2026-09-26**, sobre a `main` com a 5b-0 (merge `728f832`): vista falhar com
  "declara 6 ArUcos; esperados 4", e a reversão rodada. Registro na cobertura, seção "1.1 a 1.3
  reexecutadas".
- [x] 1.2 Os marcadores são detectados uma vez por quadro, e cada região com **todos** os seus
  `marker_ids` declarados encontrados é lida (decisão 1) — quatro para o gabarito, dois para a região
  discursiva. O resultado do quadro passa a ser por região (decisão 3). A região discursiva é
  retificada e tem o QR conferido, sem medição de bolha. Verificação: testes para os cenários da ADDED
  "A captura identifica as regiões presentes…" (duas regiões, só a discursiva, região pela metade) e
  da ADDED "A região discursiva é reconhecida…". Os documentos são renderizados da fixture discursiva,
  sem foto.

  **Reaberta ao aplicar `/opsx:update`, em 2026-09-25, pelo mesmo motivo da 1.1.** Os cenários "Duas
  regiões no mesmo quadro", "Só a região discursiva no quadro" e "Região pela metade" passam a exigir
  dois marcadores para a região discursiva, não quatro, e a execução anterior foi contra a geometria
  antiga.

  **Reexecutada em 2026-09-26**, com a homografia da região de dois marcadores decidida pelo
  mantenedor antes do código (decisão 1 do `design.md`, atualização de 2026-09-26). Duas mutações
  vistas falhar, com o previsto igual ao real, e as duas revertidas e rodadas.
- [x] 1.3 A prova só objetiva não muda de fora. Verificação: `CorpusInstrumentedTest` e os testes
  existentes de `RegionDetector` e `SheetReader` passam sem mudar de sentido. Os ajustes de assinatura
  são listados na cobertura.

  **Reaberta ao aplicar `/opsx:update`, em 2026-09-25, pelo mesmo motivo da 1.1 e da 1.2:** é
  verificação de regressão sobre `RegionDetector` e `SheetReader`, que a 1.1 e a 1.2 vão reexecutar.

  **Reexecutada em 2026-09-26:** `testDebugUnitTest --rerun` com 320 testes e 0 falhas, e
  `connectedDebugAndroidTest` sem filtro com 89 testes, 0 falhas e 2 pulados, `timestamp` 09:12:25Z.

## 2. A sessão (`scan-session`)

- [x] 2.1 A sessão decide "prova com discursiva" por `fully_offline_gradable` do pacote (decisão 4) e
  entra no estado novo:
  - quem é o aluno;
  - o que foi reconhecido;
  - a frase de que a correção ainda não está disponível e de que nada foi guardado.

  Verificação: um teste em `ScanSessionTest` por cenário da ADDED de `scan-session`. Cada teste confere
  a **frase** e o **aluno**, e não só o tipo do estado.
- [x] 2.2 **Ver falhar** "nada é gravado" camada por camada. Ele tem **duas** proteções: a sessão, que
  não apura a prova com discursiva, e o domínio (`ObjectiveScoring`), que a recusa (8.1 da 5a).
  - **M-a**, a sessão apura a prova com discursiva como objetiva: cai o cenário da folha no quadro,
    que vira recusa por divergência, e "nada é gravado" continua verde, porque o domínio recusa;
  - **M-c**, só o domínio passa a aceitar leitura que é subconjunto do declarado: nada cai, porque a
    sessão não o chama para esta prova;
  - **M-a e M-c juntas:** cai "nada é gravado".

  Reverter e rodar.

  *Corrigido ao executar (P7). A redação original pedia a mutação "o estado novo devolve apuração",
  com "nada é gravado" caindo sozinho. Ela não pode ser montada: uma prova com discursiva não tem
  `ObjectiveScore` para devolver. A proteção é em duas camadas, e é isso que as três mutações mostram.*
- [x] 2.3 A tela desenha o estado novo (`ScanScreen`). Verificação: o build compila, e a tela é
  conferida na 4.2 ou fica escrita como lacuna (decisão 5).

## 3. A queda

- [x] 3.1 A montagem do analisador sai da `ScanActivity` para uma função que a `Activity` e o teste
  chamam, sem região pré-escolhida (decisão 5). Verificação: um teste monta o analisador com o mapa da
  fixture discursiva e não recebe exceção. `grep` não acha `regions.single()` em
  `apps/android/src/main`. **Ver falhar:** com o `.single()` restaurado na função, o teste cai com
  `IllegalArgumentException`. Reverter e rodar.

## 4. A folha discursiva impressa (decisão 6)

> **Pausada em 2026-09-25, por decisão do mantenedor, antes de a 4.1 imprimir.** O texto das
> tarefas 4.1 a 4.3 fica como estava (P7).
>
> **O que aconteceu:** o mantenedor viu `discursiva-aluno-web.pdf` e reprovou a disposição **antes**
> do papel. A revisão virou os ADR-0016 a ADR-0019 (PR #69):
> - a pauta de 7 mm, em cinza claro;
> - linhas e largura declaradas pelo professor;
> - a região discursiva com dois ArUcos na diagonal e o QR ancorando o terceiro canto;
> - a paginação que redistribui as questões.
>
> **Por que pausar:** fotografar a geometria de quatro marcadores de 14 mm pagaria a linha `5b` do
> §16 com uma folha que vai ser descartada. A região de dois ArUcos ficaria, de novo, sem papel.
>
> **O que a retomada espera:**
> 1. a mudança de layout que produz a região discursiva nova na fixture;
> 2. esta mudança atualizada (`/opsx:update`) para reconhecer a região de dois ArUcos. A decisão 1
>    ("os quatro `marker_ids`") e os conjuntos de "ver falhar" da 4.2 mudam de texto nesse momento, e
>    não agora.
>
> **O prazo da linha `5b` não muda.** Ela vence quando a 5c abrir, e a 5c não abre antes disto.
>
> **Item 1 pago em 2026-09-25:** a `slice-5b-0-a-regiao-discursiva-compacta` foi arquivada, com a
> região discursiva de dois ArUcos na diagonal, marcador nominal 11,2 mm, retângulo de referência pelo
> canto externo e pauta cinza. **Item 2, esta atualização (`/opsx:update`):** a decisão 1 e a spec de
> `capture-omr` passam a exigir todos os `marker_ids` que a região declara — quatro para o gabarito,
> dois para a discursiva —, e não mais quatro fixo. As tarefas 1.1 a 1.3, que tinham sido verificadas
> contra a geometria de quatro marcadores por região discursiva, voltam a `[ ]`: a página 0 da fixture
> agora declara 6 ArUcos (gabarito + `d1`), não 8. Os conjuntos de "ver falhar" da 4.2 são sobre
> topologia de página (uma região por página vs. duas), não sobre contagem de marcador, e por isso não
> mudam de texto. A 4.1 continua sendo a tarefa do mantenedor, e mede a detecção do marcador de
> 11,2 mm, como já prevista.
>
> **Correção, em 2026-09-26 (P7):** "foi arquivada", acima e na reabertura da 1.1, está errado. A 5b-0
> está completa e mergeada na `main` (PR #70), e **não arquivada**: continua em `openspec/changes/`
> (`openspec list`). O item 1 está pago do mesmo jeito, porque a geometria nova está na `main`.
>
> **Em 2026-09-26, a impressão da 4.1 não coube**, por decisão do mantenedor. As tarefas 4.1 a 4.3
> seguem abertas, e o fechamento (seção 5) espera por elas.
>
> **Movidas em 2026-09-26, por decisão do mantenedor (`/opsx:update`), para a sessão única de papel
> antes da fatia 6.** Isto substitui o parágrafo anterior.
> - **Por quê:** o mantenedor não tem impressora, e cada impressão custa um deslocamento. Toda
>   conferência em papel passa a ser a etapa final, numa ida só (decisão 6 do `design.md`,
>   atualização).
> - **O que muda nas tarefas:** 4.1 a 4.3 deixam de ser tarefas desta mudança, e **não** foram feitas.
>   O texto delas fica abaixo, sem caixa de seleção, como rascunho do protocolo daquela sessão.
> - **O §16:** a linha `5b` é reagendada para `6` no archive (5.5).
>
> Duas decisões do mantenedor, encerradas: o gabarito fica com 4 ArUcos, e a discursiva com 2 e o QR.
> O gabarito compacto fica fora por ora.

- **4.1 (movida para a sessão única de papel)** **Tarefa do mantenedor:**
  - imprimir `build/parity/discursiva-aluno-web.pdf`, a folha de `tok-a`, gerada nesta sessão;
  - marcar as quatro objetivas e escrever nas duas molduras;
  - **antes de fotografar**, anotar as marcações em `fixtures/corpus-5b-marcacoes.json`;
  - fotografar as duas folhas, de frente e em ângulo.

  Verificação: as quatro fotos em `fixtures/corpus-5b-*.jpg`, e o EXIF delas sem coordenada de GPS,
  conferido pelo leitor de EXIF da abertura da fatia 5. Se houver GPS, a coordenada é removida antes
  do commit, e a cobertura diz isso.
- **4.2 (movida para a sessão única de papel)** Teste instrumentado que lê as quatro fotos pelo caminho de produção. Ele afirma as regiões
  reconhecidas por foto, as respostas do gabarito iguais às marcações anotadas, e o `tok-a` com o
  índice certo no QR de cada região discursiva. **Regra de parada:** foto não reconhecida fica no
  conjunto, com o motivo registrado, e a mudança para. Não se troca foto. **Ver falhar**, com dois
  conjuntos disjuntos:
  - sem o filtro da 1.1: caem as fotos da primeira folha, e as da segunda não, porque a página 1 tem
    só a região de `d2`;
  - com a região escolhida sempre como a 0: caem as fotos da segunda folha, e as da primeira não.

  Reverter e rodar.
- **4.3 (movida para a sessão única de papel)** **De ponta a ponta no celular do mantenedor, se
  couber** (decisão 7):
  - publicar a fixture discursiva na organização de conferência;
  - instalar o build desta mudança no celular e puxar a prova;
  - escanear a folha impressa: a câmera abre, e a tela diz o aluno, as regiões e a frase.

  Verificação: capturas de tela com horário. **Se não couber, a tarefa fica desmarcada**, e a cobertura
  diz que a tela não foi conferida.

## 5. Fechamento

- [x] 5.1 `grep -rn "MUTACAO"` fora de `build/` e de `node_modules/` dá `0`, e toda reversão foi
  rodada (P10).
- [x] 5.2 Comando cheio local:
  - `./gradlew build --rerun-tasks` e `connectedDebugAndroidTest`, sem filtro;
  - `vitest` e o `build` do web;
  - as guardas Node.

  Contagens e `timestamp` comparados com a 0.1 (P2, P3, P5).

  *Ponto de controle rodado em 2026-09-26, antes da 4.x, tudo verde (cobertura, "Ponto de controle de
  2026-09-26"). Ele não fecha a 5.1 nem a 5.2, porque a 4.2 ainda acrescenta teste e mutação.*
  *Atualizado em 2026-09-26: a 4.2 saiu desta mudança. A 5.1 e a 5.2 são rodadas de novo, depois do
  commit desta atualização, e fecham sobre a árvore final.*
- [x] 5.3 `docs/cobertura-slice-5b-1-o-aparelho-reconhece-a-discursiva.md`:
  - como cada verificação foi vista falhar, com o previsto e o real;
  - as fotos, com `sha256` e EXIF *(movido em 2026-09-26 para a sessão única de papel; aqui, a
    cobertura diz que nenhuma foto foi lida)*;
  - a seção "o que ainda não foi verificado".
- [ ] 5.4 PR contra `main`, e o CI **lido no destino** (P2, P26).
- [x] 5.5 Preparar a reconciliação do archive (P27):
  - a linha `5b` do §16 **paga**, com a evidência (a queda consertada, o motivo certo, as fotos lidas
    e, se houve, a 4.3). O token ganha `paga`, e a prosa da linha fica (P7);
  - as três linhas `5` seguem em dia;
  - a linha `A folha de teste de impressão não aprova a região discursiva…` (`5b`), acrescentada pela
    5b-0, é alcançada e não paga por esta mudança. Ela é paga por outro veículo ou reagendada, com
    fatia-limite nova e motivo (acrescentado em 2026-09-26; proposta, "Linhas do §16");
  - os eventos `migration-da-5-em-producao` e `implantar-api-da-5a` não foram alcançados.

  **Atualizado em 2026-09-26, por decisão do mantenedor (`/opsx:update`).** O primeiro e o terceiro
  itens acima passam a ser:
  - **a linha `5b` do §16 é reagendada para `6`, e não paga.** O token passa de `5b` a `6`, e a prosa
    ganha o motivo: o mantenedor não tem impressora, e o papel vai para a sessão única antes da 6. A
    prosa ganha também a evidência parcial, que é a queda consertada, o motivo certo e as regiões
    reconhecidas no documento renderizado. O veículo nomeado é a sessão única de papel;
  - **a linha da folha de teste (`5b`) é reagendada para `6`**, pelo mesmo motivo e com o mesmo
    veículo. A aprovação da folha de teste é em papel;
  - **a prosa das duas diz que dois registros apontam para "a 4.1 retomada da 5b-1".** São o
    comentário de `EssayGeometry.kt`, sobre a medição do marcador de 11,2 mm, e a regra de parada da
    decisão 1 da 5b-0. Os dois passam a valer para a sessão única, e a correção deles fica com ela
    (P19).
