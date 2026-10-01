# Proposal

## Why

A `5c-2` deixou o servidor pronto para receber a nota do professor (`POST …/results/graded`, `Pontos` decimal exata, revisão corrente), mas **nenhum aparelho a envia**: a imagem da resposta que a `5c-1` guarda só pode ser vista e refeita, e a parcial objetiva nunca é completada. Sem a tela de nota e o envio, a correção manual da §10 ("local, entra no outbox") não existe, e o corpus de medição (§9.2, "a nota não muda") não tem a nota do professor como referência.

É também onde nasce o gatilho "após a sincronização" da classe H, e onde a imagem passa a ter razão de ficar mais tempo no aparelho: o que a linha `5c` do §16 (o teto de 30 dias só roda quando o aplicativo abre) dizia encarecer esperar.

Esta é a segunda das duas mudanças em que o plano dividiu o item 1 (`docs/plano-da-fatia-5-restante.md`): **5c-2 = contrato e servidor (arquivada); 5c-3 = o aparelho.**

## What Changes

Decisões do mantenedor de 2026-10-01 que esta proposta fixa:
- A nota é dada **na hora, sobre o caderno completo corrente**, antes do próximo aluno. Não há memória de mais de um caderno (a regra atual de `scan-session` se mantém).
- Confirmada a nota pelo servidor, **a imagem das respostas é eliminada na hora** (gatilho "após a sincronização" da classe H); o teto de 30 dias continua como rede.

O que muda, em comportamento:
- **Tela de nota (`scan-session`).** Sobre o caderno completo, o professor vê a imagem de cada discursiva e dá a pontuação de cada uma, de 0 até o valor que o pacote declara, fracionária com até 2 casas (as mesmas guardas de `scoring`/`Pontos`, do mesmo código do servidor). Vê o total resultante antes de gravar. A nota cobre **todas** as discursivas da variante de uma vez.
- **A nota vira resultado durável pendente (`result-sync`)**, antes de qualquer rede, na mesma fila, com chave própria e identificando a captura da parcial que completa. O envio usa a rota da `5c-2`; falha transitória é tentada de novo, recusa definitiva não vira laço, e o pendente só sai depois da confirmação — tudo como já é para os demais resultados.
- **Caderno completo sem nota não é substituído em silêncio.** Se a folha de outro aluno aparece enquanto o caderno corrente está completo e sem nota, a sessão pede confirmação antes de descartá-lo (as respostas se perderiam).
- **Caderno corrigido deixa de ser caderno em andamento.** Gravada a nota, o caderno fica corrigido: não volta a incompleto quando a imagem é eliminada e não reentrega a parcial.
- **Eliminação da imagem após a confirmação da nota.** Nota gravada mas ainda não confirmada **mantém** a imagem. Recusa definitiva não a elimina.
- **Teto de 30 dias em segundo plano (paga a linha `5c`).** A eliminação passa a rodar também por agendamento periódico, sem depender de o aplicativo abrir, com uma trava para nunca eliminar o que o escaneamento aberto está gravando.

**Premissas minhas, para a revisão** (não perguntadas ao mantenedor):
1. Corrigir uma nota **já gravada** não existe nesta mudança. Depois da confirmação a imagem some; refazer a nota exige nova captura (revisão nova pelas regras da `5c-2`).
2. Folha do mesmo aluno lida de novo depois de corrigida começa **captura nova** (recorte pedido de novo), nos termos de `result-sync` ("captura nova").
3. Caderno de prova só objetiva não muda; não há tela de nota para ele.
4. Descartar um caderno completo sem nota é ato explícito do professor, e fica dito na tela que as respostas se perdem.
5. O intervalo da eliminação em segundo plano é de **ao menos uma vez por dia**: o teto é de 30 dias, e o sistema operacional pode adiar a execução.

**Não será alterado** (limite desta mudança):
- Servidor, `apps/api`, banco, contrato do fio (`GradedResultSubmissionDto`) e a regra "revisão humana vence": são da `5c-2`. Se o aparelho achar que o contrato precisa mudar, **pare e `/opsx:update`**.
- Nenhuma imagem sobe, nem referência a arquivo (política §6.4).
- Resultado objetivo, parcial e a rota atual; nota por critério de rubrica, por habilidade, origem `ai`/`text`; `apps/web`; o corpus de medição.
- **Finalizar caderno incompleto** (`slice-5e-…`): a nota exige o caderno completo.
- Sair da sessão e a revogação do vínculo continuam sem eliminar resposta nem pendente.

## Capabilities

### New Capabilities
<!-- Nenhuma: as duas capabilities que a mudança toca já existem. -->

### Modified Capabilities
- `scan-session`: dois requisitos acrescentados (o professor dá a nota sobre a imagem, na hora; o caderno completo sem nota não é substituído em silêncio) e um modificado — a resposta guardada tem prazo no aparelho (eliminação após a confirmação da nota e em segundo plano, com trava).
- `result-sync`: um requisito acrescentado (a nota do professor vira resultado durável pendente e é enviada pela rota própria) e um modificado — o corpo da nota não leva imagem, e agora o aparelho elimina a imagem depois da confirmação, o que o texto da `5c-2` ("não eliminá-la") não previa.

## Impact

- **Android** (`apps/android`): tela de nota e a leitura da imagem (Compose, sobre a tela de resposta da `5c-1`), o caderno com o estado "corrigido" e a captura da parcial que ele completa, a gravação da nota no outbox e o envio pela rota `graded` (`ktor-client`), o `WorkManager` periódico da eliminação e a trava entre ele e o analisador. Nenhuma tecnologia nova (o `WorkManager` já está em uso no outbox).
- **Domínio KMP** (regra 7): `lerPontos`, hoje no servidor, sobe para o domínio, e `ApuracaoParaEnvio` ganha o subtipo da nota (o ADR-0021 o adiou "até haver consumidor"). O aparelho usa o **mesmo** código que o servidor valida.
- **Paridade:** a 5c-2 deixou um teste de literal do contrato em `apps/android/src/test`; passa a haver consumidor, e `tools/parity/fio.mjs` continua exigindo o literal nos dois lados. Teste no aparelho real é pelo Leon (toque manual de permissão); sem papel nesta mudança.
- **Privacidade:** a nota é classe B, sem nome, turma ou matrícula (I5); a imagem é classe H e passa a ter eliminação por evento.
- **Dívida (§16, P27).** `node tools/divida/divida.mjs`: fatia corrente `5c`; o nome `slice-5c-3-…` não a move. Linhas que esta mudança alcança, e o que o **archive** diz de cada uma:
  - **`O teto de 30 dias das respostas só roda quando o aplicativo abre` (`5c`)**: **paga aqui** (agendamento periódico e trava); o archive a retira e diz o que ficou de fora, se algo.
  - **`A política §10.8 diverge do comportamento: sair e a revogação preservam o pendente`**: passa a dizer que a imagem some ao confirmar a nota.
  - **`Migration não é aplicada por nenhum pipeline` (`antes-de:migration-da-5-em-producao`)** e **`Implantar a API da 5a …` (`antes-de:implantar-api-da-5a`)**: o aparelho passa a **chamar** a rota nova; nenhuma das duas vai a produção por esta mudança, e o archive registra que o consumidor existe e os eventos seguem por declarar.
  - **`A retenção executável da classe B`** (`antes-de:primeira-eliminacao`): sem prazo novo.
  - **`A guarda de dívida não lê a tabela "Aberto"` (`5`)**: **não é tomada aqui**; segue `5`, limite a abertura da fatia 6, mudança própria ainda não proposta — silêncio não é reconciliação.
  - **`A região discursiva ainda não passou pelo aparelho nem pelo papel`** e **`O limiar do desvio … sem letra de aluno`** (`6`): a tela de nota usa o sinal de desvio e a região passa pela câmera, não pelo papel; sem reagendamento.
