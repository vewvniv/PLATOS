# Histórico da arquitetura v3 — emendas, correções de registro e texto substituído

**O que é isto.** O `ARQUITETURA-FINAL-v3.md` descreve o que **vale hoje**. Este arquivo guarda o que o
documento dizia **antes**, e as emendas e correções que o mudaram — cada trecho **literal**, extraído por
intervalo de linhas do documento no commit `6984ae9` (2026-10-01), sem redigitar.

**Por que existe (`rigorous.md` P7, como ela passou a valer em 2026-10-01).** A afirmação errada ou
superada **fica no registro, marcada, com a razão** — e o registro é este arquivo e os ADRs, **não** o corpo
do documento vigente. O corpo ficou com a decisão em vigor; quem precisa saber o que já falhou, ou por que o
valor mudou, vem aqui. Apagar sem guardar continua proibido.

**Como ler.** Cada seção diz de onde o trecho veio e o que o substituiu. Os números de linha são os de
`6984ae9`.

---

## Cabeçalho do documento

_Linha 3 e 6 do original. O status "pronta para virar ADRs e specs" é de 2026-08-13 e deixou de descrever o documento._

**Plataforma de Avaliação Educacional com IA**
**Data:** 2026-08-13 · **Mantenedor:** 1 pessoa · **Status:** alinhada, pronta para virar ADRs e specs

> Este documento substitui a v1, a v2 e o documento de design. É a referência única.

## §5 — correção de registro de 2026-09-18 (ADR-0014, decisão 1)

_Estava no corpo do §5, depois do bloco do `ExamPackage`. A lista passou a trazer `params_hash`; a lição sobre a forma foi para o `rigorous.md`._

> **Correção de registro, 2026-09-18 — ADR-0014, decisão 1.** Esta lista trazia `prompt_version` e
> `model_id` e **não** trazia `params_hash`, enquanto **I3 (§2) sempre exigiu os três**. A
> incompletude não se apaga (P7), porque ela **produziu código**: `PackageMeta` foi escrito com dois
> dos três campos, seguindo esta lista, e a KDoc dele passou a afirmar que a fatia 6 preencheria os
> campos "sem mexer no contrato" — falso, porque o terceiro faltava e acrescentá-lo muda o
> `content_hash` de todo pacote. É o achado 4.1 da
> `docs/auditoria-2026-09-18-antes-da-fatia-5.md`.
>
> **Isto não é substituição de decisão:** I3 nunca mudou. Pela precedência do `rigorous.md` §0, a
> invariante vence a prosa descritiva do mesmo documento, e o que aconteceu aqui é o registro
> descritivo alcançando o normativo. A lição que fica é sobre a forma: **uma lista ilustrativa ao
> lado de uma invariante é lida como se fosse a invariante**, e quem implementa segue a que tem os
> nomes dos campos.

## §7 — a pauta discursiva: o valor e a emenda (ADR-0016)

_Linha 259 era a linha da tabela de geometria do §7; 261–265 era a emenda que a seguia._

| Pauta discursiva | 8,6 mm (generosa: manuscrito espremido é o pior inimigo da leitura) |

> **Emenda de 2026-09-25 — ADR-0016.** A pauta discursiva passa a ser de **7 mm, em cinza claro**,
> do lado decorativo do ADR-0010, como a letra dentro da bolha: ela é guia para o aluno, e não
> geometria para a câmera. O valor anterior e a razão dele ficam escritos acima (P7). A razão é uma
> hipótese sobre leitura que nunca foi medida, e a medição continua sendo a da linha "Acurácia em
> manuscrito" do §16, agora sobre a pauta nova.

## §7 — a paginação: o parágrafo antigo e a emenda (ADR-0019)

_Linha 267 era o parágrafo da paginação; 269–280, a emenda._

**Paginação.** Medir → agrupar em super-blocos indivisíveis (enunciado+alternativas; enunciado+moldura; texto-base+dependentes com penalidade) → **DP minimizando `Σ(sobra)² + penalidades`** → posicionar regiões → emitir. O quadrado da sobra distribui o vazio em vez de empurrá-lo para o fim. Com N ≤ 60 blocos é O(N²), milissegundos. Colunas: **adaptativo** — 2 por padrão, blocos largos atravessam, 1 quando houver muito conteúdo largo.

> **Emenda de 2026-09-25 — ADR-0019.** A DP acima distribui uma sequência **fixa**: ela nunca muda
> a ordem das questões (`Pagination.kt`). A paginação passa a **redistribuir as questões**: não sobra,
> no meio da prova, espaço onde uma questão caberia de maneira **ideal**, que é inteira, com o
> espaçamento normal e sem nada comprimido.
> - Encaixe forçado é proibido.
> - A ordem do professor desempata. A ordem impressa já era por variante (§5, "mapa posição física
>   → `item_id`").
> - A **numeração impressa é a da folha**, e o gabarito, os chips de completude e os relatórios
>   usam esse número.
> - Os super-blocos se movem inteiros, e a ordem sai determinística.
> - "Com N ≤ 60 blocos é O(N²)" deixa de valer como está escrito: escolher a ordem é empacotamento,
>   e a busca é heurística.

## §7 — a área discursiva dimensionada pela rubrica: o parágrafo antigo e a emenda (ADR-0017)

_Linha 282 era o parágrafo; 284–291, a emenda._

**Área discursiva dimensionada pela rubrica:** `expected_lines` da rubrica define a altura da moldura. A IA gera a questão e a rubrica; a rubrica define o espaço; o espaço condiciona a resposta; a resposta é avaliada contra a mesma rubrica. Uma cadeia só, sem decisão manual. Nunca maior que uma página — se a rubrica pede mais, a questão vira itens (a), (b), (c).

> **Emenda de 2026-09-25 — ADR-0017.** A cadeia acima perde um elo: **a rubrica deixa de definir o
> espaço**, e "sem decisão manual" deixa de valer. O professor declara, questão por questão, o
> número de linhas e a largura, que é uma coluna ou a página, **sem valor padrão**.
> - `expected_lines` continua na rubrica, como o que cada critério espera, e a resposta continua
>   avaliada contra ela.
> - A questão de largura de página ocupa uma faixa própria. O fluxo das colunas continua antes e
>   depois dela. É o "blocos largos atravessam" do parágrafo de paginação, acima.
> - O teto "nunca maior que uma página" fica.

## §8 — o modelo de região discursiva: o parágrafo antigo e a emenda (ADR-0018)

_Linha 299 era o parágrafo "Modelo"; 301–311, a emenda._

**Modelo.** Sempre uma região `ANSWER_BLOCK` (gabarito + QR ao lado, dentro de 4 ArUcos). Apenas se houver discursivas, uma região `ESSAY_REGION` por questão, cada uma com 4 ArUcos e um QR compacto.

> **Emenda de 2026-09-25 — ADR-0018.** A região discursiva passa a ter **dois ArUcos na diagonal**:
> `4k` no canto superior esquerdo e `4k+3` no inferior direito. O **QR fica no canto superior
> direito**, na faixa do marcador, e **ancora o terceiro canto**.
> - A alocação `{4k…4k+3}` do parágrafo "IDs de ArUco", abaixo, fica como está.
> - A ordem do pipeline também fica. A primeira homografia sai dos dois ArUcos, o QR é lido na ROI
>   já retificada, e só depois os padrões de posição dele entram num segundo ajuste, que dá o
>   recorte.
> - O canto inferior esquerdo é extrapolado, e a folga do recorte o cobre até a medição em papel.
> - As coordenadas da região discursiva deixam de ser as do "quadrilátero dos 4 ArUcos" do §6, e
>   passam a ser as do retângulo entre os dois marcadores.
> - **O gabarito não muda:** continua com 4 ArUcos.

## §15 — a linha da fatia 4 e a da fatia 5, como estavam

_Linhas 504–505 do original (células com narrativa de 2026-09-10 e 2026-10-01)._

| 4 | Sync · outbox · modo degradado · roster. **Atualizado em 2026-09-10, no archive das duas fatias 4a.** O **gate de pré-voo saiu desta lista**: foi entregue (ADR-0013, `PreparoDaProva.passarPeloGate`; a natureza binária dele vem de ADR-0009) e conferido em aparelho real — a câmera abre só com pacote conferido, e prova sem pacote em modo avião barra com motivo próprio. **Modo degradado continua aqui, e não é o gate:** §10 o define como "se offline e ausente, captura e guarda as imagens brutas para corrigir depois", e hoje esse caso **barra** em vez de capturar — ele depende de fato durável no aparelho, que é o que esta fatia introduz. A condição "não proponha a 4b antes de `slice-4a-cache-referencia` estar arquivada" foi **satisfeita** em 2026-09-10 (`openspec/changes/archive/2026-09-10-slice-4a-cache-referencia`); ela fica escrita porque o ponto de não-retorno do §16 explica por que ela existiu | Modelo offline |
| 5 | Regiões discursivas · completude · deviants · correção manual · **corpus de medição**. **Atualizado em 2026-10-01, depois do archive da `slice-5c-1`:** o que falta desta fatia e a ordem de dependência entre as mudanças estão em `docs/plano-da-fatia-5-restante.md`; a tabela "Ponto de não-retorno" do §16 continua sendo o registro de dívida | D1 sem IA, e os dados para decidir §9 |

## §15 — as "escolhas de ordem" (terceira e quarta) e o que ficou fora da 2a

_Linhas 513–518 do original. A defesa da ordem das fatias 1, 1.6 e 2a/2b._


Uma terceira, acrescentada ao fechar a 1.5: a **fatia 1.6 vem antes da 2**. A 1.5 validou o mecanismo — converter, empacotar como caixa, desenhar igual nos dois renderizadores —, mas a matemática de prova de ensino básico é predominantemente **em linha**, e não em bloco. O gatilho formal é ser bloqueadora da fatia 6: chegar à geração de exatas por IA com a tipografia predominante nunca tendo passado pelo Layout Engine anularia o motivo pelo qual a 1.5 veio antes da 2. A data desejada é mais cedo que isso, e a razão é de contrato: a 1.6 introduz `InlineBox` na medição de texto, e resolver esse tipo antes de a fatia 2 congelar os contratos do `ExamPackage` evita reabrir a medição depois, com o OMR já estabilizado sobre geometria publicada e hasheada.

Uma quarta escolha, acrescentada ao fechar a 1.5: a **fatia 2 foi partida em 2a e 2b**, e o critério do corte é o que a entrega consegue **reprovar**. A 2a entrega o menor pacote publicado de ponta a ponta, e seu critério de aceite são os dois contratos que esse pacote de fato exercita: o **roster mutável separado do pacote imutável** (ADR-0002, I5) e o **perfil tipográfico no cabeçalho do `LayoutMap`** (ADR-0004). Ambos ficam caros depois que houver pacote publicado e hasheado, e ambos falham visivelmente se estiverem errados.

Ficaram deliberadamente **fora** da 2a três itens que uma versão anterior deste plano listava como critério dela: proveniência de item (I4, ADR-0005), identidade de aluno (ADR-0003) e classe de retenção (ADR-0006). Nenhum é exercitado por "publicar uma prova fixa e renderizá-la" — uma prova fixa não tem IA, não produz fatos e não cria tabela de aluno —, então entrariam como declarações que a fatia não pode reprovar. Cada um entra como **commit de contrato antes do consumidor** na fatia que o exercita, e cada um tem fatia-limite e dono em §16. Uma fatia cujo critério de aceite não pode falhar é uma camada horizontal com nome de fatia vertical, e a regra 3 do `CLAUDE.md` existe para impedir isso.

## §16 — as linhas de risco com narrativa: impressão, uso offline, LGPD

_Linhas 528, 529 e 531 do original, literais. O estado vigente delas está no §16 do documento; a história, aqui._

| **Impressão dos ArUcos** | São 4 por questão discursiva, não 4 por prova: muito mais superfície sujeita a toner fraco. Marcador ≥ 12 mm e folha de teste de impressão no onboarding. **Entregue na 2b:** marcador de 14 mm e uma folha de teste que é um `LayoutMap` do mesmo engine, com o critério de aprovação impresso nela, sujeita às mesmas guardas da prova. |
| **Uso offline não fecha ponta a ponta (§10)** | **Atualizado em 2026-09-24, na `registro-de-divida-executavel` (P7: o texto a seguir fica, e é o estado de 2026-09-08).** Fechado pela `slice-4a-cache-referencia`, arquivada em 2026-09-10. Foi medido em aparelho no mesmo dia: a cadeia inteira aconteceu num processo nascido em modo avião, até a câmera abrir (`docs/cobertura-fatia-4a-cache-referencia.md:112-124`). A linha deste risco no ponto de não-retorno, abaixo, foi reconciliada como paga. **Aberto, descoberto em 2026-09-08 na conferência em aparelho da fatia 4a.** O pacote é puxado, conferido e guardado corretamente, e o cache do aparelho funciona — mas o aplicativo **não abre sem rede depois de o processo morrer**: com credencial guardada, `DeviceSession.abrir` vai a `Consultando` e reconsulta `/me/organizations` incondicionalmente (decisão 10 da fatia 4a-zero), e sem rede o arranque para antes de existir qualquer listagem de provas. Os testes de JVM não pegam isto porque alimentam a máquina de estados direto, e a spec não é violada nos próprios termos: "consulta sem rede diz que está sem rede" e "segunda vez sem rede usa o pacote guardado" convivem, e o segundo pressupõe uma escolha que exige listagem. **Isto é risco de entrega da §10**, e não só cobertura de uma fatia: a promessa de captura offline é do produto. Ver o item de acompanhamento no `design.md` da 4a. **Endereçado por `slice-4a-cache-referencia`, proposta em 2026-09-09**, que acrescentou ao diagnóstico a **segunda parede**: mesmo com a organização resolvida, a listagem de provas vem da API e nada a guarda — resolver só o arranque não fecha a §10. O que falta ao aparelho é a categoria **referência mutável** (nome da organização, título da prova e, na 4b, o roster), que ADR-0002 mandou viver fora do pacote imutável sem dizer onde ela mora no dispositivo. |

| **LGPD com dados de menores** | **Encaminhado em 2026-08-27, e não fechado.** ADR-0012 e `docs/legal/politica-de-privacidade.md` fixam base legal por faixa etária, o controlador em cada modalidade — a ambiguidade do *self-serve* estava aqui e o §3.3 da política a resolve —, oito classes de retenção e o conteúdo do contrato de operador. O modo sem identificação nominal deixou de ser mitigação teórica: é o **padrão** de toda organização, e o roster recusa matrícula fora do modo nominal. Toda tabela declara finalidade e classe de retenção, com guarda de construção derivada do catálogo. **Falta**, e continua sendo risco: a coerção do papel na interface e o bloqueio de roster nominal sem contrato de operador registrado (política §3.5 e §4), que vão com a tela de cadastro de aluno; e a retenção executável — expurgo, anonimização da classe B, pedido de eliminação —, que vai com a **fatia 4b**, onde o resultado passa a persistir. **Atualizado em 2026-09-10, no archive das duas fatias 4a:** o **cache no dispositivo** saiu desta lista porque foi entregue — `slice-4a-package-pull` fez o logout apagar o diretório de pacotes da organização, e `slice-4a-cache-referencia` acrescentou a visão guardada à mesma lista e fez a **revogação observada** apagar as duas coisas, conferido em aparelho (`find files -type f` restou só o marcador de perfil). E a atribuição dos três restantes deixou de ser "fatia 4" porque a fatia foi cortada: eles vencem onde resultado persiste, que é a 4b. Atualização de registro com informação nova, não substituição de decisão — não abre ADR. |

## §16 — o texto explicativo do formato da coluna "Fatia-limite"

_Linhas 540–558 do original. A parte que a guarda `tools/divida/divida.mjs` exige continua no documento; a prosa de origem, aqui._

**O formato da coluna "Fatia-limite", desde 2026-09-24** (ETAPA 8 do plano de correção; `rigorous.md`
P27). Cada célula começa com um **token entre crases**, e só esse token é lido pela guarda
`tools/divida/divida.mjs`. A prosa que vem depois é para o leitor.

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

Isto é atualização de registro, porque muda o formato de uma coluna que já existia, e não
substituição de decisão. Não abre ADR, pelo mesmo critério das atualizações de 2026-09-10.

**Eventos que já ocorreram:** nenhum.

## §17 — as emendas de 2026-09-25

_Linhas 593–600 do original._

> **Emendas de 2026-09-25:**
> - **D35, pela ADR-0017:** o espaço da discursiva, com linhas e largura, é declarado pelo
>   professor, e não pela rubrica.
> - **Pauta do §7, pela ADR-0016:** 7 mm, em cinza claro.
> - **Modelo de região do §8, pela ADR-0018:** a região discursiva tem dois ArUcos, e o QR ancora o
>   terceiro canto.
> - **Paginação do §7, pela ADR-0019:** as questões são redistribuídas para não deixar branco, e a
>   numeração impressa é a da folha.

## §17 — a tabela "Aberto", linhas e notas finais

_Linhas 632–644 do original, literais. As linhas que duplicavam o §16 saíram do documento; as demais ficaram._

**Aberto** — cada item com o ponto em que deixa de ser barato. Um item sem essa coluna volta a flutuar, que foi o que aconteceu com a LGPD.

| Item | Fatia-limite | O que encarece depois dela | Dono |
|---|---|---|---|
| Base legal e política de retenção sob LGPD | **3** | Dado real de menor já coletado; sem escola controladora no Basic (§16) | mantenedor + jurídico externo |
| Unicidade de `skill` por versão do framework curricular | **6** (seed BNCC) | Código BNCC reusado entre versões quebra comparação longitudinal em silêncio; `skill_relation` cobre o mapeamento entre currículos, não a unicidade | mantenedor |
| `subscription.origin` — distinguir empenho de cartão | **8** | Backfill adivinhado sobre assinaturas já existentes. Hoje é uma coluna numa tabela sem linhas em produção | mantenedor |
| Garantia executável de que o recorte discursivo não contém cabeçalho | **5** · **paga em 2026-09-30, na `slice-5c-0-o-recorte-da-resposta`** — camada 1: a validação do `LayoutMap` recusa texto, imagem, QR, marcador, círculo ou retângulo preenchido dentro da área de resposta (`AreaDeRespostaLimpaTest`); camada 2: o recorte sobre o documento renderizado tem **zero** de tinta com o nome do aluno impresso em volta (`RecorteSemCabecalhoInstrumentedTest`). **Limite declarado:** a camada 1 não vê texto que começa fora da largura da região e a invade; só a camada 2 o vê, e sobre o documento renderizado, não em papel. Texto anterior da linha: | A moldura já exclui o enunciado por geometria (§8), mas nada afirma isso; o nome do aluno é impresso na folha | mantenedor |
| Injeção de prompt manuscrita pelo aluno, no eval set | **5** | Validação de schema garante forma, não conteúdo: resposta adversarial produz JSON válido com nota errada | mantenedor |
| Fórmula em bloco seguida de mais enunciado | **1.6+** | Exige o corpo da questão virar sequência de blocos — contrato maior que `InlineBox`, e não sai de graça junto com ele | mantenedor |
Três itens desta lista não viraram ADR de propósito — unicidade de `skill`, `subscription.origin` e a garantia de recorte. São decisões de um campo ou de um cenário de spec, e um ADR por coluna de tabela esvazia o instrumento. Ficam aqui, com data-limite e dono, que é o que faltava.

**Saiu da lista ao fechar a 2a.** A parametrização de `Sheet` foi entregue na 1.6, como `LayoutProfile`, e o campo de perfil entrou no `LayoutMap` na 2a (ADR-0004) — as duas metades cumpridas dentro do prazo que a lista registrava. `CaptureGeometry` **não** foi parametrizada, e isso é decisão e não pendência: geometria de bolha está amarrada às tolerâncias de OMR do ADR-0001 e só se mexe com evidência de captura sob outra escala. Um teste afirma que perfil nenhum a altera.

