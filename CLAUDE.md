# Projeto — Plataforma de Avaliação Educacional com IA

## Fonte de verdade

- `docs/architecture/ARQUITETURA-FINAL-v3.md` é a referência arquitetural única.
- Não contradiga decisões da arquitetura. Uma mudança arquitetural exige ADR.
- `openspec/specs/` descreve o comportamento atual.
- `openspec/changes/` descreve mudanças em andamento.
- `docs/adr/` registra decisões arquiteturais permanentes.
- Nunca recrie contexto já registrado nesses arquivos; leia a fonte relevante.

## Regras de execução

1. **Contrato antes de implementação.** Mude contratos/tipos/schema antes dos consumidores.
2. **Fatias verticais.** Uma mudança deve atravessar o menor caminho completo possível; evite tarefas horizontais de infraestrutura sem necessidade.
3. **Uma mudança OpenSpec = um escopo pequeno de implementação.** Se tocar mais de duas capabilities/specs, reavalie e divida.
4. **Não introduza tecnologia nova** sem justificar no `design.md` e verificar se a arquitetura já cobre o problema.
5. **Não substitua uma decisão registrada por preferência pessoal.**
6. **Não faça refatoração oportunista** fora do escopo da mudança.
7. **Não duplique regra de negócio** entre apps; código compartilhado pertence ao domínio KMP quando aplicável.
8. **Não crie abstrações prematuras.** Só abstraia quando houver necessidade comprovada.
9. **Teste antes de declarar concluído.** Execute testes diretamente relacionados à mudança e, quando possível, a suíte afetada.
10. **Nunca marque tarefa como concluída sem verificação real.**

## Verificação

A regra 10 diz *o que* verificar; esta diz como saber se a verificação vale. Aplica-se a número, não só a teste: as piores evidências falsas desta base foram medições, não suítes vermelhas.

**Antes de confiar numa medição, prove que ela reage a uma mudança no que ela mede.**

Crítico é o que falha em silêncio e chega à folha impressa ou ao OMR — medição de texto, geometria, paridade, fidelidade e todo artefato imutável hasheado. Para esses:

- Introduza um erro de propósito, confirme que a verificação fica vermelha, e reverta.
- Cubra `NaN`, infinito, vazio e fora de faixa. `NaN > tolerância` é falso e passa calado.
- Confira valor numérico contra oracle independente, que não compartilhe código com o que ele julga.
- Desconfie de janela de medição que alcance o vizinho, e de contagem feita sobre cache.
- Rode o **comando completo do CI**, e não a versão filtrada, antes de publicar mudança que toque
  build, manifesto ou suíte instrumentada. `./gradlew build` não roda `connectedDebugAndroidTest`,
  e `--tests` de uma classe não roda as outras. Duas vezes o comando estreito local escondeu o que
  o cheio pega.
- **Artefato de execução anterior não é evidência da execução atual.** Reinstalação de APK apaga o
  `filesDir`, e o nome do arquivo não muda — nada avisa. A primeira tentativa de fechar paridade na
  fatia 4a comparou o web de **hoje** contra um `android.pdf` de **agosto**: o arquivo estava lá,
  com o nome certo, e só a data denunciava. Estado que mora no instrumento precisa de âncora —
  data, hash ou diretório por execução —, conferida **antes** da comparação. É defeito
  diferente do comando de CI filtrado: lá falta cobertura; aqui a cobertura roda e mede o
  artefato errado.
- **Sinal barato não prova o que ele não mede.** `comando; echo "ok"` imprime o `ok` mesmo com o
  comando vermelho — só `&&` ou uma conferência de `$?` amarram os dois. A família é maior que o
  `echo`: workflow verde prova que a imagem foi construída, não que ela está servindo; um 401 sem
  token prova roteamento e autenticação, não que o processo alcança o banco; um arquivo com o nome
  certo prova que existe um arquivo. Antes de citar um sinal como evidência, diga **qual passo ele
  atravessa** — e se o passo que interessa não estiver nesse caminho, o sinal não serve, por mais
  verde que esteja. Quatro vezes na fatia 4a.
- Registre em `docs/cobertura-*.md` como o teste foi visto falhar, não só que ele passa.

**Fixture mínima sombreia a camada que deveria testar.** Quando duas conferências cobrem o mesmo
dado por motivos diferentes, mutar a de dentro deixa a de fora recusando pelo motivo errado, e o
teste fica verde por acidente — ou vermelho sem provar nada. Aconteceu duas vezes na fatia 4a: o
cenário de conteúdo truncado no cache continuou **verde** com a leitura confiando no nome do arquivo,
porque truncado também não parseia e a camada (b) o recusava por interpretação; e a conferência de
identidade da folha só pôde ser exercitada porque a `prova-2` foi construída com os **mesmos itens,
posições e gabarito** da `prova-referencia` — com itens diferentes, `ObjectiveScoring` recusaria por
divergência de conjunto e a identidade nunca seria consultada. Ao escrever um "ver falhar" para uma
camada específica:

- A fixture da mutação SHALL **isolar essa camada**: passar em todas as outras conferências e falhar
  só na que está sob teste. Se ela falha em duas, a mutação não diz qual das duas segurou.
- A asserção SHALL conferir o **motivo** da recusa, e não só que houve recusa. "Recusou" é
  indistinguível entre a camada certa e a vizinha.
- Leia **quais** cenários caíram e quais não: conjuntos disjuntos entre duas mutações são a prova de
  que as camadas são independentes; um cenário que sobrevive à mutação da própria camada que ele
  nomeia está medindo outra coisa.

## Invariantes arquiteturais

- I1: toda questão nasce marcada por habilidade BNCC.
- I2: resultados são fatos append-only atribuídos a habilidade.
- I3: todo artefato gerado por IA carrega `prompt_version`, `model_id` e `params_hash`.
- I4: todo item nasce com proveniência e licença; `visibility: public` é condicionado à licença.
- I5: artefato imutável nunca contém dado pessoal direto.
- Toda tabela de domínio é autorizada por `organization_id`, nunca por `user_id`.
- `membership` é N:N entre usuário e organização.
- Assinatura pertence à organização.
- `ExamPackage` publicado é imutável e possui hash.
- OMR é offline; sincronização é pull de referência imutável + push append-only.
- O LayoutMap é a fonte geométrica do OMR; PDF não é a fonte de geometria.
- KMP é a fonte compartilhada de medição, layout, scoring e contratos de domínio.
- Captura segue: ArUco → identificação de região → homografia → QR na ROI retificada → OMR/OCR/recorte.
- Correção objetiva local é definitiva quando não há discursivas.
- Revisão humana vence qualquer resultado automático.
- `TranscriptionProvider` permanece plugável; caching de prefixo vem antes de OCR.
- TexTeller não entra em v1 sem evidência do corpus real.
- Não introduzir Redis, broker, vector DB, Elasticsearch, GraphQL ou microserviços sem ADR explícito.

## IA e custo

- Estruture prompts com o prefixo invariável primeiro.
- Não envie ao modelo dados que o sistema já possui em texto exato.
- Não use OCR só para “economizar tokens” sem medir se a nota muda.
- Registre chamadas LLM com tokens, custo, latência, prompt/model/params.
- Toda saída LLM deve passar por schema validation.
- Prompts são versionados no Git; mudanças de prompt exigem nova versão.

## Workflow OpenSpec

1. Se houver incerteza: `/opsx:explore`.
2. Caso contrário: `/opsx:propose <nome>`.
3. Leia e revise `proposal.md`, `specs/`, `design.md`, `tasks.md`.
4. Só então: `/opsx:apply`.
5. Verifique testes e implementação.
6. `/opsx:archive`.

Use `openspec/` como memória durável. Não reexplique a arquitetura no chat quando um arquivo existente puder ser citado.

## Commits

- Um commit deve representar uma unidade lógica.
- Contratos/DB/API devem ser separados dos consumidores quando isso reduzir risco.
- Não misture formatação, renomeações ou limpeza com implementação funcional.
- Não faça commit de segredos, `.env` ou credenciais.

## Preferências de implementação

- Kotlin: Ktor 3, jOOQ, kotlinx.serialization.
- Banco: PostgreSQL/Supabase, RLS, pgvector, migrations via Supabase CLI.
- Android: CameraX, OpenCV, ZXing-C++, Room, WorkManager, Compose, supabase-kt.
- Web: React, TypeScript, Vite, TanStack Query.
- Compartilhado: KMP.
- CI: GitHub Actions; observabilidade: Sentry + logs estruturados.

## Antes de alterar algo

1. Leia o change ativo.
2. Leia a spec relacionada.
3. Leia o ADR relacionado, se existir.
4. Leia somente os arquivos de código diretamente necessários.
5. Implemente a menor mudança compatível.
