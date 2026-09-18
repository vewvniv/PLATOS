## Context

Ver `proposal.md` — Why. O que o desenho precisa levar em conta, e que foi conferido no repositório
antes de decidir:

- `ObjectiveScoring.score` acumula `points` num laço sobre `answers` e só guarda as pendências. O
  acerto e o erro de cada item existem dentro do laço e morrem nele.
- `ScanState.Scored` carrega `InterpretedReading` inteira ao lado da nota, e `InterpretedReading.answers`
  traz `QuestionAnswer` por questão. **O insumo por item já está na mão no ponto da correção.**
- `ClienteApi` só faz `GET`. O `Routes.kt` da API não tem uma rota de escrita.
- Não existem `Room`, `WorkManager` nem processador de anotações no catálogo de dependências.
- Não existe tabela `student`, e `student_id` não aparece em nenhum arquivo do repositório — a busca em
  `.kt`, `.sql`, `.ts` e `.tsx` devolve duas linhas, ambas **comentários** que citam `(exam_id,
  student_id)` da §10 como coisa futura.
- `PackageAssignment` tem três campos: `studentToken`, `variantId`, `qr`. A KDoc diz por quê, e a spec
  de `exam-package` repete como norma: "O pacote SHALL levar de cada atribuição apenas o token."

## Goals / Non-Goals

**Goals:**

- Fechar o fluxo da `proposal.md` com o menor número de peças, cada uma participando dele.
- Não descartar o resultado por item, que é irrecuperável depois que a folha de papel sai de circulação.
- Deixar o fato gravado hoje resolvível pela identidade de aluno do dia em que ela existir, **sem
  reescrever fato nenhum**.

**Non-Goals:**

- Histórico de resultados no aparelho. O que é confirmado sai do aparelho; a nota mora no servidor.
- Lote, avanço automático e completude de turma.
- `capture_session` e `capture_region`. Elas vêm com lote e com o modo degradado, que guardam imagem.

## Decisions

### 1. O fato nasce chaveado por `(exam_id, student_token)`, e é assim que ele alcança `student_id` depois

§10 fixa a idempotência em `(exam_id, student_id)`. `student_id` **não existe** — não há tabela
`student`, e o pacote leva só o token, porque I5 proíbe dado pessoal direto no artefato imutável.
O que o aparelho tem no momento da captura é o `student_token` do QR.

Dentro de uma prova os dois são **a mesma chave**: `exam_roster` impõe
`unique (exam_id, student_token)`, então um token é um aluno. E a idempotência da §10 é intra-prova por
definição — "recaptura cria nova revisão; a mais recente é a corrente". Nada do requisito se perde.

**Por que não criar `student_id` agora.** Criá-lo é implementar ADR-0003 inteiro — um `student` por
organização mais `student_alias` append-only — e a `exam_assignment` da fatia 7. Duas fatias de
trabalho para produzir um identificador que esta fatia não consegue exercitar, contra a regra 3 e
contra a regra de corte desta mudança.

**Por que isto não vira dívida silenciosa.** ADR-0003 decide que "os fatos continuam apontando para o
`student_id` que existia quando foram gravados" e que a unificação acontece em **read model**, nunca
por reescrita. Um fato que nasce apontando para o token é o mesmo desenho um passo antes: quando
`student` existir, `exam_roster` ganha a chave e o caminho `(exam_id, student_token)` → `exam_roster`
→ `student_id` resolve o histórico inteiro por junção, sem tocar em fato nenhum.

**O que fica registrado como decisão, e não como descoberta.** ADR-0003 diz "a implementação fica para
a 3, que é quando os fatos passam a existir". Os fatos não passaram a existir na 3 — passam a existir
**aqui**. O gatilho dele dispara nesta fatia, e o que esta fatia responde é só metade: o fato nasce
chaveado pelo identificador que existe. A outra metade — `student`, `student_alias` e o read model do
boletim — continua onde ADR-0003 a pôs. **Isto é atualização de decisão registrada com informação
nova, e não substituição**, então não abre ADR; mas a linha pertence ao ADR-0003, e não só aqui, e
levá-la para lá é tarefa desta mudança.

*Alternativa descartada:* resolver o token para um `student_id` no servidor, no momento do push. Exige
a tabela que não existe, e seria a segunda estratégia de resolução de identidade que a fatia proíbe.

### 2. O resultado por item é evidência, e não pontuação por habilidade

`ObjectiveScore` ganha a lista por questão: item, resposta lida, quanto valia, quanto rendeu. Sai do
mesmo laço que já produz o total — não há segunda apuração, e por isso não há como os dois divergirem
por caminhos diferentes.

A coerência é afirmada no contrato, não presumida: a soma dos pontos rendidos é igual ao total, e o
conjunto de itens sem decisão é exatamente a lista de pendências. Duas travas sobre o mesmo número, do
mesmo jeito que `ObjectiveScore` já faz com `points in 0..maxScore`.

**Nada disto toca habilidade.** O vínculo item→habilidade continua onde está, dentro do `ExamPackage`
(`ItemSkill`, obrigatória e não-vazia por I1). Esta fatia não o lê, não o soma e não o converte em
ponto. O que ela faz é não jogar fora o dado de que a derivação precisará: com item e pontos por
questão gravados, `assessment_fact` é derivável depois por junção com o pacote — que é imutável e
hasheado, e portanto ainda estará lá dizendo a mesma coisa.

*Por que não deixar para depois:* os fatos são append-only e a folha de papel some. Sem a evidência,
as provas corrigidas nesta janela ficam sem dimensão analítica **para sempre** — não por falta de
código, mas por falta do dado.

### 3. Uma tabela no aparelho, e ela é a fila

O resultado durável e a linha da fila são **a mesma linha**. Uma tabela Room, `resultado_pendente`, com
o resultado e o estado de envio.

Isso cai exatamente sobre a classe H: ela compreende "observações pendentes de sincronização" e manda
eliminá-las "após a sincronização bem-sucedida". Um histórico local separado dos pendentes seria uma
**segunda** cópia de dado no aparelho, fora do que a classe H descreve, sem consumidor no fluxo (P18) e
com regra de apagamento a inventar — que é o que a decisão 4 do mantenedor proíbe.

*Consequência aceita:* depois de sincronizado, o resultado não é mais consultável no aparelho. A nota
está no servidor, e a tela mostra a folha recém-escaneada durante a sessão. Ninguém pediu histórico
local, e ele custaria uma categoria de retenção nova.

### 4. A chave de idempotência é gerada no aparelho, por captura

Cada captura apurada gera um identificador próprio, gravado com o resultado. Ele viaja no envio.

- **Reenvio do mesmo resultado** — a confirmação que se perdeu no caminho — chega com o mesmo
  identificador, e o servidor reconhece e responde como respondeu antes. Nada novo é gravado.
- **Recaptura da mesma folha** é outra captura, logo outro identificador: o servidor grava **revisão
  nova** para o mesmo `(exam_id, student_token)`, e ela passa a ser a corrente.

A distinção entre "é o mesmo" e "é de novo" fica onde a informação existe — no aparelho, que sabe se
houve uma segunda passada da folha pela câmera. O servidor não precisa adivinhar comparando conteúdo,
e comparar conteúdo seria errado: uma recaptura que desse exatamente a mesma nota é recaptura, e não
reenvio.

### 5. Duas tabelas no servidor, com os nomes que a arquitetura já usa

`grading_result` e `answer_observation` são os nomes da §11, e é o que elas guardam:

```
grading_result   (exam_id, student_token, revision, origin='omr',
                  package_hash, variant_id, points, max_score, closed, ...)
        |  append-only; revisão nova nunca toca a anterior
        v
answer_observation  (item_id, resposta lida, pontos do item, pontos rendidos)
        |  a evidência por questão da decisão 2
```

Ambas nascem com RLS habilitada e forçada, e com finalidade e classe de retenção declaradas no
catálogo — **classe B**, "fato de avaliação e nota: pontuação por questão, nota final". Sem a
declaração, `RetentionDeclarationTest` reprova a construção, e é para isso que a guarda existe.

`capture_session` fica de fora. Ela é a sessão de captura por regiões (D18), e o que a exercita é lote
e modo degradado.

### 6. Sair preserva o pendente; revogação apaga

A classe H dá o prazo em duas frases, e elas não dizem a mesma coisa. A primeira: "eliminados
automaticamente **após a sincronização bem-sucedida**". A segunda: "o encerramento de sessão ou a
desinstalação do aplicativo eliminam a base local".

Vale a primeira, e o motivo é o que os dois dados são. Roster, pacote e visão guardada são **cópia de
referência puxada do servidor** — apagar é grátis, porque o original está lá e a cópia se refaz na
entrada seguinte. O resultado pendente é o contrário: ele **só existe no aparelho** até subir. Apagá-lo
ao sair não elimina uma cópia, destrói o único exemplar de um trabalho já feito.

Então `DeviceSession.sair` continua apagando os três, e o pendente fica. O aparelho avisa quantos não
subiram, e o aviso é informação, não pedido de permissão para apagar.

**A revogação apaga a referência, e não o pendente.** Ela é o terceiro caminho de apagamento que a
`slice-4b-roster-no-aparelho` estabeleceu, e continua levando roster, pacote e visão guardada. Mas a
regra da classe H é a mesma nos três: pendente sai **depois** da sincronização, e nenhum evento local a
antecipa. Revogar o vínculo de um professor não é motivo para destruir a correção que a escola já fez —
ela pertence à organização, não a quem segurava o aparelho.

*Primeira redação desta decisão dizia o contrário*, e fica dita em vez de apagada (P7): ela tratava a
revogação como caso simétrico ao do roster, e o argumento — "preservar fato de avaliação de uma escola
num aparelho que perdeu acesso a ela inverteria o que a revogação existe para fazer" — confundia dado de
referência com trabalho. O dado de referência copiado do servidor tem original do outro lado; o pendente
não tem.

**Quem empurra o pendente preservado.** O resultado de leitura óptica é fato sobre a folha, e
`grading_result` o marca com `origin: omr` — não há atribuição a professor a preservar. Então o pendente
é escopado pela **organização**, como o resto do dado local, e sobe na sessão seguinte de qualquer membro
dela. Escopo por usuário seria uma quarta regra de apagamento sem nada que a exija.

**O que esta decisão deixa em aberto, e não pode ficar calado (P20).** A segunda frase da política §10.8
passa a descrever comportamento que o código não tem, e o teto que sobra para o pendente preservado é o
`[30]` entre colchetes — que a linha 12 da política declara como prazo **proposto**, não fixado. É a
mesma forma de lacuna que o §16 registra para a classe H e o roster: a redação final tem **jurídico
externo** como dono. A tarefa 8.2 escreve a divergência e o dono; esta mudança não edita a política.

### 7. Room, WorkManager, e o processador de anotações

Room e WorkManager estão em §13 e em D21 — não é tecnologia nova, e não exige ADR. Entram no catálogo.

Room precisa de um processador de anotações. Ele entra como **ferramenta de build**, e não como
decisão de arquitetura: nada do comportamento do sistema depende de qual processador gera o código do
DAO. Fica registrado aqui em uma linha e em lugar nenhum além.

### 8. A ordem é contrato, servidor, aparelho

1. `ObjectiveScore` ganha o resultado por item, em KMP, com os consumidores atuais ajustados.
2. Migration, RLS e rota de escrita no servidor, exercitadas contra banco real.
3. Room, fila e envio no aparelho, contra a rota que já existe.

Regra 1: contrato antes de consumidor. E é a ordem que deixa cada passo verificável sozinho — o
aparelho chega por último, quando já há onde bater.

## Risks / Trade-offs

**Um pendente pode ficar sem entregador, e o `[30]` vira o único limite dele.** Depois da revogação, o
usuário revogado não consegue mais enviar: o servidor recusa resultado de organização de que o
autenticado não é membro, e essa recusa — corretamente — não apaga o pendente. Num aparelho compartilhado
de escola o caso se resolve, porque o pendente é escopado pela organização e sobe na sessão de outro
membro. Num aparelho pessoal de quem saiu da escola, não se resolve: o fato de avaliação fica lá, e o
único limite escrito passa a ser o `[30]` entre colchetes que a linha 12 da política declara como prazo
**proposto**. → Mitigação: nenhuma dentro desta fatia. **Não é mitigado, é conhecido** (P8), e a tarefa
8.2 o registra com o dono que ele já tem — jurídico externo. O que esta decisão muda é o peso daquele
colchete: ele deixa de ser teto de conveniência e passa a ser a única coisa que limita dado pessoal
indevolúvel num aparelho.

**A desinstalação continua sem teste desta base.** O Android apaga o `filesDir`, e a base Room vai com
ele, mas isso é garantia da plataforma e não do aplicativo. → Mitigação: fica dito como **herdado**, e
não como verificado — a mesma forma que a `slice-4b-roster-no-aparelho` usou para o roster.

**A evidência por item pode divergir do total.** Dois números sobre a mesma apuração é exatamente a
forma de defeito que `ObjectiveScore` já teve uma vez — a nota de 41 de 40 que a guarda de construção
pegou. → Mitigação: a evidência sai do mesmo laço que o total, e a coerência é afirmada como requisito,
não presumida.

**Confirmação perdida gera reenvio, e reenvio mal resolvido gera nota duplicada.** É o caso rotineiro do
modelo offline, não a exceção. → Mitigação: decisão 4, com cenário de reenvio e cenário de recaptura
caindo em conjuntos diferentes.

**Esta fatia atravessa KMP, Android, API e banco.** São duas capabilities — `scoring` e `result-sync` —,
dentro do que a regra 3 admite, mas quatro módulos. → Mitigação: a decisão 7 ordena por contrato, e
cortar o fluxo em pedaços menores produziria a camada horizontal com nome de fatia vertical que §15
descreve: uma fatia de tabelas que nada exercita, ou uma de Room que empurra para lugar nenhum.

**O expurgo local é afirmação sobre ausência, e ausência é fácil de afirmar sem conferir.** → Mitigação:
o cenário confere o armazenamento depois da confirmação, e não o retorno da função que apaga — é o
mesmo método que a `slice-4b-roster-no-aparelho` usou para o apagamento do roster.

## Migration Plan

A migration do servidor só acrescenta tabelas: nada existente é alterado, e reverter é removê-las
enquanto nada gravou. Depois do primeiro resultado real, reverter passa a descartar fato de avaliação —
e é por isso que a forma da chave (decisão 1) é decidida agora e não depois.

No aparelho, a base Room nasce nesta versão. Não há dado anterior para migrar: hoje nada é persistido.

## Open Questions

- **Com que frequência a fila tenta de novo** depois de uma falha de rede. Não muda spec, contrato nem
  a divisão de tarefas — é parâmetro de agendamento, e escolhê-lo antes de ver o envio funcionando seria
  número sem consumidor.
