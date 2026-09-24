## Why

A auditoria de 2026-09-18 achou o padrão por trás de cinco achados (`docs/auditoria-2026-09-18-antes-da-fatia-5.md`
§7): **o que vence depois do archive não tem quem o cobre.** Os itens que entraram na tabela de ponto
de não-retorno do §16 avançaram e fecharam (roster cacheado, classe H, retenção da classe B, a 6.4b,
a 9.2). Os que ficaram só em prosa de `docs/cobertura-*.md` não avançaram (modo degradado, variante
release, migration no deploy, reexame do limiar). A diferença entre os dois grupos não é
importância: é **estar na tabela**. E há um segundo eixo, que a mesma auditoria isolou: o rigor desta
base é **por módulo**, e o que atravessa dois módulos (a versão do renderizador em três registros, o
contrato do fio em quatro espelhos, a instância de Room alcançada por três caminhos) não tem camada
posicionada para vê-lo.

É a ETAPA **8** do `docs/plano-de-correcao-antes-da-fatia-5.md`, a última da banda de correção.
**Por que agora:** instalar o regime **depois** da fatia 5 faria a 5 criar dívida sob o regime
antigo. A regra existe para a próxima fatia, e a próxima é ela (plano, §2, "O que precisa fechar
antes de a fatia 5 abrir").

## What Changes

- **`rigorous.md` ganha duas regras, e não três**, cada uma com os incidentes que a pagaram (§10:
  commit, arquivo e linha):
  - **P27 [V]**: o registro de dívida é **um só**, a tabela de ponto de não-retorno do §16, e o
    archive de uma mudança reconcilia as linhas que ela alcançou, marcando cada uma como paga ou
    reagendada com motivo. O texto é o do plano, transcrito. Os quatro incidentes do plano, e um quinto
    que o plano não podia conhecer: a linha do APK de release que o próprio plano mandava pôr no §16,
    e que nunca entrou (nota de 2026-09-23 no §2 do plano).
  - **P28**: valor, contrato ou recurso em dois módulos tem dono único compilado ou conferência
    cruzada. Os incidentes são a versão do renderizador (4.4), o contrato do fio (2.1) e a instância
    de Room (3.2). O precedente que mostra que a regra é barata é `tools/parity/limiar.mjs`.
  - A lista normativa da zona vermelha (§4) passa de quinze para dezesseis regras, e o §8 ganha a
    linha de fechamento que a P27 exige. Essa linha hoje só existe no quadro do §10 do plano, e o
    quadro sai de circulação com a banda.
- **A coluna "Fatia-limite" do §16 passa a ser legível por máquina.** Cada célula começa com um
  token entre crases: uma fatia (`5`, `2b`), `antes-de:<evento>` ou `continuo`. Quando o item foi
  pago, vem em seguida o marcador `paga`. A prosa que já estava na célula fica intacta, e nenhum
  limite muda nessa passada. `paga` só entra onde a própria linha já diz que fechou. Acima da tabela
  entra uma linha com os eventos que já ocorreram, que hoje são **nenhum**.
- **Duas linhas novas no §16**, vindas do §9 do plano, **por decisão do mantenedor nesta sessão**:
  `assessment_fact` (limite `9`) e `minifyEnabled`/assinatura/`versionCode` (limite
  `antes-de:lancamento`). O archive desta mudança apaga do `CLAUDE.md` o ponteiro para o plano. Sem as
  duas linhas, esses itens ficariam só na prosa de um documento que ninguém mais abre.
- **`tools/divida/divida.mjs`**, novo, no molde de `limiar.mjs`. Ele lê a tabela **do próprio §16**
  (não de uma cópia) e deriva a **fatia corrente** da maior mudança `slice-<N>` em `openspec/changes/`,
  ativa ou arquivada; hoje dá `4b`. Também por decisão do mantenedor nesta sessão, a fatia corrente
  **não é digitada**: um segundo registro de "em que fatia estamos" seria o espelho cego que a P28
  proíbe. A guarda reprova a linha **vencida sem reconciliação** e nomeia cada uma. Quando não consegue
  ler (tabela, coluna, token, piso), sai com `2`.
- **A guarda nasce vermelha sobre a árvore real**, e o conjunto está previsto por leitura: **exatamente**
  `LGPD com dados de menores` (limite `3`) e `Uso offline não fecha ponta a ponta` (limite `4a`). As duas
  vencidas são reconciliadas em commit próprio. A linha do uso offline foi paga, com evidência já
  registrada. A da LGPD é decisão do mantenedor, e a proposta leva uma sugestão.
- **Dois passos no `ci.yml`**, job `web`, no molde: um confere, o outro planta defeitos fora do
  checkout e falha se a conferência aceitar.
- **Duas linhas no `CLAUDE.md`**, no workflow OpenSpec, entre o passo 5 e o 6. No propose, a proposta
  nomeia as linhas do §16 que a mudança vai alcançar. No archive, cada uma é reconciliada.
- **No archive:** a linha temporária do `CLAUDE.md` sai. Ela mesma manda sair no archive da última
  mudança da banda, e `transferencia-entre-aparelhos` já foi arquivada.
- **Nenhum comportamento do produto muda.** Nenhum código Kotlin ou TypeScript é tocado, e nenhum
  fixture, golden, hash, spec ou migration.

### Linhas do §16 que esta mudança alcança (a P27 aplicada à própria proposta)

A fatia corrente continua `4b` durante toda a mudança, então nenhuma linha **vence** por causa dela.
Duas já estão vencidas, e é a guarda que vai nomeá-las: `LGPD com dados de menores` e `Uso offline não
fecha ponta a ponta`. Esta mudança reconcilia as duas. Três linhas **vencem na fatia 5**, a próxima:
`Acurácia em manuscrito`, `Modo degradado (§10) não existe` e `O limiar do OMR foi apurado sobre um
aparelho e uma impressora`. Elas não são tocadas aqui. A guarda passa a listá-las como "vence nesta
fatia" no primeiro `/opsx:propose` da 5.

## Capabilities

### New Capabilities

Nenhuma.

### Modified Capabilities

Nenhuma. O que muda é a regra de conduta (`rigorous.md`), o formato de uma tabela da arquitetura, uma
guarda de CI e o workflow do `CLAUDE.md`. Nenhum requisito observável do produto muda. Como
`generatejooq-sem-registro-automatico` e as três mudanças da ETAPA 7, esta declara `skip_specs: true`.
A tabela do §2 do plano diz o mesmo: **Specs tocadas: nenhuma**.

## Impact

- **`rigorous.md`**: P27 e P28 numa seção nova (E), a lista do §4 e uma linha no §8.
- **`docs/architecture/ARQUITETURA-FINAL-v3.md` §16**: os tokens da coluna "Fatia-limite", a linha de
  eventos, um parágrafo curto que diz o formato, duas linhas novas e a reconciliação das duas vencidas.
  É atualização de registro com informação nova, e não substituição de decisão, então **não abre
  ADR**. O precedente é a atualização de 2026-09-10 no próprio §16.
- **`tools/divida/divida.mjs`**: novo. Usa só `node:fs`, `node:path` e `node:url`, sem dependência
  de npm.
- **`.github/workflows/ci.yml`**: dois passos no job `web`, depois dos da versão do renderizador.
- **`CLAUDE.md`**: duas linhas no workflow, e a linha temporária sai no archive.
- **`docs/cobertura-registro-de-divida-executavel.md`**: novo. Traz o primeiro vermelho, as mutações
  com os conjuntos previstos e os reais, e o que fica sem verificação.
- **Ambiente:** nenhum Docker, nenhum emulador. Nenhuma entrada do Gradle ou do Vitest muda, e o
  comando cheio é o CI da PR, lido no destino (P26).

### O que NÃO será alterado

- **Nenhuma regra é afrouxada nem removida** (`rigorous.md` §10). O plano só acrescenta uma
  reconciliação por archive.
- **Não entra uma terceira regra.** O artefato de release é **um** incidente, já corrigido na 7.2.
- **O §16 não é duplicado num YAML**, e a fatia corrente não é declarada à mão: seriam dois registros.
- **A guarda não lê `docs/cobertura-*.md`.** Prosa não é o registro, e é essa a regra.
- **Nenhum limite muda na passada de formato.** Reagendar é ato de reconciliação, com motivo escrito,
  e acontece só nas duas linhas vencidas.
- **O passivo em prosa anterior à P27** (as tabelas de débito dos `docs/cobertura-*.md`) **não é
  migrado.** A P27 vale daqui para frente. Os quatro itens que já custaram entraram no §16 na ETAPA 1,
  e os dois do plano entram aqui. O resto fica declarado como lacuna.
- **O spec de `device-session` (linha 395, antes 352)**, que afirma a recusa sem rede contra o §10.
  Corrigi-lo é mudança de spec, e pertence à fatia 5, que é a fatia-limite do modo degradado.
- **`limiar.mjs`, `answer-kind.mjs`, `fio.mjs` e `renderizador.mjs`** ficam como estão.
