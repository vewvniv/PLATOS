# Proposal

## Why

A `slice-5c-0` entregou o recorte da resposta discursiva **sem chamador de produção**, e o `proposal.md` desta mudança SHALL abrir com o chamador e com a decisão de onde a imagem mora e por quanto tempo (`docs/cobertura-slice-5c-0-o-recorte-da-resposta.md`, "O que esta mudança NÃO verificou"). É isso que está aqui.

**O chamador.** O quadro da câmera morre no `finally` de `CameraFrameAnalyzer.analyze`, antes de a `ScanSession` ver o resultado. Quem corrige a discursiva depois (a `5c-2`) não terá quadro: a imagem tem de ser tirada no analisador e ficar guardada. Sem isto, a correção manual da §10 ("local, entra no outbox") não tem o que mostrar.

**Onde mora, e por quanto tempo.** No aparelho, em arquivo, referenciada pelo caderno — classe H da política §10.8 ("imagens capturadas […] armazenadas localmente"), e não classe A: a imagem **não sobe** ao servidor nesta fatia (§6.4 da política: "nenhuma imagem sai do dispositivo"; a IA só chega na fatia 8). O prazo é o da classe H: eliminada após a sincronização bem-sucedida e, em qualquer hipótese, em até 30 dias. A sincronização da **imagem** não existe (não há envio dela); o gatilho "após a sincronização" nasce na `5c-2`, com a nota. Até lá, o que elimina é o teto de 30 dias e o caderno que deixa de existir.

**Por que agora, e por que só isto.** O recorte é a geometria mais frágil da fatia 5 (o canto inferior esquerdo é extrapolado, ADR-0018). Usá-lo ao vivo, com a câmera e o caderno de verdade, antes de haver tela de nota por cima, custa uma mudança; depois custa a tela. O corte em duas (5c-1 ver, 5c-2 nota) foi decidido pelo mantenedor na exploração de 2026-09-30, junto com: corpus fora daqui, e recorte recusado impedindo a região de contar como capturada.

## What Changes

- **O analisador pede o recorte** das regiões discursivas reconhecidas que ainda não têm resposta guardada para aquele aluno, e grava o PNG em cinza no armazenamento privado do aplicativo antes de entregar o quadro. `SheetReader.analyze` **não muda**: continua sem efeito e sem imagem (requisito "Reconhecer não recorta").
- **`ScanSession.onFrame` recebe o que o analisador gravou** (parâmetro novo, com valor-padrão vazio, e nenhum teste atual muda de chamada). Região discursiva só passa a **capturada** com resposta guardada; recorte `Recusado` a deixa **com problema**, com o motivo.
- **O `Caderno` passa a referenciar a resposta** de cada região discursiva (nome do arquivo, instante, e o sinal de desvio já classificado pelo domínio). Caderno já guardado, sem o campo, continua legível; discursiva capturada sem resposta volta a não vista na leitura.
- **Completar o caderno exige as respostas.** A entrega da parcial (5b-4) só dispara com todas as regiões capturadas *e* com resposta. **BREAKING** para o requisito de completude, e só para ele.
- **O professor vê a resposta.** O indicador de cada região discursiva capturada abre a imagem em tela cheia, com o número da questão, o aviso de desvio quando sinalizado ("confira a folha de papel") e a ação **Refazer**, que descarta a resposta e devolve a região a não vista.
- **Prazo de 30 dias executável.** Varredura na abertura do aplicativo (a tela de sessão) e do escaneamento, **antes** da câmera e da leitura do caderno: elimina o arquivo que passou de 30 dias e o que nenhum caderno guardado referencia. Eliminar um arquivo que falha não impede o escaneamento de abrir.
- **O texto da tela muda:** "nada foi guardado" deixa de ser verdade. Passa a dizer que nenhum **resultado** foi gravado e que as respostas capturadas ficam neste aparelho.

**Não será alterado** (limite desta mudança):
- **Nota, entrada de nota, fato de correção, outbox, servidor, migration, `result-sync`, `apps/api`, `apps/web`** — é a `5c-2`, e a forma do fato (revisão nova de `grading_result` ou tabela própria) é ADR dela.
- **Nenhum envio de imagem**, e nenhum corpus de medição (§9): mudança própria, que resolve antes a política §12 (dado real de aluno em desenvolvimento).
- `SheetReader.analyze`, `RecorteDaResposta`, `DesvioDaResposta`, os três limiares da 5c-0, o `LayoutMap`, qualquer golden ou fixture.
- `DeviceSession.sair` (segue sem apagar caderno nem resposta: é trabalho não concluído, como o pendente — ver §16, linha da política §10.8).
- Recorte em cor (`answer_capture_mode: color`), cifragem em repouso, varredura periódica em segundo plano, finalizar caderno incompleto.

## Capabilities

### New Capabilities
<!-- Nenhuma: as duas capacidades que a mudança toca já existem. -->

### Modified Capabilities
- `scan-session`: a completude de região discursiva exige resposta guardada; o caderno referencia a resposta; o professor a vê, vê o aviso de desvio e pode refazê-la; a resposta tem prazo de 30 dias no aparelho; o texto "nada foi guardado" é corrigido. Dois requisitos modificados (um renomeado) e dois acrescentados.
- `capture-omr`: um requisito acrescentado — a captura pede o recorte uma vez por região discursiva reconhecida que ainda não tem resposta para o aluno, e não pede a cada quadro. Nenhum requisito existente muda de texto.

## Impact

- **Código Android** (`apps/android`): `scan/` (`CameraFrameAnalyzer`, `ScanSession`, `Caderno`, `ScanScreen`, `ScanState`, `ScanActivity`, e a função `lerCadernoEmAndamento` que a mudança `o-caderno-e-lido-fora-do-fio-principal` criou, agora composta com a varredura e a normalização), `session/SessaoActivity` (só a chamada da varredura), um guardador de respostas em arquivo novo, e um codificador de PNG para `RectifiedRegion`. Nenhuma tecnologia nova (PNG por `Imgcodecs`/`Bitmap`, arquivos em `filesDir`, Compose).
- **Domínio KMP, API, banco, contrato do fio:** nenhum.
- **Privacidade:** passa a existir manuscrito de menor no aparelho. O backup já é negado por domínio de armazenamento (`device-session`, regra de extração, cobre a raiz do diretório de dados); a mudança prova que o diretório novo cai sob essa regra.
- **Dívida (§16, P27).** `node tools/divida/divida.mjs` (2026-10-01, com o #83 na `main`): fatia corrente `5c`, `exit 0`, 23 linhas, nenhuma vencida; a única que "vence nesta fatia" é "A guarda de dívida não lê a tabela 'Aberto'" (`5`), **que esta mudança não toma**. O **archive** diz, em uma frase, que ela segue `5` (limite: a abertura da fatia 6), que a mudança própria que a paga ainda não foi proposta, e que isso precisa acontecer antes do archive da última mudança da fatia 5 — silêncio não é reconciliação (P27). Linhas que esta mudança alcança e que o **archive** reconcilia:
  - "A política §10.8 diverge do comportamento: sair e a revogação preservam o pendente": passa a cobrir também a **resposta guardada** (mesma razão, dado mais sensível).
  - "A região discursiva ainda não passou pelo aparelho nem pelo papel" (`6`): o recorte passa pela câmera ao vivo, não pelo papel — atualizar sem apagar o texto anterior (P7).
  - "O limiar do desvio e o teto do resíduo foram fixados sem letra de aluno" (`6`): a tela passa a mostrar o sinal de desvio, e a linha já dizia que isso encareceria esperar.
  - "LGPD com dados de menores" (`antes-de:primeiro-piloto`): acrescentar que há imagem de manuscrito no aparelho e o que a elimina.
  - **Linha nova**, a abrir nesta mudança: o teto de 30 dias só roda quando o aplicativo abre; aparelho que nunca mais abre não expurga. **Token `5c`** (e não `5c-2`: a gramática da guarda é `<inteiro>[.<inteiro>][<letra>]`, e `5c-2` seria recusado com `exit 2`); a `5c-2` é o veículo que a reconcilia no archive. Custo estimado (um `WorkManager` periódico, que já está em uso no outbox, mais uma trava entre a varredura e o analisador) e dono registrados (P20).
