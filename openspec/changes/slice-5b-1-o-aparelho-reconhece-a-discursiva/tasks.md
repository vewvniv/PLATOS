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
  e a lê. **Ver falhar:** sem o filtro, o teste cai com "declara 8 ArUcos; esperados 4". Reverter e
  rodar.
- [x] 1.2 Os marcadores são detectados uma vez por quadro, e cada região com os quatro `marker_ids`
  encontrados é lida (decisão 1). O resultado do quadro passa a ser por região (decisão 3). A região
  discursiva é retificada e tem o QR conferido, sem medição de bolha. Verificação: testes para os
  cenários da ADDED "A captura identifica as regiões presentes…" (duas regiões, só a discursiva, região
  pela metade) e da ADDED "A região discursiva é reconhecida…". Os documentos são renderizados da
  fixture discursiva, sem foto.
- [x] 1.3 A prova só objetiva não muda de fora. Verificação: `CorpusInstrumentedTest` e os testes
  existentes de `RegionDetector` e `SheetReader` passam sem mudar de sentido. Os ajustes de assinatura
  são listados na cobertura.

## 2. A sessão (`scan-session`)

- [ ] 2.1 A sessão decide "prova com discursiva" por `fully_offline_gradable` do pacote (decisão 4) e
  entra no estado novo:
  - quem é o aluno;
  - o que foi reconhecido;
  - a frase de que a correção ainda não está disponível e de que nada foi guardado.

  Verificação: um teste em `ScanSessionTest` por cenário da ADDED de `scan-session`. Cada teste confere
  a **frase** e o **aluno**, e não só o tipo do estado.
- [ ] 2.2 **Ver falhar**, com duas mutações de conjuntos disjuntos previstos:
  - a sessão apura a prova com discursiva como objetiva: cai o cenário da folha no quadro (vira recusa
    por divergência), e "nada é gravado" continua verde, porque recusa não grava;
  - o estado novo devolve apuração: cai **só** "nada é gravado".

  Reverter e rodar.
- [ ] 2.3 A tela desenha o estado novo (`ScanScreen`). Verificação: o build compila, e a tela é
  conferida na 4.2 ou fica escrita como lacuna (decisão 5).

## 3. A queda

- [ ] 3.1 A montagem do analisador sai da `ScanActivity` para uma função que a `Activity` e o teste
  chamam, sem região pré-escolhida (decisão 5). Verificação: um teste monta o analisador com o mapa da
  fixture discursiva e não recebe exceção. `grep` não acha `regions.single()` em
  `apps/android/src/main`. **Ver falhar:** com o `.single()` restaurado na função, o teste cai com
  `IllegalArgumentException`. Reverter e rodar.

## 4. A folha discursiva impressa (decisão 6)

- [ ] 4.1 **Tarefa do mantenedor:**
  - imprimir `build/parity/discursiva-aluno-web.pdf`, a folha de `tok-a`, gerada nesta sessão;
  - marcar as quatro objetivas e escrever nas duas molduras;
  - **antes de fotografar**, anotar as marcações em `fixtures/corpus-5b-marcacoes.json`;
  - fotografar as duas folhas, de frente e em ângulo.

  Verificação: as quatro fotos em `fixtures/corpus-5b-*.jpg`, e o EXIF delas sem coordenada de GPS,
  conferido pelo leitor de EXIF da abertura da fatia 5. Se houver GPS, a coordenada é removida antes
  do commit, e a cobertura diz isso.
- [ ] 4.2 Teste instrumentado que lê as quatro fotos pelo caminho de produção. Ele afirma as regiões
  reconhecidas por foto, as respostas do gabarito iguais às marcações anotadas, e o `tok-a` com o
  índice certo no QR de cada região discursiva. **Regra de parada:** foto não reconhecida fica no
  conjunto, com o motivo registrado, e a mudança para. Não se troca foto. **Ver falhar**, com dois
  conjuntos disjuntos:
  - sem o filtro da 1.1: caem as fotos da primeira folha, e as da segunda não, porque a página 1 tem
    só a região de `d2`;
  - com a região escolhida sempre como a 0: caem as fotos da segunda folha, e as da primeira não.

  Reverter e rodar.
- [ ] 4.3 **De ponta a ponta no celular do mantenedor, se couber** (decisão 7):
  - publicar a fixture discursiva na organização de conferência;
  - instalar o build desta mudança no celular e puxar a prova;
  - escanear a folha impressa: a câmera abre, e a tela diz o aluno, as regiões e a frase.

  Verificação: capturas de tela com horário. **Se não couber, a tarefa fica desmarcada**, e a cobertura
  diz que a tela não foi conferida.

## 5. Fechamento

- [ ] 5.1 `grep -rn "MUTACAO"` fora de `build/` e de `node_modules/` dá `0`, e toda reversão foi
  rodada (P10).
- [ ] 5.2 Comando cheio local:
  - `./gradlew build --rerun-tasks` e `connectedDebugAndroidTest`, sem filtro;
  - `vitest` e o `build` do web;
  - as guardas Node.

  Contagens e `timestamp` comparados com a 0.1 (P2, P3, P5).
- [ ] 5.3 `docs/cobertura-slice-5b-1-o-aparelho-reconhece-a-discursiva.md`:
  - como cada verificação foi vista falhar, com o previsto e o real;
  - as fotos, com `sha256` e EXIF;
  - a seção "o que ainda não foi verificado".
- [ ] 5.4 PR contra `main`, e o CI **lido no destino** (P2, P26).
- [ ] 5.5 Preparar a reconciliação do archive (P27):
  - a linha `5b` do §16 **paga**, com a evidência (a queda consertada, o motivo certo, as fotos lidas
    e, se houve, a 4.3). O token ganha `paga`, e a prosa da linha fica (P7);
  - as três linhas `5` seguem em dia;
  - os eventos `migration-da-5-em-producao` e `implantar-api-da-5a` não foram alcançados.
