## Why

Na conferência em aparelho real da `slice-4b-outbox-de-resultado`, o push deu **HTTP 500** porque a
migration não estava aplicada em produção. O aparelho tratou esse 500 como recusa definitiva: o
pendente ficou parado até alguém abrir sessão ou escanear outra folha. Está registrado em
`docs/cobertura-slice-4b-outbox-de-resultado.md` §5.1.2, com **dono: esta base** e **fatia-limite: a
primeira que tiver retentativa com política**. Esta é essa fatia.

O segundo item da mesma seção — "quem reabriu a sessão foi o mesmo usuário" — é dívida de
**verificação**, não de comportamento: a spec já exige que o pendente suba na sessão seguinte de
qualquer membro, e `SessaoActivity.escoarPendentes` já implementa isso. O que nunca foi medido é
**outra credencial** drenando a fila. Entra aqui como tarefa de teste, sem delta de spec.

## What Changes

- `EnvioDeResultados` passa a separar **recusa transitória** (o servidor falhou: 5xx) de **recusa
  definitiva** (o servidor decidiu: 4xx). Hoje as duas caem em `recusados`.
- `EnvioDeResultadosWorker` passa a devolver `Result.retry()` também quando houve recusa transitória,
  e não só por ausência de rede. Recusa definitiva continua devolvendo `success`: repetir contra um
  servidor que já disse não é laço quente.
- O `output` do `WorkSpec` ganha a contagem de transitórios, para que "rodou e não drenou" continue
  distinguível de fora — foi essa indistinguibilidade que travou a conferência.
- Teste instrumentado novo: pendente gravado sob a credencial de um usuário é enviado com a
  credencial de **outro**, depois da troca de sessão no mesmo aparelho. Reduz o item do segundo
  membro de inferência a medição, no que o aparelho decide.

## Capabilities

### New Capabilities

Nenhuma.

### Modified Capabilities

- `result-sync`: o requisito "O resultado nasce pendente e espera a rede numa fila local" passa a
  distinguir recusa transitória de definitiva e a exigir nova tentativa automática só para a
  primeira.

## Impact

- `apps/android/src/main/kotlin/com/platos/android/outbox/EnvioDeResultados.kt` — classificação e
  `ResumoDoEnvio`.
- `apps/android/src/main/kotlin/com/platos/android/outbox/EnvioDeResultadosWorker.kt` — decisão de
  `retry` e o diagnóstico.
- `apps/android/src/test/kotlin/com/platos/android/outbox/EnvioDeResultadosTest.kt` — cenários de
  classificação.
- `apps/android/src/androidTest/.../` — teste instrumentado da troca de credencial.

### O que NÃO será alterado

- **`Retorno`** (`net/Retorno.kt`). Ele já carrega o status cru, e a decisão 8 da 4a-zero fixou um
  vocabulário único de falha para o aplicativo inteiro. Classificar dentro dele faria a entrada e a
  consulta herdarem uma política que é do outbox.
- **O servidor.** Nenhuma rota, migration ou tabela muda. O 500 que motivou o item era implantação,
  não contrato.
- **A regra do expurgo.** O pendente continua saindo **somente** após confirmação. Nada aqui toca a
  classe H nem os três caminhos de apagamento.
- **`ExistingWorkPolicy.KEEP`** e o agendamento por organização.
- **Teto de tentativas.** Não entra número: o backoff exponencial do `WorkManager` já limita a
  frequência, e limite sem evidência de pressão é número sem consumidor (P18).
