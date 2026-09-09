# Projeto — Plataforma de Avaliação Educacional com IA

## Fonte de verdade

- `docs/architecture/ARQUITETURA-FINAL-v3.md` é a referência arquitetural única.
- Não contradiga decisões da arquitetura. Uma mudança arquitetural exige ADR.
- `openspec/specs/` descreve o comportamento atual.
- `openspec/changes/` descreve mudanças em andamento.
- `docs/adr/` registra decisões arquiteturais permanentes.
- `rigorous.md` define as proibições de conduta (P1–P26) e o método de verificação (§3). Leia antes
  de implementar e antes de fechar qualquer tarefa. Instrução que só pode ser cumprida quebrando
  este arquivo ou aquele é má instrução, e `rigorous.md` §7 diz o que fazer com ela.
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

As regras 9 e 10 dizem *o que* verificar. **Como saber se a verificação vale** está em `rigorous.md`
§3, junto com o método de "ver falhar" e o sombreamento de fixture; é leitura obrigatória antes de
fechar qualquer tarefa que produza número, geometria, paridade ou artefato hasheado.

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

### Editar `openspec/specs/` direto, sem passar por uma mudança

É atalho, e vale **só com as cinco condições juntas**. Faltando qualquer uma, o veículo é mudança
nova — inclusive para correção "pequena" logo depois de um archive, que é quando o atalho é mais
tentador e menos visível.

1. **Nenhum comportamento muda.** Só a forma como uma regra já vigente é dita.
2. **A decisão já existe e é citável**, com o arquivo e a linha na mão antes da edição — não a
   lembrança de que ela existe.
3. **Nenhum texto novo é inventado.** Cada frase vem de um bloco que já estava escrito; o que muda é
   onde ela mora.
4. **Commit isolado**, que diz de onde veio cada metade e por que o veículo não foi uma mudança.
5. **Nenhuma mudança ativa declara delta sobre o mesmo requisito.** Um `MODIFIED` carrega o bloco
   **inteiro** do requisito: editar a spec principal por baixo dele faz a mudança ativa passar a
   descrever um estado que já não existe, e o archive dela reintroduz o texto antigo em silêncio.

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
