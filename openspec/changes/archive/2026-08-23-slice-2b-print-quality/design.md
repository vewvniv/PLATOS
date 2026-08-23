## Context

Ver `proposal.md — Why`. O que as fatias anteriores deixaram pronto, e o que elas deixaram sem guarda, define quase todo o desenho desta:

- O `LayoutMap` já é determinístico, versionado, embutido no pacote publicado e comparado byte a byte em três alvos. A folha desenhada já vem do pacote (D-2a.5).
- `DrawRect` já tem `fill` no contrato, em **porcentagem inteira de preto**. Nenhum mapa emite `fill` hoje, então o campo nunca foi exercitado.
- **O renderizador Android ignora `fill`.** Ele desenha `DrawRect` só com traço; o web desenha com preenchimento. Uma faixa emitida hoje sairia em um documento e não no outro, e **a paridade não acusaria** — ela compara centroides de marcador e bolha, e uma faixa ausente não move nenhum deles.
- `compare.mjs` mede centroide por **peso de escuridão**, contando todo pixel com escuridão acima de 8 em 255. Uma trama de 4,5% tem escuridão ≈ 11: ela entraria na conta e puxaria o centroide da bolha. `fidelidade.mjs` usa corte em 128, que a trama não alcança, mas a letra cinza dentro do círculo pode alcançar.
- As duas ferramentas rasterizam em `DeviceGray`. Uma guarda de monocromia medida sobre raster cinza **não pode reprovar**: a cor foi descartada antes de ela olhar.
- `docs/protocolo-medicao-impressa.md` já tem o portão de escala (vão de 180 mm) e a inspeção de marcador, mas rodando sobre a prova de referência.

O que **não** existe: qualquer tinta que não seja preto pleno na folha, qualquer cabeçalho ou rodapé, e qualquer documento que exista para reprovar uma impressora.

## Goals / Non-Goals

**Goals:**

- A folha completa de §7 na parte que não pertence à fatia 7, com as três mitigações de erro de transcrição.
- Um orçamento de tinta decorativa declarado no artefato publicado, e verificado sobre o documento por caminho independente de quem o produziu.
- Uma folha que reprova uma impressora antes da primeira turma.
- Nenhuma verificação nova que não possa dar resultado negativo.

**Non-Goals:**

- Ajustar as ferramentas de raster para "ignorar" a decoração por lista de exceções. A separação é por faixa de escuridão, e não por identificador.
- Fixar o limiar do OMR. Esta fatia declara o corredor onde ele poderá cair; escolhê-lo é fatia 3.
- Onde a folha de teste aparece para o professor. Não há UI nesta base.

## Decisions

### D-2b.1 — Tom e trama em permilagem de preto, no contrato, antes de qualquer consumidor

`DrawRect.fill` passa de porcentagem inteira (0–100) para permilagem (0–1000), e `DrawText` ganha um campo de tom na mesma escala. Ausência de tom continua significando preto pleno.

§7 pede trama de **4,5%**. Em porcentagem inteira ela não existe: publicar 4% ou 5% seria congelar num artefato hasheado um valor que ninguém escolheu, e a diferença é visível — 4% e 5% de preto são tramas distintas a olho e sob a lente do OMR. Permilagem inteira mantém a aritmética inteira que D-1.2 exige e representa 45‰ exatamente.

*Alternativa descartada:* fração de ponto flutuante. Reintroduz na serialização canônica o tipo cujo arredondamento entre alvos o resto do mapa evita — e o mapa é hasheado.

*Alternativa descartada:* manter porcentagem e arredondar. É a decisão silenciosa acima, tomada por omissão.

Esta é a primeira mudança da fatia porque `fill` já existe no contrato e nos dois lados: mudar a escala depois de haver faixa desenhada seria mudar o significado de um número já publicado.

### D-2b.2 — O orçamento de tinta viaja no `LayoutMap`, não numa constante do app

O `LayoutMap` declara, no cabeçalho da região escaneável, o orçamento de tinta decorativa e a margem de contraste exigida.

Quem consome esse número é o OMR da fatia 3, que roda **offline**, contra um pacote imutável que pode ter sido produzido por uma versão anterior do engine. Uma constante no app concordaria com a folha por coincidência de versão, e discordaria em silêncio no dia em que uma turma imprimisse com pacote antigo — que é exatamente o cenário que o modelo de pull de referência imutável cria.

*Alternativa descartada:* constante compartilhada no KMP. Some no momento em que folha impressa e app deixam de andar juntos, que é o modelo offline inteiro.

### D-2b.3 — A fatia declara um corredor, e não o limiar do OMR

A grandeza é **cobertura**: a média de escuridão dentro do disco da bolha, normalizada, com 0 = papel e 1 = preto pleno.

Valores propostos, a serem fixados pelo ADR antes da primeira medição, no espírito de ADR-0007:

| Grandeza | Valor | Papel |
|---|---|---|
| Orçamento de tinta decorativa numa bolha não respondida | ≤ 12% | Teto que a folha não pode passar |
| Cobertura de uma bolha preenchida a caneta | ≥ 50% | Piso observado no papel, medido na impressão |
| Corredor admissível para o limiar do OMR | 20% a 40% | O que a fatia 3 herda |
| O que acontece se reprovar | A decoração cede | A folha é cosmética; a leitura não é |

O limiar do OMR não pode ser escolhido aqui porque não há OMR nem folha fotografada. O que **pode** ser garantido aqui é que exista um corredor largo entre decoração e caneta — e é isso que a decoração não tem direito de estreitar. Fixar um limiar agora seria o erro que ADR-0007 descreve: um número escolhido antes da medição que existe para justificá-la.

*Alternativa descartada:* nenhuma decoração dentro da bolha, e a letra fora do círculo. Elimina o risco e também a mitigação: §7 põe a letra dentro do círculo porque é lá que ela reduz o salto de linha, que é o erro que custa nota ao aluno.

### D-2b.3.1 — Quem julga tinta é o raster; o mapa prova só o que consegue provar

*Acrescentada durante a implementação, quando a medição mostrou que o requisito original não era implementável de forma sólida e útil ao mesmo tempo.*

A validação sem renderizar recusa o que consegue **provar**: tinta chapada dentro de uma bolha acima do orçamento — que é exato, porque uma trama que cobre o disco contribui exatamente com o próprio valor — e elemento decorativo dentro de uma bolha em preto pleno ou acima do teto de tom declarado. A cobertura efetiva continua verificada sobre o documento rasterizado.

O motivo é estrutural: `FontProgram` lê `cmap`, `hmtx` e `kern` — avanço e espaçamento —, e **não o contorno do glifo**. O único limite superior que a validação consegue provar para uma letra é "a caixa inteira é tinta", e medido na folha de referência esse limite dá **20,4%** para bolhas cuja tinta real é **7,24%**. Um validador honesto com esse limite recusaria a folha que o raster aprova.

*Alternativa descartada:* baixar o tom da letra até o limite superior caber no orçamento — de 400‰ para ~188‰. A letra ficaria 81% branca, e quem teria decidido isso é a frouxidão do limite, não o olho nem a impressora.

*Alternativa descartada:* ler os contornos da tabela `glyf` para calcular a área real de tinta. Resolve de verdade e mexe no medidor de texto — o keystone da fatia 1 — por um motivo que não é medição de texto.

*Alternativa descartada:* subir o orçamento até o limite caber. É mover o critério depois de conhecer o número, que é o que ADR-0007 existe para impedir; e 25% invadiria o corredor de 20% a 40% onde o limiar do OMR precisa caber.

### D-2b.4 — Decoração e traço ocupam faixas de escuridão disjuntas, e cada medição olha só a sua

A paridade sobe o piso de escuridão do centroide para acima de toda tinta decorativa; o orçamento mede a cobertura decorativa com teto abaixo do traço. Duas medições, duas faixas, sem interseção.

Sem isso, acrescentar decoração corromperia a medição que existe desde a fatia 1: com o piso atual de 8 em 255, uma trama de 4,5% (≈ 11) entra na conta e desloca o centroide da bolha — e o deslocamento seria interpretado como divergência entre renderizadores, ou pior, mascararia uma. É o caso literal de "janela de medição que alcança o vizinho" do `CLAUDE.md`, com o vizinho dentro do próprio elemento.

Consequência que vale declarar: **o piso da paridade é a definição operante de "decorativo"**. Tinta que a paridade enxerga não é decoração, é geometria.

### D-2b.5 — O Android passa a preencher, e a paridade passa a comparar trama

`DrawRect` no Android desenha o preenchimento declarado, e `compare.mjs` ganha uma comparação de **cobertura** de cada retângulo preenchido, além dos centroides.

Sem a segunda parte, a primeira não tem guarda: a faixa poderia sumir de um dos lados, ou sair em 20% num e 4,5% noutro, e as 185 comparações de centroide continuariam verdes. Foi assim que a ausência de `fill` no Android sobreviveu até hoje — o campo existia no contrato e nada media se ele era honrado.

### D-2b.6 — A monocromia é verificada sobre raster RGB, e é a única medição desta fatia que muda de espaço de cor

A guarda de monocromia rasteriza em RGB e afirma `R = G = B` em todo pixel. As demais continuam em `DeviceGray`.

Uma guarda de cor sobre raster cinza é decorativa no pior sentido: ela passa sempre, porque o rasterizador jogou fora a informação que ela deveria julgar. É o mesmo erro que a fatia 2a encontrou ao tentar derrubar a guarda de bytes com uma troca de tipo — a verificação precisa poder ficar vermelha pelo motivo certo, e não por outro.

*Como isto será visto falhar:* desenhando um elemento em cor de propósito e confirmando que a guarda acusa, e que as guardas em cinza **não** acusam.

### D-2b.7 — A folha de teste é um `LayoutMap` como qualquer outro

Um construtor no KMP produz o `LayoutMap` da folha de teste, com as mesmas primitivas e a mesma `CaptureGeometry` da prova. Os dois renderizadores a desenham sem saber que ela é especial, e ela entra na paridade e na fidelidade junto com a prova.

O vão de referência é o mesmo que o protocolo já usa como portão de escala — o vão entre marcadores —, com o comprimento esperado impresso ao lado dele. Assim quem confere não precisa do documento aberto ao lado da folha: o critério está na folha, que é onde ele é útil.

*Alternativa descartada:* uma folha desenhada à mão em HTML ou PDF fixo. Aprovaria a impressora por um caminho que a prova não percorre — e as duas fatias anteriores mostraram que o defeito mora exatamente no caminho, não na intenção.

### D-2b.8 — O cabeçalho entra acima da região, e a região continua no topo da página 1

Título e instrução de preenchimento ocupam uma faixa acima da região de gabarito; a região desce o que essa faixa medir, e continua sendo a primeira coisa da página 1. O rodapé vive dentro da margem inferior, que hoje está vazia.

§7 põe a folha de respostas no topo pelo fluxo de escaneamento — uma pilha objetiva é escaneada sem folhear —, e nada disso depende de a região encostar na margem. O que depende é ela estar na página 1 e caber inteira no enquadramento, e o teto de 100 mm de altura da região preserva os dois com folga.

### D-2b.9 — Um golden por causa, e a ordem é de contrato para consumidor

Ordem: escala de tinta no contrato → renderizadores honrando tom e trama → cabeçalho e rodapé → decoração do gabarito → orçamento declarado e validado → folha de teste → guardas de raster.

O golden e o pacote da fixture são regravados **uma vez por causa**, e cada regravação registra o que mudou e prova que nada além mudou — a auditoria que a 2a fez duas vezes (2b.3 e 6.5 de lá) e que existe para separar "mudou o que está escrito" de "mudou onde está escrito".

## Risks / Trade-offs

**A decoração corrompe as medições que já existem** → D-2b.4 separa por faixa de escuridão em vez de por exceção. Antes de confiar na separação, cada ferramenta é vista falhando com a decoração no lugar: um deslocamento deliberado ainda tem de ser acusado pela paridade **com** faixa e letra na folha.

**A faixa some num renderizador e ninguém vê** → é o que acontece hoje. D-2b.5 põe a cobertura sob medição; a guarda é vista falhando removendo o preenchimento de um dos lados.

**O orçamento é declarado antes de existir OMR** → por isso ele é um corredor com margem, e não um limiar; e por isso o ADR registra o que cede se a fatia 3 medir fora dele. Se a caneta real cobrir menos que o piso proposto, a decoração é que sai — ela é cosmética, a leitura não é.

**O cabeçalho empurra o conteúdo e a prova ganha página** → a paginação é determinística e o contador de páginas já existe; a regravação do golden mostra o efeito. Se a prova de referência passar de página, isso é resultado a registrar, não defeito a esconder.

**Julgamento tipográfico não tem oracle dentro do sistema** → duas fatias seguidas tiveram defeito de espaçamento achado só no papel. A trama de 4,5% e o tom da letra são a mesma classe de decisão: quanto cinza é "cinza" depende do toner. Por isso a impressão entra como tarefa, com ajuste dentro do orçamento permitido, e o resultado vai para o protocolo.

**A folha de teste dá falsa confiança** → uma impressora aprovada nela imprime marcador legível; ela não promete que o OMR vai ler a folha fotografada, que depende de câmera, luz e enquadramento da fatia 3. O texto impresso na folha diz o que ela aprova, e o protocolo repete.

## Migration Plan

Aditivo no comportamento, **quebra de significado num campo**: `DrawRect.fill` muda de escala. Nenhum mapa emite `fill` hoje e o golden não o contém, então a mudança não invalida artefato existente — mas ela vai num commit próprio, antes de qualquer consumidor, porque depois da primeira faixa desenhada o mesmo número passaria a significar duas coisas.

O golden e o pacote da fixture mudam de hash. Isso é esperado e é o que a 2a registrou como caminho normal: corrigir uma prova publicada é publicar um pacote novo. Não há dado de produção, não há migration de banco e reverter é `git revert`.

## Open Questions

Nenhuma. As duas que existiam foram fechadas antes de a proposta ser escrita: o que entra da folha completa (fatia 7 fica com dados do aluno) e a forma da folha de teste (documento próprio, mesmo caminho de renderização).
