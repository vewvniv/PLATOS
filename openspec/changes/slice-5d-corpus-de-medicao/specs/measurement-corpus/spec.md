# Spec Delta

## Purpose

Define o corpus de medição: as respostas discursivas de aluno que o mantenedor coleta, com a nota que o professor deu, para escolher o motor de OCR e calibrar o corte de fiabilidade da transcrição (§9 da arquitetura) sobre letra real. A capability diz o que uma amostra contém e nunca contém, que a coleta existe só no aplicativo de depuração, como a amostra sai do aparelho e quanto tempo espera.

## ADDED Requirements

### Requirement: Uma amostra do corpus é a foto da resposta, a nota do professor e uma referência, e nada que identifique o aluno

Uma amostra SHALL conter: a **foto** da resposta, com os **mesmos bytes** que o aparelho guardou (sem reprocessar, para que a medição veja o que o aplicativo viu); a pontuação que o professor deu a essa resposta e o máximo da questão, ambos decimais exatos nas mesmas guardas de `scoring` ("A nota do professor completa a parcial sem recalcular a parte objetiva"); a referência ao item, que é o hash do pacote e o item (o enunciado e a rubrica SHALL NOT ser copiados: o sistema já os tem em texto exato); um identificador de amostra **aleatório**, sorteado na cópia e sem relação com qualquer outro identificador do sistema; a **versão do formato**; e os campos `referencia` (o texto correto da resposta) e `descartar`, que SHALL nascer vazios e SHALL ser preenchidos fora do aparelho.

Uma amostra SHALL NOT conter nome, turma, matrícula, o token que identifica o aluno, o identificador da captura ou do caderno, o nome ou o caminho do arquivo da resposta no aparelho, a data ou a hora da captura, nem metadado do aparelho ou do professor. O nome do arquivo da foto SHALL ser o identificador aleatório, e SHALL NOT derivar do nome do arquivo de origem. Dois identificadores de amostra SHALL NOT permitir dizer que duas respostas são do mesmo aluno, da mesma folha ou da mesma sessão.

#### Scenario: A amostra leva o que a medição precisa

- **WHEN** a coleta copia uma resposta de 2 pontos a que o professor deu 1.5
- **THEN** a amostra tem a foto com os mesmos bytes, a pontuação 1.5, o máximo 2, o hash do pacote e o item, um identificador aleatório, a versão do formato, e `referencia` e `descartar` vazios

#### Scenario: A amostra não leva identificação

- **WHEN** uma amostra é copiada de uma resposta de um aluno com token, turma e matrícula
- **THEN** nenhum desses valores, nem o identificador da captura ou do caderno, nem o nome ou o caminho do arquivo de origem, nem a data, aparece no conteúdo da amostra nem no nome do arquivo da foto

#### Scenario: Duas respostas do mesmo aluno não se ligam

- **WHEN** a coleta copia as duas discursivas do mesmo caderno
- **THEN** as duas amostras têm identificadores independentes, e nada nelas diz que vieram da mesma folha

### Requirement: A coleta existe só no aplicativo de depuração e vem desligada

O código que copia a resposta para o corpus SHALL existir **somente** no aplicativo de depuração. O aplicativo de release SHALL NOT conter esse código, e SHALL NOT oferecer interruptor, pasta ou qualquer outro caminho que copie resposta: a ausência é de **compilação**, e não de configuração.

No aplicativo de depuração, a coleta SHALL vir **desligada**. Desligada, nenhuma amostra SHALL ser criada, e a correção SHALL se comportar como se a coleta não existisse. Ligada, ela vale para toda nota confirmada naquele aparelho, até ser desligada.

#### Scenario: O release não contém a coleta

- **WHEN** o aplicativo de release é construído
- **THEN** a verificação do APK de release não encontra o código da coleta, e a mesma verificação, sobre o APK de depuração, o encontra

#### Scenario: Desligada por padrão

- **WHEN** o aplicativo de depuração é instalado e uma nota é confirmada sem que o interruptor tenha sido ligado
- **THEN** nenhuma amostra é criada

#### Scenario: Ligada copia

- **WHEN** o interruptor está ligado e o professor confirma a nota de uma folha com duas discursivas
- **THEN** duas amostras são criadas

### Requirement: A amostra sai do aparelho por cabo, e nada passa pelo servidor

A coleta SHALL NOT enviar nada ao servidor, SHALL NOT criar resultado, SHALL NOT entrar na fila de envio (`result-sync`) e SHALL funcionar sem rede. O aplicativo SHALL NOT oferecer tela de exportação, compartilhamento nem qualquer rota para a amostra: ela SHALL sair do aparelho somente por cabo, com as ferramentas de depuração, a partir de uma pasta privada do aplicativo.

A conferência de que a saída foi completa SHALL ser feita fora do aparelho: toda foto SHALL ter o seu arquivo de dados e todo arquivo de dados a sua foto, e a contagem SHALL bater com a do aparelho. Foto sem arquivo de dados, ou o inverso, SHALL ser reprovada pela conferência.

Nenhuma amostra SHALL ficar no repositório do projeto.

#### Scenario: Sem rede

- **WHEN** o aparelho está sem rede e a coleta copia uma resposta
- **THEN** a amostra é criada, e nenhuma requisição ao servidor é feita

#### Scenario: Nada entra no outbox

- **WHEN** a coleta copia as discursivas de uma nota confirmada
- **THEN** o outbox contém apenas a nota, como sem a coleta

#### Scenario: Saída incompleta é reprovada

- **WHEN** uma foto é puxada sem o seu arquivo de dados
- **THEN** a conferência reprova e nomeia a amostra

### Requirement: As amostras esperam no aparelho com prazo, e somem com a sessão

As amostras SHALL ficar numa pasta própria do aparelho, **separada** das respostas guardadas por `scan-session`, fora do backup do sistema e da transferência entre aparelhos, como a resposta guardada. A eliminação da resposta guardada (confirmação da nota, prazo, falta de referência) SHALL NOT eliminar a amostra, e eliminar a amostra SHALL NOT eliminar a resposta guardada.

O aplicativo SHALL eliminar toda amostra criada há **30 dias ou mais**, em qualquer hipótese, na abertura do aplicativo e por agendamento periódico em segundo plano que não dependa de o aplicativo abrir. **Sair da sessão do aparelho e a revogação do vínculo** SHALL eliminar todas as amostras ainda no aparelho: a amostra é uma **cópia**, e não o único exemplar de um trabalho já feito, ao contrário do resultado pendente. A eliminação SHALL NOT depender de o aluno, a prova ou a organização estarem na sessão corrente. Eliminar um arquivo que falha SHALL NOT impedir as outras eliminações nem a abertura do aplicativo. Foto sem arquivo de dados, ou o inverso, SHALL ser tratada como resíduo e eliminada.

#### Scenario: Amostra vence em 30 dias

- **WHEN** uma amostra criada há 30 dias nunca foi puxada
- **THEN** ela é eliminada, com ou sem o aplicativo ter aberto

#### Scenario: Sair elimina a cópia

- **WHEN** o professor sai da sessão do aparelho com amostras no aparelho
- **THEN** as amostras são eliminadas, e a resposta guardada e o resultado pendente seguem as regras de `scan-session` e `result-sync`

#### Scenario: Revogação elimina a cópia

- **WHEN** o vínculo do professor é revogado e o aparelho observa
- **THEN** todas as amostras ainda no aparelho são eliminadas

#### Scenario: A eliminação da resposta não leva a amostra

- **WHEN** o servidor confirma a nota e a resposta guardada é eliminada
- **THEN** a amostra dessa resposta continua no aparelho até ser eliminada pelo prazo, por sair da sessão ou pela revogação

#### Scenario: Resíduo de cópia interrompida

- **WHEN** existe uma foto sem arquivo de dados na pasta das amostras
- **THEN** ela é eliminada na próxima eliminação

#### Scenario: Falha de uma eliminação

- **WHEN** a eliminação de uma amostra falha
- **THEN** as demais são eliminadas, o aplicativo abre normalmente, e a que falhou fica para a próxima eliminação
