# Arquitetura Final — v3

**Plataforma de Avaliação Educacional com IA**
**Data:** 2026-08-13 · **Mantenedor:** 1 pessoa · **Status:** referência **vigente** — o texto abaixo diz o que vale hoje; o que mudou desde então, e por quê, está nos ADRs e em [`HISTORICO-v3.md`](HISTORICO-v3.md)

> Este documento substitui a v1, a v2 e o documento de design. É a referência única.
> As decisões aqui são invariantes: mudá-las exige um ADR novo, não uma conversa.

---

## 0. Como usar este documento

| Se você quer… | Vá para |
|---|---|
| Entender o sistema inteiro em 5 minutos | §1 e §2 |
| Saber por que uma coisa é do jeito que é | §17 (registro de decisões) |
| Implementar | §15 (roadmap), depois a spec da fatia |
| Modelar o banco | §11 |
| Entender a folha impressa | §7, §8 e `mockup-prova.html` |

---

## 1. O sistema em uma página

Quatro módulos sobre um núcleo comum:

| Módulo | O que faz | Status |
|---|---|---|
| **M1 — Geração** | Cria provas (IA ou manual), publica o `ExamPackage`, renderiza o PDF | Especificado |
| **M2 — Correção** | Captura por região, OMR offline, correção discursiva opcional por IA | Especificado |
| **M3 — Boletim** | Agrega desempenho por habilidade e período | Preparado, não especificado |
| **M4 — Planejamento** | Gera plano de aula a partir das lacunas da turma | Preparado, não especificado |

**A tese central da arquitetura:** M3 e M4 não são módulos novos — são **leituras** sobre fatos que M1 e M2 produzem. Se as invariantes de §2 forem respeitadas, eles custam prompts e read models, não re-arquitetura.

---

## 2. As invariantes

Tudo o mais é negociável. Estas não são.

**I1 — Toda questão nasce marcada por habilidade da BNCC.**
Sem isso, boletim por competência e diagnóstico de turma são impossíveis, e retro-marcar milhares de questões geradas é caro e impreciso.

**I2 — Resultados são fatos append-only atribuídos a habilidade.**
`assessment_fact(aluno, habilidade, avaliação, pontos, período)`. M3 vira `GROUP BY`. M4 vira "quais habilidades estão abaixo do limiar". Nenhuma tabela nova.

**I3 — Todo artefato gerado por IA carrega `prompt_version` + `model_id` + `params_hash`.**
Prompts vivem em arquivos no Git. Sem isso não há reprodutibilidade, auditoria de contestação de nota, nem controle de regressão.

**I4 — Todo item nasce com proveniência e licença.**
Autoria humana, geração por IA ou origem externa, e o que pode ser feito com o item. `visibility: public` é condicionado à licença. É o mesmo argumento de I1: a fatia 6 gera itens em volume, e retro-atribuir milhares depois é caro e impreciso. ADR-0005.

**I5 — Artefato imutável nunca contém dado pessoal direto.**
O que é imutável, hasheado e copiado para dispositivos offline não pode carregar nome, turma ou matrícula: o direito de eliminação não alcança cópia imutável já distribuída. Operacionalizada pela separação do roster. ADR-0002, ADR-0006.

As cinco são verificáveis: cada uma reprova um desenho concreto. Regras que não podem ser reprovadas por teste — "toda tabela com dado pessoal declara finalidade" é a principal — ficam como exigência de ADR e critério de revisão de migration, não como invariante. Invariante que nada consegue reprovar enfraquece as que existem.

---

## 3. Tenancy, planos e a trajetória para Enterprise

### 3.1 O problema da migração — e por que ele não vai existir

A preocupação é legítima: se o professor "pertence" a uma organização pessoal e um dia precisa "virar" membro de uma escola, isso é uma migração de dados dolorosa — re-parentear linhas, quebrar RLS no meio do caminho, deduplicar alunos, reconciliar assinatura.

**O atrito só existe se você assumir que o professor precisa *mudar de lugar*.** A saída é modelar pertencimento em vez de propriedade:

> **`membership` N:N — um usuário pode pertencer a várias organizações simultaneamente.**

É o modelo do GitHub (conta pessoal + organizações) e do Slack (múltiplos workspaces). Com ele, "entrar numa escola" deixa de ser migração e vira **inserção de uma linha**:

| Momento | O que acontece |
|---|---|
| Cadastro | Cria `organization(kind: personal)` + `membership(user, org, role: owner)` |
| Escola contrata | Cria `organization(kind: school)`; admin convida professores |
| Professor aceita | Insere `membership(user, school_org, role: teacher)`. **Nada é movido.** |
| Trabalho novo | Acontece na organização da escola (seletor de contexto na UI) |
| Acervo antigo | Continua na organização pessoal, acessível. Itens podem ser **copiados** para a escola sob demanda, com proveniência via `source_item_id` (já previsto em D5) |

Nenhum UPDATE em massa, nenhuma janela de inconsistência, nenhum aluno duplicado forçado.

### 3.2 As duas regras que preservam isso

1. **Toda tabela de domínio é chaveada por `organization_id`. Nunca por `user_id`.** `created_by_user_id` existe como metadado, jamais como chave de autorização. Escrever uma política de RLS baseada em usuário é o único erro caro possível aqui.
2. **A assinatura pertence à organização, não ao usuário.** Um professor com plano Pro pessoal que também é membro de uma escola no plano Enterprise simplesmente tem direitos diferentes em cada contexto. Sem conflito, sem merge de assinatura.

### 3.3 O que o plano de escola realmente destrava

Já está arquitetonicamente preparado:

| Recurso Enterprise | Preparação já existente |
|---|---|
| Produção conjunta de provas | Op-log de rascunho (D27) + Realtime |
| Banco de itens da escola | `item.visibility: school` (D5) |
| Boletim consolidado da instituição | `assessment_fact` + política de notas por organização |
| Papéis (coordenação, direção) | `membership.role` |

### 3.4 Planos e cobrança

Dois planos no lançamento, sem camada gratuita, cobrança mensal / semestral / anual.

| | **Basic** | **Pro** |
|---|---|---|
| Público | Professor de uma disciplina, 1–2 turmas | Professor com várias turmas |
| Geração de prova por IA | Sim, quota menor | Sim, quota maior |
| Correção objetiva (OMR) | **Ilimitada** — é local, custo marginal zero | Ilimitada |
| Correção discursiva por IA | Não | Sim, com quota |
| Autoria manual | Ilimitada | Ilimitada |

A lógica comercial é sólida e vale registrar porque ela molda a arquitetura: **o Basic vende o fim do Word e a correção rápida, não a IA.** A correção objetiva é ilimitada porque roda no dispositivo e não custa nada — é o recurso mais barato de entregar e o de maior valor percebido diário. A IA generativa é o gancho de conversão para o Pro.

**Modelo técnico — ledger unificado.** Quotas de plano e eventuais pacotes avulsos usam a mesma estrutura, o que evita reescrever a cobrança quando add-ons aparecerem:

- `subscription(organization_id, plan, billing_period, status, current_period_start/end)`
- `credit_ledger` append-only: crédito na virada do período (concessão do plano), débito no uso, crédito em compra avulsa
- **Entitlements como arquivo versionado no Git**, não linhas no banco — mesma disciplina dos prompts. `plans/basic.yaml`, `plans/pro.yaml`
- Verificação de direito em **um único lugar** no domínio, nunca espalhada
- `hold` no enfileiramento e captura na conclusão, para que um job que falha não consuma quota

**Pagamento:** um único provedor que cubra assinatura recorrente, cartão, Pix e boleto no Brasil. É uma integração, não uma arquitetura — deixe para a fatia comercial e não deixe vazar para o domínio: o domínio conhece `subscription` e `ledger`, não o gateway.

---

## 4. Componentes

```mermaid
graph TB
    subgraph CORE["NÚCLEO"]
        ORG["Organizações & Membership<br/>personal · school"]
        SUB["Assinatura & Ledger<br/>entitlements versionados"]
        CUR["Currículo BNCC"]
        BANK["Banco de Itens<br/>private · school · public"]
        FACTS["Fatos de Avaliação<br/>append-only por habilidade"]
        GW["AI Gateway<br/>prompts · schema · cache · custo"]
    end

    subgraph SHARED["packages/domain (KMP)"]
        MEA["Medição de texto<br/>métricas do TTF, Kotlin puro"]
        LAY["Layout Engine<br/>função pura → LayoutMap"]
        SCO["Scoring · Bloom · Distribuição"]
    end

    subgraph M1["M1 — Geração"]
        DR["Rascunho: op-log"]
        GEN["Gerador IA / autoria manual"]
        PUB["Publicação → ExamPackage"]
        REN["Renderizadores<br/>pdf-lib (web) · Canvas (Android)"]
    end

    subgraph M2["M2 — Correção"]
        SY["Sync: pull pacote / push observações"]
        CAP["Captura por região<br/>ArUco → homografia → QR → OMR"]
        TRA["Transcrição<br/>OCR / fórmula / nenhuma"]
        REV["Revisão do professor"]
    end

    ORG --> SUB
    CUR --> BANK
    DR --> GEN --> BANK
    BANK --> PUB
    MEA --> LAY --> PUB
    PUB --> REN
    PUB -->|ExamPackage| SY --> CAP --> TRA --> REV
    SCO --> CAP
    REV --> FACTS
    TRA -.-> GW
    GEN -.-> GW
    SUB -.->|quota| GW
    FACTS --> M3["M3 Boletim"]
    FACTS --> M4["M4 Planejamento"]
    CUR --> M4
```

---

## 5. O `ExamPackage`

Contrato central. Produzido na publicação, **imutável**, com hash, ~110 KB em JSON puro.

```
ExamPackage
├── meta            exam_id, short_id, layout_engine_version, min_renderer_version,
│                   content_hash, fully_offline_gradable,
│                   prompt_version, model_id, params_hash          ← a tripla de I3
├── items[]         enunciado, formato, alternativas, bloom, dificuldade, habilidades BNCC,
│                   rubrica analítica (critérios · descritores · pontos · expected_lines),
│                   assets (SVG de fórmula, imagens), answer_capture_mode
├── variants[]      variant_id, mapa posição física → item_id
├── assignments[]   student_token → student_id, nome impresso, turma, variant_id
├── layout          por variante: páginas, regiões escaneáveis, quads ArUco,
│                   coordenadas normalizadas de bolhas e molduras
├── answer_key      item_id → alternativa correta, pontuação
└── scoring         pesos, composição, nota máxima
```

> `params_hash` faz parte da tripla de I3 **desde sempre**; uma versão anterior desta lista o omitia, e o `PackageMeta` foi escrito com dois dos três campos (ADR-0014, decisão 1). **A lista ilustrativa ao lado de uma invariante é lida como se fosse a invariante** — por isso a lista acima traz os três nomes. O texto da correção: [`HISTORICO-v3.md`](HISTORICO-v3.md).

**Compressão:** nenhuma explícita. `Content-Encoding` do CDN no transporte e TOAST do Postgres no repouso já entregam ~70%. Comprimir à mão economiza ~77 KB por prova e custa código nos dois clients. **Imagens nunca entram no JSON** — vão para o Storage por referência.

---

## 6. Layout Engine e renderização

A renderização é **100% client-side**. O risco disso — dois renderizadores divergirem e quebrarem o OMR silenciosamente — é neutralizado por quatro mecanismos:

**1. Separação de cálculo e desenho.** O `LayoutMap` é uma função pura no módulo KMP, calculada **uma vez** na publicação e persistida. O PDF é projeção descartável. O OMR lê o `LayoutMap`, nunca o PDF.

**2. Coordenadas normalizadas ao quad.** Dentro de uma região escaneável, tudo é `(u,v) ∈ [0,1]²` do quadrilátero dos 4 ArUcos do gabarito; na região discursiva, do retângulo entre os dois marcadores (§8, ADR-0018). Imunidade automática a escala de impressão, tamanho de papel, DPI e distância da câmera.

**3. Geometria rígida isolada de texto fluido.** Enunciados podem reflowar entre renderizadores sem consequência; só as regiões escaneáveis têm geometria fixa. É isto que torna dois renderizadores aceitáveis.

**4. Medição de texto própria.** Este é o keystone e o trabalho mais subestimado do projeto: para o layout ser idêntico entre plataformas, a **medição** precisa ser idêntica — e é exatamente isso que cada plataforma faz diferente. A solução é medição em Kotlin puro sobre tabelas `cmap`/`hmtx`/`kern` extraídas do TTF embarcado, versionadas como recurso. Nenhuma API de plataforma envolvida → resultado idêntico em JVM, Android e JS. ~400–700 linhas.

**Guardas complementares:** fonte embarcada (nunca do sistema); `min_renderer_version` no pacote, com client desatualizado **recusando imprimir**; e teste de paridade em CI que rasteriza o mesmo `LayoutMap` nas duas plataformas e afirma tolerância de 0,3 mm nos centroides de ArUcos e bolhas.

**Renderizadores:** o KMP emite uma lista de primitivas (`DrawRect`, `DrawCircle`, `DrawText`, `DrawImage`, `DrawAruco`); cada plataforma só traduz primitiva → API nativa. ~200 linhas cada. Web: `pdf-lib`. Android: `PdfDocument`/`Canvas`.

**Validação server-side sem renderizar:** o servidor valida o `LayoutMap` publicado (schema, IDs únicos, regiões sem sobreposição, coordenadas em faixa). Custo desprezível, impede que um bug de client publique prova ilegível.

**Matemática:** LaTeX/MathML → **SVG na publicação**, tratado como caixa de dimensão conhecida. Converte tipografia matemática num problema de empacotamento já resolvido. O SVG é armazenado junto ao item.

---

## 7. Design da folha

Referência visual: `mockup-prova.html` (escala real).

**Grade e tipografia.** A4, margens 15 mm laterais e inferior / 14 mm superior, 8 mm reservados para grampo. **Grade vertical de 3 mm** — toda altura de bloco é múltiplo dela, o que torna a paginação um problema de inteiros e alinha o passo das bolhas (6 mm) ao ritmo do texto. Corpo serifado 9,5 pt / entrelinha 1,4; comando da questão em semibold; número pendurado na canaleta esquerda com a pontuação abaixo. Monocromático, tramas ≤ 8%.

**Dados do aluno são impressos, não preenchidos.** Como `exam_assignment` fixa aluno ↔ variante ↔ token na publicação, o nome, a turma e o número já são conhecidos na renderização. Imprimi-los elimina a prova sem nome, o nome ilegível e o aluno que escreve a turma errada — três fontes clássicas de retrabalho. Fica um campo de assinatura opcional, para escolas que exigem.

Consequência: cada folha é única por aluno (já era, por causa de token e variante). A impressão é um PDF colacionado, um documento por aluno.

**Caso de exceção — aluno fora da lista** (transferido, matrícula tardia): a UI gera uma **folha avulsa** com campos em branco e QR contendo apenas prova + variante. A atribuição ao aluno é feita manualmente depois da captura. Sem isso, o modelo de dados impressos vira uma armadilha no dia da prova.

**Folha de respostas no topo da página 1.** Escolha pelo fluxo do professor: uma pilha de provas puramente objetivas é escaneada **sem folhear**. Numa turma de 30, é a diferença entre um minuto e cinco. O **cartão-resposta destacável** existe como **opção** para provas longas, nunca como padrão.

**Mitigação do erro de transcrição.** O bloco consolidado é certo para o produto, mas carrega o salto de linha do aluno. Mitigações: faixa alternada a 4,5% de preto, grupos de 3–5 questões, número em bold à esquerda de cada linha, colunas curtas, letra da alternativa impressa em cinza dentro do círculo. E uma que só existe porque o sistema é digital: **detector de deriva** — se o aluno acerta as primeiras e erra as seguintes com padrão de deslocamento de uma linha, isso é assinatura de salto, não de desconhecimento. Sinalizar ao professor custa poucas dezenas de linhas sobre dados que já existem.

**Geometria (restrições de CV):**

| Parâmetro | Valor |
|---|---|
| Bolha: diâmetro / passo H / passo V / traço | 4,2 mm / 5,2 mm / 6,0 mm / 0,22 mm |
| ArUco: lado gabarito / lado discursiva / zona de silêncio | ≥ 12 mm / ≥ 10 mm / ≥ 1 módulo |
| Pauta discursiva | **7 mm, em cinza claro** — do lado decorativo do ADR-0010, como a letra dentro da bolha: guia para o aluno, e não geometria para a câmera (ADR-0016) |

> A razão da pauta, e o valor que ela substituiu, estão em ADR-0016 e em [`HISTORICO-v3.md`](HISTORICO-v3.md). A hipótese sobre leitura que a pauta serve **nunca foi medida**; a medição continua sendo a da linha "Acurácia em manuscrito" do §16, sobre a pauta de 7 mm.

**Paginação.** Medir → agrupar em super-blocos indivisíveis (enunciado+alternativas; enunciado+moldura; texto-base+dependentes com penalidade) → **DP minimizando `Σ(sobra)² + penalidades`** → posicionar regiões → emitir. O quadrado da sobra distribui o vazio em vez de empurrá-lo para o fim. Colunas: **adaptativo** — 2 por padrão, blocos largos atravessam, 1 quando houver muito conteúdo largo.

**A paginação redistribui as questões (ADR-0019).** A DP acima distribui uma sequência **fixa**: ela nunca muda a ordem das questões (`Pagination.kt`). Quem escolhe a ordem é a redistribuição: não sobra, no meio da prova, espaço onde uma questão caberia de maneira **ideal**, que é inteira, com o espaçamento normal e sem nada comprimido.
- Encaixe forçado é proibido.
- A ordem do professor desempata. A ordem impressa já é por variante (§5, "mapa posição física → `item_id`").
- A **numeração impressa é a da folha**, e o gabarito, os chips de completude e os relatórios usam esse número.
- Os super-blocos se movem inteiros, e a ordem sai determinística.
- A complexidade O(N²), em milissegundos, é a da DP sobre sequência fixa. **Escolher a ordem é empacotamento**, e a busca é heurística.

**Área discursiva declarada pelo professor (ADR-0017).** O professor declara, questão por questão, o número de linhas e a largura — uma coluna ou a página —, **sem valor padrão**. `expected_lines` continua na rubrica, como o que cada critério espera, e a resposta continua avaliada contra ela; mas **a rubrica não define o espaço**, e a cadeia "rubrica → espaço" não existe. A questão de largura de página ocupa uma faixa própria, e o fluxo das colunas continua antes e depois dela (o "blocos largos atravessam" da paginação). Nunca maior que uma página — se a rubrica pede mais, a questão vira itens (a), (b), (c).

**Economia de papel:** densidade em três níveis (espaçado/normal/compacto) dentro de faixas seguras para o CV, contador de páginas ao vivo, e sugestão automática quando a última página tem menos de 25% de ocupação.

---

## 8. Regiões escaneáveis e pipeline de captura

**Modelo.** Sempre uma região `ANSWER_BLOCK` (gabarito + QR ao lado, dentro de **4 ArUcos**). Apenas se houver discursivas, uma região `ESSAY_REGION` por questão (ADR-0018), com **dois ArUcos na diagonal** — `4k` no canto superior esquerdo e `4k+3` no inferior direito — e o **QR no canto superior direito**, na faixa do marcador, que **ancora o terceiro canto**. As coordenadas da região discursiva são as do retângulo entre os dois marcadores. O canto inferior esquerdo é extrapolado, e a folga do recorte o cobre até a medição em papel.

**A moldura discursiva contém apenas a área de resposta.** O enunciado fica fora. Três razões, em ordem de peso: você já tem o enunciado em texto exato no pacote (fotografá-lo é pagar tokens de visão para reconstruir com erro um dado que você possui); a geometria da região precisa ser previsível; e o recorte limpo evita que o modelo "responda o enunciado" em vez de avaliar a resposta.

**Capturas auto-descritivas.** Cada QR carrega `{exam_short_id}.{student_token}.{variant}.{region_idx}.{crc}`. Com isso **não existe estado de sessão para corromper**: o professor pode escanear fora de ordem, embaralhar folhas, ser interrompido — nada produz atribuição errada. Os ArUcos dão redundância: `region_idx` é validado contra os IDs dos marcadores.

**IDs de ArUco.** Região `k` usa `{4k…4k+3}`, dicionário `DICT_5X5_100` → 25 regiões. Como objetivas não consomem IDs, o teto limita apenas questões discursivas — uma prova com 60 objetivas e 6 discursivas usa 7 regiões. Folgadíssimo.

**Ordem do pipeline (corrige o §7 do SAD-02):**

```
frame → detecta ArUcos → identifica região pelos IDs → homografia
      → decodifica QR na ROI prevista (já retificada) → OMR / recorte
```

O QR é decodificado sobre imagem desempenada — muito mais fácil que sobre a foto em perspectiva — e a busca ocorre em ~15% do frame.

**Na região discursiva** (ADR-0018) a primeira homografia sai dos dois ArUcos, o QR é lido na ROI já retificada, e só depois os padrões de posição dele entram num segundo ajuste, que dá o recorte.

**Escaneamento em lote.** Prova puramente objetiva = uma captura por aluno. O app detecta, confirma por som/vibração e avança sozinho: 30 alunos em cerca de um minuto, sem tocar na tela. Só é seguro porque a captura é auto-descritiva.

**Completude.** O pacote declara o conjunto esperado de `region_idx` por variante; o app mantém o conjunto capturado; a UI mostra chips numerados (verde/cinza/âmbar) e um contador. Variante divergente é rejeitada com mensagem específica. Finalizar incompleto exige confirmação explícita e registra o que faltou.

**Deviants.** Resposta que extrapola muito a moldura é detectada por proporção de tinta fora do quadrilátero e marcada para conferência manual, junto do alerta de deriva de transcrição. É minoria numa turma e corrigir 2 de 30 à mão é efeito colateral tolerável — desde que o sistema **avise**, em vez de corrigir errado em silêncio.

**Modo de cor.** A captura do gabarito é sempre monocromática — o gabarito é preto e branco por construção. O recorte discursivo é cinza por padrão, com `answer_capture_mode: color` como exceção declarada no item (resposta com gráfico colorido, lâmina, mapa). Um toggle na captura permite forçar cor pontualmente e registra o evento; forçar com frequência é sinal de autoria marcada errado.

**Imagens dentro das questões** são coisa distinta: ficam em cor no Storage, mas a publicação gera uma **versão print-safe em cinza** e a autoria **avisa** quando a imagem perde significado em preto e branco ("este gráfico distingue séries por cor"). Impressora de escola é monocromática; descobrir isso depois de imprimir 30 provas é caro.

---

## 9. Correção discursiva e economia de custo de IA

Sua leitura do problema está certa: o custo somado de dezenas de professores corrigindo por imagem é o que pode afugentar escola. Mas a ordem dos ganhos não é intuitiva.

### 9.1 Os números

Uma questão discursiva, turma de 30, prompt fixo (enunciado + rubrica + instruções) ~600 tokens, resposta como imagem ~950 tokens, como texto ~180, saída ~300:

| Caminho | Entrada por turma | Redução |
|---|---|---|
| Imagem, sem cache | 46,5 k | — |
| **Imagem, com cache de prefixo** | **29,1 k** | **37 %** |
| Texto (OCR), sem cache | 23,4 k | 50 % |
| **Texto (OCR) + cache de prefixo** | **6,0 k** | **87 %** |

Três conclusões:

1. **O caching de prefixo é o primeiro lever, não o OCR.** O enunciado e a rubrica são idênticos para os 30 alunos. Estruturar o prompt com o prefixo invariante primeiro corta 37% **sem risco algum de acurácia e sem escrever um modelo**. Fazer isso antes de qualquer outra coisa.
2. **O OCR vale a pena, mas menos do que parece isoladamente** — o ganho grande é a combinação.
3. **Existe um quarto lever maior que os dois:** corrigir contra rubrica analítica explícita é tarefa constrita; um tier de modelo mais barato pode bastar. Validar isso contra um eval set pode render mais que OCR e caching juntos.

### 9.2 O limiar de confiança

`>0,905` é um chute razoável, mas precisa ser calibrado — e sobretudo **calibrado contra a métrica certa**:

> O alvo não é "o texto está perfeito". É **"a nota não muda"**.

Um erro de OCR que não altera o julgamento é inofensivo. Então a calibração é: sobre um corpus real, corrija cada resposta pelos dois caminhos e encontre o limiar acima do qual a divergência de nota fica abaixo do aceitável. Essa métrica quase certamente permite um limiar **mais baixo** que a exigência de texto perfeito — ou seja, mais economia, não menos.

Confiança de OCR é notoriamente **descalibrada** em manuscrito (o modelo erra com convicção), então o número tem que sair de medição, nunca de intuição.

**Rede de segurança auto-calibrante:** guarde sempre a imagem e registre por qual caminho a nota saiu. Se overrides do professor forem sistematicamente mais frequentes no caminho texto, o limiar sobe sozinho. Custo: uma coluna e uma consulta.

### 9.3 TexTeller — viabilidade

Verifiquei. É um vision-encoder-decoder ViT de **0,3 B parâmetros**, com export ONNX, treinado em milhões de pares imagem-fórmula; a versão 3 declara suporte a fórmula manuscrita.

**O que isso significa na prática:**

| Aspecto | Avaliação |
|---|---|
| **Client-side (Android)** | Inviável em v1. 0,3 B em INT8 ≈ 300 MB de download, e decodificação autorregressiva em CPU móvel custa segundos por fórmula, com bateria. |
| **Server-side em CPU** | Plausível. Roda no worker que você já paga, custo marginal próximo de zero — e é aí que ele ganha do LLM de visão. |
| **Custo real de adoção** | Um segundo runtime (Python) ao lado do Kotlin: outro deploy, outra imagem, outra coisa para manter. Contra o objetivo de enxuto. |
| **Segmentação** | Ele reconhece **uma fórmula recortada**. Resposta de exatas é prosa + fórmula + cálculo em várias linhas. Falta uma etapa de detecção de região de fórmula que o TexTeller não faz. |
| **Acurácia no seu domínio** | Suporte a manuscrito é **declarado**, não medido em letra de aluno brasileiro a lápis. Tratar como hipótese a testar. |

**Recomendação: não construir em v1. Construir a costura.**

O `AiGateway` recebe uma abstração `TranscriptionProvider` com implementações intercambiáveis: `NoOp` (manda a imagem), `HandwritingOCR`, `FormulaOCR`. A decisão de qual usar é por item e por confiança. Assim, adotar TexTeller depois é escrever um adaptador — não mexer no pipeline.

E a ordem correta de trabalho é: **medir antes de otimizar.** Na fatia 5 você terá corpus real. Meça qual fração das respostas clareia o limiar; se for baixa (provável em cursiva), o TexTeller não se paga e o caching + tier de modelo resolvem melhor. Se for alta em exatas — onde a escrita tende a ser mais estruturada — aí o investimento se justifica.

---

## 10. Sincronização e offline

Não existe sync bidirecional de entidades mutáveis. Só **pull de referência imutável** e **push append-only**.

| Operação | Rede | Offline |
|---|---|---|
| Gerar prova com IA / publicar | Sim | UI desabilitada com motivo explícito |
| Autoria manual | Não | Ops na fila local |
| Baixar `ExamPackage` | Sim | Gate de pré-voo antes da sessão |
| Renderizar e imprimir | Não | Do pacote em cache |
| Captura + OMR + nota objetiva | Não | Totalmente local e **definitiva** se a prova não tiver discursivas |
| Correção manual de discursiva | Não | Local, entra no outbox |
| Correção por IA | Sim | Enfileirada, executa ao reconectar, debita quota |

**Cache miss:** gate de pré-voo verifica o pacote antes de abrir a sessão; se offline e ausente, **modo degradado** — captura e guarda as imagens brutas para corrigir depois. Nunca falha em silêncio.

**Idempotência:** chave `(exam_id, student_id)` com revisões. Recaptura cria nova revisão; a mais recente é a corrente.

---

## 11. Modelo de dados canônico

**Organização** — `organization(kind: personal|school)` · `membership(user, organization, role)` · `user` · `subscription` · `credit_ledger` · `usage_counter`

**Escolar** — `class_group` · `student(external_ref)` · `enrollment` · `subject` · `term`

**Currículo** — `curriculum_framework` (BNCC, versionada) · `skill(código BNCC)` · `skill_relation`

**Banco de Itens** — `item(visibility, curation_status, source_item_id, content_hash, embedding, answer_capture_mode)` · `item_option` · `item_skill` · `item_rubric_criterion(expected_lines)` · `item_asset(svg/imagem)` · `item_stat(dificuldade empírica, discriminação)`

**Autoria** — `exam_draft` · `exam_draft_op` (append-only)

**Avaliação** — `exam` · `exam_class_group` (atalho de UI) · `exam_variant` · `exam_item` · `exam_layout` · **`exam_assignment`** (fonte da verdade: aluno, variante, token, nome impresso) · `exam_package` (JSON imutável + hash)

**Correção** — `capture_session` · `capture_region` · `answer_observation` (append-only) · `transcription(provider, texto, confiança)` · `grading_result(origin: omr|ai|teacher, path: image|text)` · `grading_criterion_score` · `grade_override`

**Fatos** — `assessment_fact` (append-only)

**Infra** — `sync_cursor` · `outbox_event` · `job` · `llm_call`

---

## 12. Preparação para M3 e M4

Respeitadas I1–I5, sobra pouco a construir:

- **M3 Boletim** = read models sobre `assessment_fact` + política de notas por organização + template de PDF (reusa o Layout Engine). Em organização pessoal, é "relatório da minha turma"; boletim consolidado da instituição é recurso de escola, porque exige as notas de todos os componentes.
- **M4 Planejamento** = diagnóstico (mesmo read model) + retrieval via `pgvector` sobre currículo e itens + prompt versionado + entidade `lesson_plan`.

Componentes construídos em M1/M2 e reusados: `AiGateway`, Layout Engine, Currículo, `pgvector`, ledger.

---

## 13. Stack

**Servidor:** Kotlin + Ktor 3 · **jOOQ** com codegen a partir do schema (drift vira erro de compilação) · migrations pelo Supabase CLI · `kotlinx.serialization` · worker de fila em Postgres com `FOR UPDATE SKIP LOCKED` · Supabase Auth validada por JWKS.

**Banco: PostgreSQL único (Supabase).** Relacional para o núcleo, `jsonb` para layouts e rubricas, **`pgvector`** para o banco de itens e retrieval do M4, full-text `portuguese`, fila de jobs, **RLS** para isolamento por organização. Isso elimina Redis, vector DB, Elasticsearch e camada de autorização artesanal — a razão de a escolha ser certa não é falta de alternativa, é adequação estrutural.

**Compartilhado:** `packages/domain` em KMP — medição de texto, Layout Engine, Bloom, distribuição, scoring, tipos de contrato. Compilado para JVM e Android a partir do mesmo código; divergência é impossível por construção.

**Android:** CameraX · OpenCV (ArUco + homografia) · ZXing-C++ · Room · WorkManager · Compose · `ktor-client` (**não** `supabase-kt`: ADR-0013, decisão 2) · ONNX Runtime **condicional** ao resultado da medição de §9.

**Web:** React + TypeScript + Vite + TanStack Query, tipos gerados do OpenAPI. Duas linguagens no projeto é o mínimo alcançável.

**Operação:** Ktor em container no Render · web estático em CDN · Sentry · logs estruturados · tabela `llm_call` com tokens, custo e latência · GitHub Actions.

**Descartados de propósito:** Kubernetes, Redis, broker, vector DB, microserviços, GraphQL.

---

## 14. Workflow OpenSpec + Claude Code + GitHub

```
openspec/
  project.md
  specs/    identity/ billing/ curriculum/ item-bank/ exam-authoring/
            layout-engine/ print/ exam-package/ sync/ capture-omr/
            ai-grading/ reporting/ lesson-planning/
  changes/
docs/adr/
packages/  domain/ (KMP)   contracts/ (OpenAPI + JSON Schema)
apps/      api/ (Ktor)  web/ (React)  android/ (Kotlin)
CLAUDE.md
```

**As sete regras que produzem menos tokens, menos regressão e menos código descartável:**

1. **Contrato antes de código.** Campo novo? Muda `packages/domain` ou `contracts` primeiro, em commit separado. Maior alavanca anti-regressão do projeto.
2. **Um ADR por decisão de §17.** Numerado, imutável, curto. Em vez de reexplicar a cada chat, aponte: "siga o ADR-0006". É isto que dá consistência entre conversas a custo de tokens quase nulo.
3. **`CLAUDE.md` hierárquico e enxuto.** Raiz ≤ 100 linhas: stack, invariantes, padrões proibidos, como rodar testes.
4. **Uma mudança OpenSpec = um PR = um escopo de chat.** Se toca mais de 2 specs, está grande demais.
5. **Três frentes de teste obrigatórias:** golden corpus de CV (~30 folhas fotografadas em ângulos, luz e letras diferentes, com saída esperada, rodando em CI); snapshot de prompts montados; validação de schema em toda saída de LLM mais eval set por prompt.
6. **Fatias verticais, nunca camadas horizontais.**
7. **Schema como fonte de verdade tipada.** Migrations → jOOQ (Kotlin) e OpenAPI → TS. Divergência é erro de compilação, não bug em produção.

---

## 15. Roadmap

| Fatia | Entrega | Valida |
|---|---|---|
| 0 | Schema núcleo · organização + membership + RLS · assinatura inerte | Tenancy e a forma que evita retrofit |
| **1** | **Medição de texto (KMP) · grade de 3 mm · paginação DP · LayoutMap · dois renderizadores · teste de paridade em CI · impressão real medida com régua** | O risco central do render client-side |
| 1.5 | Renderização matemática **em bloco** (LaTeX/MathML → SVG → raster) | Exatas antes de gerar conteúdo de exatas |
| **1.6** | **Matemática em linha · `InlineBox` com largura, altura e `baseline_offset` na medição de texto** | **O caso predominante das exatas — bloqueadora da fatia 6** |
| **2a** | **Um pacote real: `ExamPackage` publicado · validação server-side · PDF nos dois alvos** | **O contrato do pacote, provado pelo menor artefato que pode reprová-lo** |
| **2b** | **Folha completa · cabeçalho e instrução de preenchimento · mitigação do erro de transcrição · orçamento de tinta na região escaneável · folha de teste de impressão** | **A qualidade de impressão, provada pelo que ela consegue reprovar** |
| 3 | Captura por região · ArUco → homografia → QR → OMR normalizado · nota objetiva offline · escaneamento em lote | **Produto já usável e vendável no Basic, sem gastar um token** |
| 4 | Sync · outbox · roster · gate de pré-voo — **entregue** (4a e 4b; o gate é o `PreparoDaProva.passarPeloGate`, ADR-0013). O **modo degradado** (§10) **não** foi entregue: tem linha própria no §16, com fatia-limite `6` | Modelo offline |
| 5 | Regiões discursivas · completude · deviants · correção manual · **corpus de medição**. **Em andamento:** o que falta e a ordem entre as mudanças estão em `docs/plano-da-fatia-5-restante.md`; a tabela "Ponto de não-retorno" do §16 é o registro de dívida | D1 sem IA, e os dados para decidir §9 |
| 6 | Op-log de autoria · seed BNCC · `AiGateway` com caching · geração por IA · banco de itens | M1 real |
| 7 | Blueprint/distribuição · variantes · `exam_assignment` · dados impressos · folha avulsa | Randomização e impressão fim a fim |
| 8 | Assinatura, quotas e ledger · correção discursiva por IA · revisão do professor | Fecha M2 e a monetização |
| 9+ | Boletim, depois Planejamento | Valida I1–I5 |

Duas escolhas de ordem que valem defender: a **fatia 1 é o Layout Engine**, porque é onde mora o risco que a renderização client-side criou; e a **fatia 3 já é produto** — prova objetiva corrigida 100% offline é exatamente a proposta de valor do Basic, e ela fica pronta antes de qualquer custo de IA existir.


A **fatia 1.6 vem antes da 2**: a matemática de prova de ensino básico é predominantemente **em linha**, e introduzir `InlineBox` na medição de texto antes de a fatia 2 congelar os contratos do `ExamPackage` evita reabrir a medição com o OMR já estabilizado sobre geometria publicada e hasheada. Ela é bloqueadora da fatia 6.

A **fatia 2 foi partida em 2a e 2b** pelo que a entrega consegue **reprovar**. A 2a prova os dois contratos que o menor pacote publicado exercita: o **roster mutável fora do pacote imutável** (ADR-0002, I5) e o **perfil tipográfico no cabeçalho do `LayoutMap`** (ADR-0004). Proveniência de item (I4, ADR-0005), identidade de aluno (ADR-0003) e classe de retenção (ADR-0006) **não** são exercitados por "publicar uma prova fixa": entram como commit de contrato antes do consumidor, na fatia que os exercita, cada um com fatia-limite em §16. Uma fatia cujo critério de aceite não pode falhar é uma camada horizontal com nome de fatia vertical (regra 3 do `CLAUDE.md`).

---

## 16. Riscos

| Risco | Avaliação |
|---|---|
| **Divergência entre renderizadores** | Neutralizado por medição própria + normalização + fonte embarcada + guarda de versão + paridade em CI. **Se o teste de paridade não existir, vira o maior risco do projeto.** |
| **Acurácia em manuscrito** | Maior risco não-arquitetural. Não se resolve por arquitetura — meça na fatia 5 antes de construir a 8. |
| **Impressão dos ArUcos** | Mais superfície sujeita a toner fraco do que o gabarito sozinho: a região discursiva tem 2 marcadores e um QR por questão (ADR-0018). Gabarito com marcador de 14 mm e folha de teste de impressão no onboarding — **entregue na 2b**: a folha de teste é um `LayoutMap` do mesmo engine, com o critério de aprovação impresso nela. **A folha de teste ainda não aprova o marcador de 11,2 mm da discursiva nem a pauta cinza** (linha própria abaixo, fatia `6`) |
| **Uso offline não fecha ponta a ponta (§10)** | **Fechado** pela `slice-4a-cache-referencia` (arquivada em 2026-09-10) e medido em aparelho no mesmo dia: arranque, tela de trabalho, lista e câmera num processo nascido em modo avião (`docs/cobertura-fatia-4a-cache-referencia.md:112-124`). A medição não foi repetida depois. **O modo degradado** (§10: "se offline e ausente, captura e guarda as imagens brutas") **segue não entregue**, em linha própria no ponto de não-retorno |
| **Custo de IA** | Contido por design: Basic não inclui correção por IA; caching corta 37% de graça; quota por plano limita o teto. |
| **LGPD com dados de menores** | **Encaminhado, e não fechado.** ADR-0012 e `docs/legal/politica-de-privacidade.md` fixam base legal por faixa etária, o controlador em cada modalidade, oito classes de retenção e o conteúdo do contrato de operador. O modo sem identificação nominal é o **padrão** de toda organização, e o roster recusa matrícula fora do modo nominal; toda tabela declara finalidade e classe de retenção, com guarda derivada do catálogo. **Entregue:** o cache no dispositivo é apagado ao sair e na revogação (4a), e o expurgo da classe H (4b). **Falta**, e continua sendo risco: a interface (coerção do papel no primeiro cadastro de aluno, política §3.5; bloqueio do roster nominal sem contrato de operador, §4), a retenção da classe B, e o parecer jurídico — cada um com linha no ponto de não-retorno abaixo |
| **Um mantenedor, quatro módulos** | Mitigado pelas fatias verticais e por I1–I5: o escopo cresce sem que o núcleo precise ser reescrito. |

### Ponto de não-retorno

Risco sem ponto de não-retorno flutua: fica sempre "para a próxima fatia" até virar retrofit. O caso
da LGPD foi exatamente isso — o item estava certo, o **registro** é que não dizia quando ele deixa de
ser barato. Tabela separada, e não colunas na de cima, porque cinco colunas de prosa não se leem.

**O formato da coluna "Fatia-limite"** (`rigorous.md` P27; `tools/divida/divida.mjs`). Cada célula começa com um **token entre crases**, e só esse token é lido pela guarda; a prosa que vem depois é para o leitor.

- **O token** é uma fatia (`5`, `2b`, `4a`), `antes-de:<evento>` ou `continuo`.
- **A marca `paga`** vem logo depois do token quando o item foi pago. O limite fica, para que se saiba
  qual era.
- **A fatia corrente** é derivada, e não digitada: é o maior nome `slice-*` em `openspec/changes/`.
- **Quando a guarda reprova, no CI:** quando a fatia de uma linha sem `paga` já passou, ou quando o
  evento dela já ocorreu.
- **Como se reconcilia uma linha vencida:** pagando, ou reagendando para uma fatia-limite nova, com o
  motivo escrito na própria linha.
- **Os eventos** só contam quando declarados na linha abaixo, entre crases. Quem declara um evento
  declara também os que ele implica: `piloto-nominal` implica `primeiro-piloto`.

**Cada linha diz o estado vigente**, em poucas linhas. Quando a informação nova chega, ela **substitui** o estado; a narrativa — o "Atualizado em…", a correção da afirmação anterior, o texto que a linha tinha antes — vai para [`DIVIDA-HISTORICO.md`](DIVIDA-HISTORICO.md), na seção de mesmo nome (`rigorous.md` P7). Linha nova entra aqui, e a narrativa dela entra lá.

**Eventos que já ocorreram:** nenhum.

| Risco | Fatia-limite | O que encarece depois dela | Dono |
|---|---|---|---|
| Divergência entre renderizadores | `continuo` · contínuo, verificado a cada CI | **A partir da 2a** já existe geometria publicada e hasheada, e os dois renderizadores desenham a partir dela; divergir passa a quebrar OMR sobre artefato imutável, e não sobre arquivo de trabalho | mantenedor |
| Acurácia em manuscrito | `6` · medir antes de construir a 8. Reagendada de `5` em 2026-09-26: a 5b-2 trata só da parcial objetiva e não mediu manuscrito. Em dia até a 6 abrir | Construir a 8 sem o número é construir sobre suposição; o critério de reprovação precisa existir antes (ADR-0007). A pauta de 7 mm (ADR-0016) é hipótese sobre leitura que **nunca foi medida**, e a medição é esta | mantenedor |
| Impressão dos ArUcos | `2b` `paga` · **2b — alcançada** | A folha de teste de impressão existe desde a 2b e reprova uma impressora antes da primeira turma; o que encarece agora é folha já distribuída, e a mitigação é anterior a ela | mantenedor |
| Custo de IA | `6` | Depois da geração em volume, caching e quota viram retrofit sobre uso real | mantenedor |
| **LGPD com dados de menores** | `antes-de:primeiro-piloto` · primeiro piloto com turma real. Reagendada de `3` em 2026-09-24, por decisão do mantenedor: nenhum piloto aconteceu, e o evento é o **primeiro** piloto, e não só o nominal, porque a coerção do §3.5 vale em qualquer modo | **Aberto:** a **interface** — a coerção do papel no primeiro cadastro de aluno (política §3.5) e o bloqueio do roster nominal sem contrato de operador (§4). O expurgo da classe H foi pago na 4b, e a classe B tem linha própria. **Há imagem de manuscrito de menor no aparelho** (`filesDir/respostas/`, PNG em cinza, classe H da política §10.8): elimina-a o prazo de 30 dias (na abertura do aplicativo e do escaneamento), a eliminação das que nenhum caderno referencia e o "refazer" do professor; **não** a elimina sair, a revogação do vínculo nem o envio da parcial (decisão do mantenedor de 2026-10-01). Fica fora do backup e da transferência pela regra por domínio, conferida no APK instalado nos dois aparelhos; a conferência por transporte não foi feita. **Sem cifra em repouso**, que é decisão própria. Depois do primeiro dado real de menor a correção envolve dado já coletado: o que era barato antes do primeiro roster foi feito (base legal, modo `coded` como padrão, declaração de retenção — ADR-0012) | mantenedor, para a interface; **jurídico externo**, para preencher os campos em aberto da política antes da publicação |
| **Uso offline não fecha ponta a ponta (§10)** | `4a` `paga` · antes de `/opsx:propose` rodar para a 4b. Condição **satisfeita** em 2026-09-10 (archive de `slice-4a-cache-referencia`); reconciliada como paga em 2026-09-24 | O risco foi observado no aparelho no mesmo dia: arranque, tela de trabalho, lista e câmera num processo nascido em modo avião (`docs/cobertura-fatia-4a-cache-referencia.md:112-124`); **a medição não foi repetida**. A 4b introduz o roster como segundo dado mutável de referência no aparelho, e sem a categoria resolvida antes ela reabriria a mesma decisão sob escopo maior | mantenedor |
| **A classe H não enumera o roster baixado** | `antes-de:piloto-nominal` · **antes de qualquer piloto com turma real em modo `nominal`** | §10.8 da política compreende "pacotes de prova baixados, imagens capturadas e observações pendentes de sincronização" — e **o roster baixado não está na lista**. O prazo dela também não encaixa: "eliminados após a sincronização bem-sucedida" não se aplica a dado que é **puxado**, não empurrado. Depois do primeiro roster nominal num aparelho, enumerar categoria e fixar prazo passa a operar sobre dado pessoal de menor **já copiado para fora do servidor**, e o direito de eliminação deixa de alcançar por um caminho novo. **Em modo `coded` — o padrão de ADR-0012 — o roster no aparelho é código ou apelido, não nome civil:** é isso que mantém a fatia-limite no piloto nominal e não na 4b-1b | **jurídico externo**, levado pelo mantenedor, para a enumeração e para o `[30]` deixar de ser colchete (a linha 12 da política declara os prazos do item 10 como padrões propostos) |
| **O roster cacheado sem regra de apagamento** | `4b` `paga` · fechada em 2026-09-16 pela `slice-4b-roster-no-aparelho` | O roster cacheado é apagado em **três** caminhos — sair, a **revogação** de vínculo e a desinstalação —, e a linha original pedia só os dois de fato do usuário: **faltava a revogação**, que é o servidor dizendo que a organização não é mais dele, e quem foi removido da escola não vai sair do aplicativo para que o apagamento aconteça. Sair e revogação têm cenário próprio e mutações em conjuntos disjuntos. **A desinstalação continua sem teste desta base:** é garantia da plataforma (o Android apaga o `filesDir`), dita como **herdada, e não verificada** (P8). Enquanto o jurídico não fixa o teto, a leitura restritiva vale e nada além dele é assumido | mantenedor |
| **A retenção executável da classe B** | `antes-de:primeira-eliminacao` · **antes do primeiro pedido de eliminação de titular, ou do primeiro vínculo de aluno encerrado há 5 anos** | O resultado passou a persistir na 4b, e **só o expurgo da classe H entrou** (o pendente local é eliminado após a sincronização confirmada). A classe B não entrou: o prazo em forma identificável é "enquanto durar o vínculo e por até 5 anos após", e construir a anonimização agora seria política sem consumidor (P18). **O que encarece depois é o volume:** com um piloto, anonimizar é uma migration; depois de anos de fatos append-only de várias escolas, é retrofit sobre dado de menor acumulado | mantenedor, para o expurgo e a anonimização; **jurídico externo** para o pedido de eliminação alcançar fato já gravado |
| **A política §10.8 diverge do comportamento: sair e a revogação preservam o pendente** | `antes-de:publicacao-da-politica` · **antes da publicação da política** | O item 10.8 tem duas frases que não dizem a mesma coisa. "Eliminados automaticamente após a sincronização bem-sucedida" é o que o código faz. "O encerramento de sessão ou a desinstalação eliminam a base local" **não é**: sair e a revogação **preservam** o resultado pendente (o único exemplar de um trabalho já feito), o caderno que o referencia e — decisão do mantenedor de 2026-10-01 — a **resposta guardada**, a imagem do manuscrito do aluno (classe H, dado **mais sensível** que um número). O que limita é o prazo de 30 dias da resposta, que só roda quando o aplicativo abre (linha própria abaixo), e o `[30]` entre colchetes da política, que para o pendente sem entregador (usuário revogado, aparelho pessoal, nenhum outro membro para enviá-lo) deixa de ser teto de conveniência e passa a ser o **único** limite. **Não é mitigado, é conhecido** (P8). Revisar a política é do mantenedor e do parecer jurídico | **jurídico externo**, levado pelo mantenedor, na mesma linha em que a classe H e o roster baixado já esperam parecer |
| **Modo degradado (§10) não existe** | `6` · reagendada de `5` em 2026-09-26: a 5b-2 não o introduziu. Em dia até a 6 abrir | §10 promete "se offline e ausente, captura e guarda as imagens brutas"; hoje o gate **barra**. Depois da 5 há discursiva no caminho, e o modo degradado passa de "guardar imagem do gabarito" a "guardar imagem de tudo". **O registro dele ainda é disperso:** ADR-0013 diz "são 4b e **4c**", e `4c` não existe; `docs/cobertura-slice-4b-outbox-de-resultado.md` o agrupa com `assessment_fact`, `capture_session` e `sync_cursor` sob "não têm consumidor neste fluxo", o que vale para aqueles e **não** para este, cujo consumidor é a promessa de produto da §10; e `openspec/specs/device-session/spec.md` afirma o oposto como comportamento corrente, no cenário "Pacote ausente e sem rede" ("SHALL levar à recusa explicada, e SHALL NOT levar a escaneamento"), sem marca de provisoriedade. Pela precedência a spec não vence a arquitetura, e o veículo para corrigi-la é uma mudança OpenSpec | mantenedor |
| **Migration não é aplicada por nenhum pipeline** | `antes-de:migration-da-5-em-producao` · **antes de a primeira migration da fatia 5 ir a produção** | Já cobrou: código novo contra schema antigo deu **HTTP 500** na conferência da 4b, com `/health` em 200 o tempo todo (`docs/cobertura-slice-4b-outbox-de-resultado.md` §5.1.1, item 2). A 5 acrescenta schema (rubrica, transcrição), então a próxima migration é certa. Estava só em `docs/deploy-api.md` ("o que este roteiro não cobre"), que é lista de ausências e não ponto de não-retorno | mantenedor |
| **O limiar do OMR foi apurado sobre um aparelho e uma impressora** | `6` · reagendada de `5` em 2026-09-26 (é a fatia do corpus): não houve papel nem aparelho novo. Em dia até a 6 abrir | A obrigação de reexaminar `V` e `C` foi registrada na 3b, venceu na 3c e ficou "continua aberta" sem prazo. §14 regra 5 pede ~30 folhas em ângulos, luz e **letras diferentes**; há 9 fotos de 3 folhas e o eixo "letras" não existe. **2 das 9 não decodificam o QR**, sem causa medida (as hipóteses de resolução e de tamanho de arquivo caíram na 3b). A correção objetiva offline **já é o produto** (§15), e está calibrada sobre uma impressora e um telefone. Vence junto com a acurácia em manuscrito, pela mesma razão | mantenedor |
| **A regra de extração de dados não existe abaixo da API 31** | `antes-de:piloto-nominal` · **antes de qualquer piloto em modo `nominal` num aparelho abaixo de Android 16** | Com `dataExtractionRules` negando os quatro domínios, a transferência cancela o pacote por não haver dado, com o roster e o `outbox.db` intactos no disco (`docs/cobertura-transferencia-entre-aparelhos.md` §10.2 e §10.3). **Sobra a faixa em que o mecanismo não existe:** ele é da API 31 e o `minSdk` é **26**; em Android 8 a 11 o arquivo é ignorado e nada o substitui (`fullBackupContent` governa backup em nuvem, que `allowBackup="false"` já barra). **Medido só em Android 16**; 12 a 15 declaram o mesmo mecanismo e não foram exercitados. Conhecido, **não mitigado** (P8), até ser medido num aparelho de cada extremo. O piloto nominal põe nome civil de menor no roster, e um aparelho antigo na sala é indistinguível de um novo | mantenedor |
| ~~**A afirmação de que a credencial não fica em claro depende da ordem da suíte**~~ **fechado em 2026-09-23** | `antes-de:lancamento` `paga` · **antes do lançamento** | Fechado na `o-apk-de-release-e-verificado` (ETAPA 7.2). A falha foi **reproduzida**, uma vez em dezenove, no aparelho de 19/09 (2511FPC34G, Android 16): `FileNotFoundException … ENOENT` em `SessaoEmRepousoInstrumentedTest.kt:83`, a guarda lendo o arquivo entre um `exists()` e um `readBytes()` enquanto o `SharedPreferences` o regravava. **A causa estava no teste, e não no produto**, e não era a ordem da suíte. Conserto: as leituras de sondagem não fazem `exists()` antes; nenhuma asserção nem espera mudou. A afirmação de segurança foi vista falhar pelo motivo certo, com o token plantado em claro. **O limite que fica:** a corrida não se força, e o fechamento é por construção e pela pilha, e não por contagem. `docs/cobertura-o-apk-de-release-e-verificado.md` §4 e Parte II | mantenedor |
| **`assessment_fact` não existe** | `9` (a fatia que o consome) | **O adiamento é correto, e esta linha não o contesta:** o append-only de I2 está garantido nas duas tabelas de resultado, o insumo (`item_id`, `worth`, `earned`) está gravado em `answer_observation`, e a dimensão por habilidade é derivável por junção com o pacote imutável (auditoria de 2026-09-18, §6). O fato ficou fora da 4b junto com `capture_session` e `sync_cursor`. I2 define o boletim (M3) como `GROUP BY` sobre `assessment_fact`; chegar à 9 sem a tabela seria construir o consumidor sem o fato | mantenedor |
| **O APK de release sai sem assinatura e sem R8, com `versionCode = 1`** | `antes-de:lancamento` · **fatia comercial** (plano de correção, §9) | `apps/android/build.gradle.kts` não tem `buildTypes`, `signingConfig` nem `isMinifyEnabled`, e declara `versionCode = 1` (conferido por leitura em 2026-09-24). A ETAPA 7 do plano de correção proibiu os três de propósito: assinar e mexer em `versionCode` é trabalho de lançamento, e ligar `minifyEnabled` muda o artefato e abre uma frente de verificação inteira. **O token é o evento, e não um número:** "fatia comercial" não é linha do §15, e o que o item bloqueia é a entrega do APK ao professor. Antes do lançamento a frente de verificação do R8 se faz sobre um APK que ninguém recebeu; depois, sobre o que o professor já tem instalado | mantenedor |
| **A região discursiva ainda não passou pelo aparelho nem pelo papel** | `6` · **a sessão única de papel, antes da fatia 6** — ainda não proposta, e por isso sem nome de mudança aqui. Reagendada de `5b` em 2026-09-26, por decisão do mantenedor: o mantenedor não tem impressora, e cada impressão custa um deslocamento; toda conferência em papel passa a ser a etapa final, numa sessão única, com o protocolo escrito antes e a alternativa de cada regra de parada impressa na mesma ida | **Não medida em papel, só no documento renderizado.** **Pago sem papel** (5b-1, 5c-0, 5c-1): o aparelho reconhece a folha discursiva (as regiões saem dos marcadores encontrados, quatro no gabarito e dois na discursiva; o QR é conferido contra os `marker_ids` do mapa); o recorte da `answer_area` e o segundo ajuste do ADR-0018 passam pelo documento renderizado (a moldura cai a ≤ 0,07 mm do mapa) e pela câmera ao vivo — **16 de 16** regiões aceitas (2 folhas × 2 páginas × 4 ângulos fixados antes do resultado), nos dois aparelhos, o que **não** estima a taxa em foto real; o analisador custa de 180 a 260 ms por região reconhecida. **O papel ainda precisa medir:** (1) a extrapolação do canto inferior esquerdo e o teto do resíduo de 1,0 mm (ADR-0018, decisão 4); (2) se `position` do decodificador continua no canto do símbolo com ganho de ponto e tinta real (ADR-0020); (3) que o erro que o resíduo aceita — até 0,89 mm de afastamento com 2 mm de erro em `QR.tr` em `+x` ou em `BR.c4` em `+y` — cabe na folga vertical de 2 mm, e que na horizontal, **sem folga**, o alinhamento se sustenta; (4) o marcador de 11,2 mm e o QR lido com dois ArUcos; (5) a **taxa de recusa do recorte em foto real** — se não for zero, a região fica com problema e o caderno não completa, e esse é o argumento da fatia "finalizar caderno incompleto"; (6) a tela do estado novo, hoje verificada pela árvore de acessibilidade e não por olho. **Custo aceito ao adiar:** a 5b-2 e a 5c vêm antes do papel, e é o retrofit sobre fato append-only — que só se materializa se uma prova com discursiva for a produção antes da sessão, e nenhuma está (lido no banco em 2026-09-24, não relido). O rascunho do protocolo está nas tarefas 4.1 a 4.3 da `slice-5b-1` arquivada. **Não é mitigado, é conhecido** (P8) | mantenedor |
| **Implantar a API da 5a faz o servidor responder HTTP 500 a resultado das provas antigas** | `antes-de:implantar-api-da-5a` · **antes de implantar no Render uma imagem que contenha a `slice-5a-regiao-discursiva`** (a primeira é `sha-a8ccf1a`, publicada em 2026-09-24 e não implantada) | A rota de resultados lê o `content` do pacote gravado com o tipo do domínio (`conferirProveniencia`, `ProvenienciaDoResultado.kt:64`), e a 5a tornou `qr_id` obrigatório na região. Os dois pacotes em produção, `prova-referencia-slice-1` e `prova-referencia-slice-2` (lidos no banco em 2026-09-24), não o têm. **A falha de leitura é medida** (o pacote congelado de antes da 5a cai no parse, tarefa 4.4); **o 500 é por leitura, e não medido:** a rota não trata a exceção, e a KDoc dela decide que "a exceção sobe" porque "um `content` que não parseia é pacote corrompido no banco" — premissa que a 5a desfez. Atinge só um aparelho com build anterior à 5a que ainda tenha resultado pendente dessas provas, e o aparelho trata 500 como falha transitória, então o pendente reenviaria sem fim. **Como se paga:** antes do deploy, conferir que nenhum aparelho tem pendente das duas provas, ou decidir por escrito que esses resultados de conferência são descartáveis; e declarar o evento aqui quando o deploy acontecer. Alternativa: o servidor recusar com motivo, e não 500, se aparecer pendente real | mantenedor |
| **A folha de teste de impressão não aprova a região discursiva que a prova imprime** | `6` · **a mudança que atualiza a folha de teste, aprovada na sessão única de papel antes da fatia 6** — ainda não proposta, e por isso sem nome de mudança aqui. Reagendada de `5b` em 2026-09-26, pelo mesmo motivo da linha da região discursiva: sem impressora, aprovar a folha de teste é papel | A folha de teste existe para reprovar uma impressora antes de ela imprimir uma turma (D-2b.7), e deixou de aprovar o que a prova imprime: **aprova marcador de 14 mm**, e o da região discursiva é de **11,2 mm** (ADR-0018); e **não tem pauta cinza**, que passou a ser linha cinza abaixo do teto decorativo (ADR-0016), justamente o que uma impressora fraca apaga. **A mudança de código da folha pode ser feita a qualquer momento** (o filtro de marcadores por região está na `main` desde a PR #72); só a **aprovação** espera o papel, e a mudança precisa estar feita **antes** da sessão, para ser impressa junto. O que encarece depois: uma escola aprova a impressora pela folha de teste, e a discursiva sai com marcador ou pauta que aquela folha nunca conferiu | mantenedor |
| **O limiar do desvio (5% e 4 mm²) e o teto do resíduo (1,0 mm) foram fixados sem letra de aluno** | `6` · **a sessão única de papel, antes da fatia 6** | Os três números são suposições fixadas antes da primeira execução (ADR-0007), sem nenhuma letra real fotografada; as fronteiras estão pinadas por teste, o que **não** diz que estejam certos. Errar para menos deixa passar resposta que extrapola — corrige-se errado em silêncio, que é o que o §8 manda evitar; errar para mais aponta desvio que não existe, e o professor confere à toa. **Não se afrouxam depois de conhecido o resultado (P11); mudar exige ADR que registre o resultado obtido.** A tela da resposta (5c-1) herdou o sinal: mostra "O aluno escreveu fora da area de resposta. Confira a folha de papel." quando o recorte é sinalizado, e o aviso que o professor lê já depende dos três números. Nenhum foi mudado | mantenedor |
| **A guarda de dívida não lê a tabela "Aberto" do §16** | `5` · **antes de a fatia 6 abrir; uma mudança própria dentro da fatia 5** (item 3 de `docs/plano-da-fatia-5-restante.md`) | `tools/divida/divida.mjs` lê só `### Ponto de não-retorno`. A tabela "Aberto" tem uma linha de fatia-limite `5` — "injeção de prompt manuscrita pelo aluno, no eval set" — que só venceria, sem que nada acusasse, na abertura da fatia 6. A guarda não foi mudada (P19). Duas saídas legítimas, a decidir na mudança própria: a guarda passar a ler as duas tabelas, ou as linhas da "Aberto" migrarem para esta. **A linha de injeção de prompt é reconciliada — paga ou reagendada para `8`, com o motivo — no archive da última mudança da fatia 5** | mantenedor |
| **O teto de 30 dias das respostas só roda quando o aplicativo abre** | `5c` · **a 5c-2 é o veículo**, porque nela nasce o gatilho da nota e o `WorkManager` já está em uso | **Acrescentado em 2026-10-01, na `slice-5c-1-a-resposta-fica-no-aparelho` (P20).** A classe H diz "em qualquer hipótese, em até 30 dias". A varredura roda na abertura do aplicativo (`SessaoActivity`) e na do escaneamento, antes de a câmera abrir. **Aparelho que guarda a resposta e nunca mais abre o aplicativo não expurga.** Conhecido, **não mitigado** (P8): a eliminação é por arquivo e o número das que falharam fica só no log. **Custo de pagar:** um `WorkManager` periódico (o outbox já o usa) e uma trava entre a varredura e o analisador, porque um worker roda com o escaneamento aberto — hoje não há trava, e a varredura contra o analisador é inofensiva por construção, já que eliminar cedo demais só devolve a região a não vista. **O que encarece esperar:** a 5c-2 põe a nota sobre a imagem, e a imagem passa a ter razão de ficar mais tempo no aparelho. O token é `5c` porque a gramática da guarda é `<inteiro>[.<inteiro>][<letra>]`, e `5c-2` seria recusado com `exit 2` | mantenedor |
| Um mantenedor, quatro módulos | `continuo` · contínuo | — | mantenedor |

---

## 17. Registro de decisões

**Aceitas (viram ADR):** D1 offline redefinido · D3 OMR template-driven · D4 nota no servidor, offline definitivo sem discursivas · D5 banco de itens com tiers de visibilidade · D6 QR com identidade e CRC · D7 multimodal-first · D8 pull de referência + push append-only · D9 gate de pré-voo e modo degradado · D10 revisão humana obrigatória · D11 UUIDv7 · D12 Bloom versionado · D13 maior resto estratificado · D14 override do professor vence · D15 provedor único atrás do `AiGateway` · D16 Storage separado com retenção · D17 BNCC obrigatória na geração · D18 sessão por regiões · D19 idempotência por revisão · D20 versionamento de prompt e modelo · D21 Room · D22 remover `OpenScanVision` · D23 QR repetido por região · D24 guarda de versão de renderizador · D25 ledger unificado · D26 `exam_assignment` fixado na publicação · D27 op-log de rascunho · D28 pipeline de imagem · D29 `DICT_5X5_100` com quad único · D30 sem compressão explícita · D31 gabarito no topo, cartão destacável como opção · D32 colunas adaptativas · D33 matemática via SVG · D34 atomicidade e agrupamento · D35 área discursiva declarada pelo professor (ADR-0017) · D36 fonte embarcada · D37 densidade em três níveis · D38 detector de deriva.

> **Decisões vigentes alteradas por ADR posterior, já aplicadas no texto acima:** D35 (ADR-0017), a pauta do §7 (ADR-0016), o modelo de região do §8 (ADR-0018) e a paginação do §7 (ADR-0019). O que cada uma substituiu: [`HISTORICO-v3.md`](HISTORICO-v3.md).

**Rejeitada:** D2 render server-side — substituído por Layout Engine compartilhado + renderizadores client-side.

**Novas nesta versão:**

| # | Decisão |
|---|---|
| **D39** | `membership` N:N — usuário pertence a várias organizações. Entrar numa escola é inserir uma linha, não migrar dados. Toda tabela chaveada por `organization_id`, nunca por `user_id`. |
| **D40** | Assinatura por organização; entitlements como arquivo versionado no Git; quotas e avulsos no mesmo ledger; verificação de direito em um único ponto do domínio. |
| **D41** | Imagens de enunciado guardadas em cor, com versão print-safe em cinza gerada na publicação e aviso de autoria quando a imagem perde significado em P&B. Captura do gabarito é sempre monocromática. |
| **D42** | `TranscriptionProvider` plugável (`NoOp` / `HandwritingOCR` / `FormulaOCR`). Caching de prefixo **antes** de qualquer OCR. Limiar calibrado contra "a nota não muda", nunca contra texto perfeito. Rede de segurança auto-calibrante via overrides. |
| **D43** | TexTeller não entra em v1. Costura pronta; server-side se algum dia; decisão condicionada à medição da fatia 5. |
| **D44** | Dados do aluno impressos, não preenchidos. Folha avulsa com campos em branco para aluno fora da lista. |
| **D45** | Deviants detectados por proporção de tinta fora do quadrilátero e enviados ao mesmo canal de conferência manual do detector de deriva. |

**Novas ao fechar a fatia 1.5 (viram ADR):**

| # | Decisão | ADR |
|---|---|---|
| **D46** | Roster mutável — nome, turma, matrícula — fora do `ExamPackage` imutável e fora do `content_hash`. Sem segundo hash: integridade do impresso já vem do QR. | ADR-0002 |
| **D47** | Identidade de aluno unificada por `student_alias` append-only, resolvido em read model. Nenhum fato é reescrito. | ADR-0003 |
| **D48** | O `LayoutMap` declara o perfil tipográfico que o produziu. Campo antes da parametrização. | ADR-0004 |
| **D49** | Todo item nasce com proveniência e licença; `visibility: public` condicionado à licença. Vira I4. | ADR-0005 |
| **D50** | Artefato imutável nunca contém dado pessoal direto (I5). Finalidade e classe de retenção por tabela ficam como exigência de ADR, não como invariante. | ADR-0006 |
| **D51** | Critério de aprovação de toda medição que decide é registrado antes da primeira execução. | ADR-0007 |
| **D52** | O branco em volta da fórmula em bloco é derivado da transição de texto, assimétrico, e não varia com a altura da fórmula. | D-1.5.9 (fatia 1.5) |
| **D53** | O pacote publicado é armazenado como texto canônico, e não como `jsonb`: o hash é sobre os bytes, e `jsonb` guarda árvore normalizada. | ADR-0008 |
| **D54** | Uma prova publicada tem exatamente um pacote; corrigir prova publicada é publicar prova nova, com `short_id` próprio. O QR impresso não carrega identificador de pacote (§8). | ADR-0009 |
| **D55** | Tinta decorativa dentro de região escaneável tem orçamento declarado no artefato publicado: cobertura ≤ 12% na bolha não respondida, caneta ≥ 50%, corredor de 20% a 40% para o limiar do OMR. Se a medição reprovar, a decoração cede. | ADR-0010 |
| **D56** | Tom e trama são fração de preto em permilagem, declarados no `LayoutMap`; renderizador não escolhe tom. Quem julga cobertura de tinta é o documento rasterizado, e não a validação sem renderizar — o programa de fonte não conhece o contorno do glifo. | D-2b.1, D-2b.3.1 (fatia 2b) |

**Aberto** — cada item com o ponto em que deixa de ser barato. Um item sem essa coluna volta a flutuar, que foi o que aconteceu com a LGPD. **O que já virou linha do ponto de não-retorno (a LGPD, o recorte sem cabeçalho) mora só lá**, e a guarda de dívida ainda não lê esta tabela (linha própria do §16).

| Item | Fatia-limite | O que encarece depois dela | Dono |
|---|---|---|---|
| Unicidade de `skill` por versão do framework curricular | **6** (seed BNCC) | Código BNCC reusado entre versões quebra comparação longitudinal em silêncio; `skill_relation` cobre o mapeamento entre currículos, não a unicidade | mantenedor |
| `subscription.origin` — distinguir empenho de cartão | **8** | Backfill adivinhado sobre assinaturas já existentes. Hoje é uma coluna numa tabela sem linhas em produção | mantenedor |
| Injeção de prompt manuscrita pelo aluno, no eval set | **5** | Validação de schema garante forma, não conteúdo: resposta adversarial produz JSON válido com nota errada | mantenedor |
| Fórmula em bloco seguida de mais enunciado | **1.6+** | Exige o corpo da questão virar sequência de blocos — contrato maior que `InlineBox`, e não sai de graça junto com ele | mantenedor |

Unicidade de `skill` e `subscription.origin` não viraram ADR de propósito: são decisões de um campo, e um ADR por coluna de tabela esvazia o instrumento. Ficam aqui, com data-limite e dono.

**`CaptureGeometry` não é parametrizada por perfil**, e isso é decisão e não pendência: a geometria de bolha está amarrada às tolerâncias de OMR do ADR-0001 e só se mexe com evidência de captura sob outra escala. Um teste afirma que perfil nenhum a altera. (A parametrização de `Sheet` foi entregue na 1.6, como `LayoutProfile`, e o campo de perfil entrou no `LayoutMap` na 2a, ADR-0004.)

---

*Fontes consultadas para a análise do TexTeller (§9.3):*
[TexTeller — repositório](https://github.com/OleehyO/TexTeller) · [TexTeller no Hugging Face](https://huggingface.co/OleehyO/TexTeller)
