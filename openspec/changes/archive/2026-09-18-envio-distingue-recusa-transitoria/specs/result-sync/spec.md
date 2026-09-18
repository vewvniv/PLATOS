## MODIFIED Requirements

### Requirement: O resultado nasce pendente e espera a rede numa fila local

Todo resultado gravado SHALL nascer **pendente de sincronização** e entrar numa fila local.

Havendo rede, o aparelho SHALL enviar os pendentes. Não havendo, eles SHALL permanecer na fila, e a
ausência de rede SHALL NOT descartar, alterar nem marcar como enviado qualquer resultado.

O envio de um resultado SHALL NOT depender do sucesso do envio de outro: um resultado recusado pelo
servidor SHALL NOT bloquear a fila.

O aparelho SHALL distinguir a recusa que o servidor atribui **a si mesmo** — falha do servidor, da
qual a classe de status 5xx é o sinal — da recusa em que o servidor **decide sobre o pedido**, cuja
classe é 4xx. A primeira é transitória: o mesmo envio, repetido depois, pode ser aceito sem que nada
no aparelho mude. A segunda não é: repeti-la produz a mesma resposta.

Havendo pendente recusado de forma transitória, o aparelho SHALL agendar nova tentativa por conta
própria, sem depender de escaneamento novo nem de abertura de sessão. Havendo apenas recusa
definitiva, o aparelho SHALL NOT agendar nova tentativa imediata — o pendente continua na fila e
espera o próximo caminho que já existe.

A nova tentativa SHALL ser espaçada, e SHALL NOT repetir o envio em laço contínuo contra o servidor.

Nenhuma classificação de recusa SHALL apagar, alterar ou marcar como enviado um pendente: a distinção
decide **quando se tenta de novo**, e nunca o que acontece com a fila.

O resultado de uma passada pela fila SHALL ser legível de fora do aplicativo, distinguindo quantos
foram confirmados, quantos ficaram por ausência de rede, quantos por recusa transitória e quantos por
recusa definitiva. "Rodou e não drenou" SHALL NOT ser o único sinal disponível.

#### Scenario: Sem rede, a fila espera

- **WHEN** resultados são apurados com o aparelho offline
- **THEN** todos ficam pendentes na fila, e nenhum é perdido ou marcado como enviado

#### Scenario: A rede volta

- **WHEN** o aparelho recupera a rede com resultados pendentes na fila
- **THEN** os pendentes são enviados

#### Scenario: Um resultado recusado não trava os outros

- **WHEN** o servidor recusa um resultado da fila
- **THEN** os demais pendentes continuam sendo enviados

#### Scenario: Falha do servidor é tentada de novo

- **WHEN** o envio de um pendente é recusado por falha do servidor
- **THEN** o pendente continua na fila e o aparelho agenda nova tentativa por conta própria

#### Scenario: Recusa definitiva não vira laço

- **WHEN** o envio de um pendente é recusado por decisão do servidor sobre o pedido
- **THEN** o pendente continua na fila e o aparelho não agenda nova tentativa imediata

#### Scenario: A passada diz por que não drenou

- **WHEN** uma passada pela fila termina sem confirmar nenhum pendente
- **THEN** o desfecho registrado distingue ausência de rede, recusa transitória e recusa definitiva
