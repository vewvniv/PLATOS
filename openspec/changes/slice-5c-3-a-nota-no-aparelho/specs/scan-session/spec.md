## ADDED Requirements

### Requirement: O professor dá a nota das discursivas sobre a imagem, na hora

Quando o caderno de uma prova com discursiva está **completo** (todas as regiões capturadas, com as respostas guardadas) e ainda sem nota, a sessão SHALL oferecer **dar a nota**. A tela SHALL mostrar, para cada questão discursiva da variante, a imagem guardada da resposta, identificada pelo **número que a questão tem na folha impressa** (e com o aviso de desvio quando a resposta foi sinalizada, como em "A resposta capturada fica no aparelho, e o professor a vê"), e SHALL pedir a pontuação dela, **de 0 até o valor que o pacote da sessão declara**, fracionária com **até 2 casas decimais**. As guardas de pontuação SHALL ser as de `scoring` ("A nota do professor completa a parcial sem recalcular a parte objetiva"), aplicadas pelo **mesmo** código que o servidor usa para conferir: pontuação negativa, acima do valor, com mais de 2 casas ou não numérica SHALL ser recusada na tela, dizendo a questão e o motivo.

A nota SHALL cobrir **todas** as discursivas da variante de uma vez: a tela SHALL NOT permitir gravar com alguma sem pontuação. Antes de gravar, a tela SHALL mostrar o **total** resultante (os pontos objetivos da parcial mais as discursivas) sobre o máximo da prova, e SHALL pedir confirmação. Confirmada, a nota SHALL ser gravada como resultado durável pendente (`result-sync`: "A nota do professor vira resultado durável pendente e é enviada pela rota própria"), e o caderno passa a **corrigido**.

**Caderno corrigido deixa de ser caderno em andamento.** Ele SHALL NOT voltar a incompleto quando as imagens são eliminadas, SHALL NOT entregar a parcial de novo, e SHALL NOT oferecer dar a nota outra vez. A mesma folha lida de novo depois disso começa uma **captura nova**, com o recorte pedido de novo.

A tela SHALL NOT alterar a imagem da resposta, e SHALL NOT enviá-la. Prova só objetiva SHALL NOT ter tela de nota e SHALL continuar sendo apurada e gravada como antes.

#### Scenario: Dar a nota sobre o caderno completo

- **WHEN** o caderno de uma prova com duas discursivas completa e o professor abre a nota
- **THEN** a tela mostra a imagem de cada discursiva com o número impresso da questão e pede uma pontuação para cada uma, dentro da faixa que o pacote declara

#### Scenario: Pontuação fracionária

- **WHEN** o professor dá 1.5 a uma discursiva e 1.75 a outra
- **THEN** a tela aceita, mostra o total exato, e a nota gravada leva exatamente 1.5 e 1.75

#### Scenario: Pontuação inválida é recusada na tela

- **WHEN** o professor dá a uma discursiva uma pontuação acima do valor do pacote, negativa, com 3 casas ou não numérica
- **THEN** a tela a recusa nomeando a questão e o motivo, e nada é gravado

#### Scenario: Discursiva sem pontuação

- **WHEN** o professor tenta gravar com uma discursiva sem pontuação
- **THEN** a tela não grava e diz qual falta

#### Scenario: A nota vira pendente e o caderno, corrigido

- **WHEN** o professor confirma o total
- **THEN** o resultado é gravado como pendente na fila de envio, e o caderno passa a corrigido sem voltar a incompleto nem reentregar a parcial

#### Scenario: Caderno incompleto não tem nota

- **WHEN** o caderno de um aluno ainda tem região não capturada
- **THEN** a tela não oferece dar a nota

#### Scenario: Prova só objetiva não muda

- **WHEN** a sessão de uma prova só objetiva lê uma folha
- **THEN** a nota é apurada, apresentada e entregue para gravar, como antes, sem tela de nota

#### Scenario: A mesma folha lida de novo depois de corrigida

- **WHEN** a folha de um aluno cujo caderno foi corrigido é escaneada de novo
- **THEN** começa uma captura nova e o recorte das respostas é pedido de novo

### Requirement: O caderno completo sem nota não é substituído em silêncio

Enquanto o caderno corrente está **completo e sem nota**, a folha de **outro** aluno SHALL NOT substituí-lo sem confirmação do professor: a sessão SHALL dizer que o caderno do aluno corrente aguarda nota e que **descartá-lo perde as respostas capturadas**, e oferecer **dar a nota** ou **descartar e seguir**. Enquanto a escolha não é feita, a sessão SHALL NOT iniciar o caderno do outro aluno. Descartar SHALL ser ato explícito do professor; ao descartar, o caderno e as respostas se perdem como já se perde o caderno incompleto substituído, e nada é gravado nem enviado.

A regra vale só para o caderno completo sem nota. O caderno incompleto substituído por outro aluno continua se perdendo como em "A completude da folha do aluno é mostrada por região", e o caderno corrigido não pede confirmação.

#### Scenario: Outro aluno aparece com a nota por dar

- **WHEN** a folha de outro aluno é reconhecida enquanto o caderno corrente está completo e sem nota
- **THEN** a sessão não inicia o caderno novo, diz que descartar perde as respostas, e oferece dar a nota ou descartar e seguir

#### Scenario: Descartar e seguir

- **WHEN** o professor escolhe descartar e seguir
- **THEN** o caderno e as respostas dele são perdidos, nenhum resultado é gravado, e o caderno do outro aluno começa

#### Scenario: Dar a nota e seguir

- **WHEN** o professor escolhe dar a nota, e a grava
- **THEN** o caderno fica corrigido, e a folha do outro aluno inicia o caderno novo sem confirmação

#### Scenario: Incompleto continua se perdendo

- **WHEN** a folha de outro aluno substitui um caderno que ainda não completou
- **THEN** ele é substituído sem pedir confirmação, como antes desta mudança

## MODIFIED Requirements

### Requirement: A resposta guardada tem prazo no aparelho

A resposta guardada é dado de classe H (`docs/legal/politica-de-privacidade.md` §10.8; ADR-0012, decisão 4): **manuscrito de menor no dispositivo do professor**. O aplicativo SHALL eliminar do aparelho:
- toda resposta guardada há **30 dias ou mais**, contados da captura, em qualquer hipótese (a classe H diz "em até 30 dias", e o dia 30 já é o limite);
- toda resposta que **nenhum caderno guardado no aparelho referencia** — a do caderno substituído por outro aluno, a descartada ao refazer, a de um quadro cuja gravação não chegou a ser referenciada, **e o arquivo temporário de uma gravação interrompida**;
- toda resposta de um caderno **corrigido** cuja nota o **servidor confirmou** (o gatilho "após a sincronização" da classe H, que nasce com a nota).

A eliminação por prazo e por falta de referência SHALL rodar na abertura do aplicativo (a tela de sessão, que é a porta de entrada) e na abertura do escaneamento, **antes** de a câmera abrir e antes de o caderno guardado ser lido, **e também por agendamento periódico em segundo plano**, programado para rodar **ao menos uma vez por dia**, sem depender de o aplicativo abrir: aparelho que guarda a resposta e nunca mais abre o aplicativo SHALL expurgá-la dentro do prazo. A eliminação SHALL NOT eliminar resposta referenciada por caderno guardado que tenha menos de 30 dias. O relógio é o do aparelho. A eliminação SHALL NOT depender de o aluno, a prova ou a organização estarem na sessão corrente: o aparelho é compartilhado entre escolas. **Eliminar um arquivo que falha SHALL NOT impedir o escaneamento de abrir nem as outras eliminações**: o arquivo fica para a próxima eliminação, e a falha é contada.

**A eliminação em segundo plano SHALL NOT eliminar o arquivo que o escaneamento aberto está gravando ou acaba de gravar e ainda não referenciou no caderno**: ela o deixa para a próxima eliminação. Eliminar cedo demais um arquivo que ninguém ainda referencia SHALL NOT corromper o caderno nem a gravação em curso.

A nota **gravada e ainda não confirmada** pelo servidor SHALL NOT eliminar a resposta, e a recusa definitiva da nota também não: a imagem só sai pela confirmação, pelo prazo ou por não ser referenciada. Sair da sessão do aparelho **e a revogação do vínculo** SHALL NOT eliminar a resposta (é trabalho não concluído, como o resultado pendente e o caderno, que já sobrevivem aos dois caminhos), e enviar a **parcial** SHALL NOT eliminá-la: a correção discursiva ainda precisa da imagem.

Resposta eliminada de caderno **em andamento** SHALL devolver a região a não vista, e SHALL NOT deixar caderno apontando para arquivo inexistente como se ele existisse (requisito "A completude da folha do aluno é mostrada por região"). A eliminação das respostas de um caderno **corrigido** SHALL NOT o devolver a incompleto (requisito "O professor dá a nota das discursivas sobre a imagem, na hora").

#### Scenario: Resposta com 31 dias

- **WHEN** a eliminação roda e uma resposta foi capturada há 31 dias
- **THEN** o arquivo é eliminado, e o caderno que a referenciava é lido com a região não vista

#### Scenario: Resposta com exatamente 30 dias

- **WHEN** a eliminação roda e uma resposta foi capturada há exatamente 30 dias
- **THEN** o arquivo é eliminado

#### Scenario: Resposta com 29 dias é mantida

- **WHEN** a eliminação roda e uma resposta referenciada por um caderno guardado foi capturada há 29 dias
- **THEN** o arquivo é mantido, e a região continua capturada

#### Scenario: Resposta do caderno substituído

- **WHEN** a folha de outro aluno substituiu o caderno, o aplicativo foi fechado e reaberto
- **THEN** as respostas do caderno substituído são eliminadas, e as do caderno corrente são mantidas

#### Scenario: Arquivo que nenhum caderno referencia

- **WHEN** o aplicativo terminou depois de gravar uma resposta e antes de guardar o caderno que a referenciaria, e é reaberto
- **THEN** o arquivo é eliminado na abertura

#### Scenario: Refazer elimina o arquivo na hora, e a eliminação é a rede

- **WHEN** o professor refaz uma resposta, e o arquivo dela por algum motivo não foi eliminado na hora
- **THEN** a próxima eliminação o elimina, porque nenhum caderno guardado o referencia

#### Scenario: A eliminação roda antes da câmera

- **WHEN** o escaneamento é aberto com respostas vencidas no aparelho
- **THEN** as respostas vencidas são eliminadas antes de o primeiro quadro ser analisado

#### Scenario: Sair da sessão não elimina a resposta

- **WHEN** o usuário sai da sessão do aparelho com respostas guardadas de menos de 30 dias e referenciadas por caderno
- **THEN** as respostas continuam no aparelho

#### Scenario: A revogação do vínculo não elimina a resposta

- **WHEN** o vínculo do aparelho é revogado com respostas guardadas de menos de 30 dias e referenciadas por caderno
- **THEN** as respostas continuam no aparelho, como o caderno que as referencia

#### Scenario: Enviar a parcial não elimina a resposta

- **WHEN** o caderno completa e a parcial é entregue e confirmada pelo servidor
- **THEN** as respostas do caderno continuam no aparelho

#### Scenario: A confirmação da nota elimina as respostas

- **WHEN** o servidor confirma a nota de um caderno corrigido
- **THEN** as respostas do caderno são eliminadas na hora, e o caderno continua corrigido, sem voltar a incompleto

#### Scenario: Nota gravada e não confirmada mantém a resposta

- **WHEN** a nota foi gravada como pendente e o servidor ainda não a confirmou, por falta de rede ou recusa
- **THEN** as respostas do caderno continuam no aparelho

#### Scenario: Eliminação que falha não impede o escaneamento

- **WHEN** o escaneamento é aberto e um dos arquivos vencidos não pode ser eliminado
- **THEN** os outros arquivos vencidos são eliminados, o escaneamento abre, o caderno é retomado, e o arquivo que falhou continua no aparelho até a próxima eliminação

#### Scenario: Arquivo temporário de uma gravação interrompida

- **WHEN** o aplicativo terminou no meio da gravação de uma resposta, deixando um arquivo temporário, e é reaberto
- **THEN** o temporário é eliminado na abertura, porque nenhum caderno o referencia

#### Scenario: Outra organização no mesmo aparelho

- **WHEN** a eliminação roda numa sessão de uma organização e há respostas vencidas de outra organização no aparelho
- **THEN** as vencidas das duas são eliminadas, e as não vencidas e referenciadas das duas são mantidas

#### Scenario: Aparelho que nunca mais abre o aplicativo

- **WHEN** uma resposta foi guardada há 31 dias e o aplicativo não foi aberto desde então
- **THEN** a eliminação agendada em segundo plano a elimina, sem o aplicativo abrir

#### Scenario: O segundo plano não elimina o que o escaneamento está gravando

- **WHEN** a eliminação em segundo plano roda com o escaneamento aberto, enquanto uma resposta acaba de ser gravada e ainda não foi referenciada pelo caderno
- **THEN** o arquivo é mantido para a próxima eliminação, a gravação conclui e o caderno o referencia normalmente
