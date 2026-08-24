## Context

Ver `proposal.md — Why`. O que importa aqui é o estado do código.

`apps/android` hoje é **só um renderizador**: `render/LayoutMapRenderer.kt` e `render/RendererContract.kt`, com nove testes de host na JVM e um instrumentado no emulador. Não há câmera, OpenCV, ZXing nem Room. O `androidTest` já monta `fixtures/` como assets, então uma imagem versionada ali chega ao emulador sem encanamento novo.

`packages/domain/.../capture/` já existe e contém `CaptureGeometry`, `ArucoDictionary` e `QrEncoder` — os **produtores** da geometria de captura. Nenhum deles toca imagem. Esta fatia acrescenta o primeiro leitor.

Três restrições registradas moldam tudo abaixo, e nenhuma é escolha desta fatia: §13 aloca `OpenCV (ArUco + homografia)` ao Android; §8 fixa a ordem `ArUcos → região → homografia → QR na ROI retificada → OMR`; ADR-0010 define a grandeza como cobertura média de escuridão no disco, com 0 = papel.

Existe uma implementação de referência: `tools/parity/papel.mjs`, escrita para a tarefa 8.3 da 2b. Ela faz o pipeline menos o QR, em JavaScript, e produziu números sobre papel real — 160 bolhas, caneta de 51,61% a 72,20%, vazias até 9,20%.

## Goals / Non-Goals

**Goals**

- Manter o domínio sem qualquer conhecimento de pixel.
- Pôr a fronteira de teste onde ela rende mais: tudo que produz número roda sem emulador.
- Ter oracle para cada afirmação numérica, e pelo menos um que não compartilhe desenho com o medidor.
- Preservar `papel.mjs` como implementação independente, e não convertê-lo em código oficial.

**Non-Goals**

- Não otimizar desempenho. A fatia mede correção; velocidade de quadro é problema da fatia com câmera.
- Não generalizar para região discursiva, mesmo que a geometria admita — §8 prevê `ESSAY_REGION`, e nada aqui deve antecipá-la.
- Não criar módulo KMP novo. Ver a decisão 1.

## Decisions

### 1. A aritmética do OMR fica em `apps/android`, não no KMP

Fronteira: o adaptador **acha**, o núcleo puro **calcula**.

```
apps/android
  vision/   OpenCV: ArUco → 4 cantos, homografia, retificação   ← emulador
            ZXing-C++: QR sobre a ROI retificada → payload      ← emulador
  omr/      projeção das bolhas, amostragem, cobertura          ← teste de host (JVM)
                    │ produz OmrMeasurement
                    ▼
packages/domain
  capture/  CaptureGeometry, ArucoDictionary, codec do QR, OmrMeasurement
  layout/   LayoutMap — a fonte geométrica
```

`android → domain`, nunca o contrário.

*Alternativa descartada — `GrayImage` dentro de `packages/domain`.* Contradiz §13, que aloca a homografia ao Android, e §12, que põe a captura em M2 e não em `SHARED`. E dá ao módulo que representa o domínio do exame conhecimento de raster, que é conceito de outra camada.

*Alternativa descartada — `packages/omr`, módulo KMP novo.* Conceitualmente melhor que a anterior, mas contradiz §14, que declara `packages/ domain/ contracts/`. O único benefício concreto seria testar em três alvos — e esse benefício já existe sem módulo novo, porque teste de host do Android roda na JVM: `RendererContractTest` faz exatamente isso hoje, com nove testes e sem emulador. Criar módulo agora custa build, três alvos, `embedFixtures`, CI e dependências em dois apps, para comprar algo que já se tem.

*Quando essa decisão deve ser revista:* quando aparecer um segundo consumidor do OMR fora do Android. O candidato plausível é o professor subir digitalização pela web — `papel.mjs` já mostra que digitalização de mesa lê melhor que foto. Não está em §15, e extrair depois é refatoração mecânica num monorepo sem artefato publicado: mover arquivos e ajustar imports, não retrofit. O custo de esperar é baixo; o de antecipar, não.

### 2. Retificar e depois medir, e não medir a elipse na imagem original

§8 manda retificar. Além de ser a leitura registrada, é a definição correta da grandeza: cobertura é área de tinta sobre área da bolha **no papel**. Retificar leva a imagem para o espaço do papel, e amostrar um círculo lá mede exatamente isso.

*Alternativa descartada — amostrar a elipse projetada na imagem original.* Evita a reamostragem, o que é atraente porque cobertura é medição de valor de pixel. Mas pesa cada pixel pela densidade em espaço de imagem: sob perspectiva o lado próximo da câmera fica super-representado, e isso é **viés**, não ruído. Corrigir exigiria ponderar pelo jacobiano — mais matemática para chegar onde a retificação chega de graça.

A objeção à reamostragem continua válida e se mitiga no adaptador: retificar por média de área, não por bilinear pontual, para que a tinta de uma bolha de 4 mm não seja borrada pela vizinhança.

**Consequência a registrar:** `papel.mjs` mede na imagem **não** retificada. Numa digitalização de mesa a perspectiva é quase nula e os dois devem concordar de perto, mas a comparação deixa de ser igualdade e passa a ter tolerância declarada. Melhor saber disso ao escrever o teste do que descobrir depois com 0,3 ponto de divergência inexplicada.

### 3. A grandeza é a de ADR-0010, literal, e esta fatia não a redefine

Cobertura = média de escuridão no disco, normalizada contra o **branco local do papel**, com o raio igual ao círculo desenhado menos o traço. É o que `tinta.mjs` faz sobre o PDF e `papel.mjs` faz sobre o papel, e é o que o mapa declara.

O papel expôs uma ambiguidade em "1 = preto pleno": na digitalização da 2b o miolo preto de um ArUco lê 83 de 255, então toner pleno rende no máximo 64% de cobertura. Uma câmera comprime diferente, e comprime por foto.

Normalizar também contra o preto do marcador — que está presente em toda captura, ao lado da região medida — provavelmente é o certo. **Esta fatia não faz isso**, e a razão é ADR-0007: mudar a grandeza medida é decisão que precisa estar registrada antes da primeira execução do corpus, e o corpus é da fatia seguinte. Redefinir agora, sem dado, seria escolher a definição pelo resultado que se espera dela.

O que esta fatia faz é deixar a normalização isolada num ponto só do código, para que a fatia do corpus consiga trocá-la sem reescrever a medição.

**A cobertura atravessa a fronteira em permilagem inteira, e não em `Double`.** Isto corrige o que
este documento dizia antes: eu supunha que `OmrMeasurement` carregaria uma fração de ponto
flutuante, e que a exceção a D-1.2 precisaria ser registrada. Não precisa, porque não há exceção.

Quem apontou foi `IntegerArithmeticGuardTest`, que varre **todo** o `commonMain` — não só
`layout/` — e recusou o `Double` na primeira compilação. A guarda estava certa e a solução é
melhor que a exceção que eu ia pedir:

- Um passo de permilagem é 0,1 ponto percentual. A distância entre caneta e bolha vazia medida no
  papel da 2b foi de **424 pontos**. Não se perde nada.
- O `ink_budget` da região já viaja em permilagem inteira: `decorative_max`, `threshold_floor`,
  `threshold_ceiling`. Medição e orçamento na mesma escala é comparação sem conversão — e
  conversão entre fração, porcentagem e permilagem na fronteira de um limiar é onde mora o erro de
  um passo.
- A medição continua sendo calculada em `Double` dentro de `apps/android`, que não é varrido pela
  guarda. O arredondamento acontece uma vez, na fronteira.

Uma guarda com zero exceções é muito mais forte que uma com uma carve-out, porque carve-out
cresce. Esta continua com zero.

### 4. O codec do payload do QR vira contrato antes de existir leitor

Hoje `LayoutEngine.qrPayloadOf` e `PrintTestSheet.qrPayload` constroem o payload de §8 e **cada um tem sua própria cópia de `crc16`**. Esta fatia acrescenta o terceiro consumidor, e é o primeiro que lê.

Um leitor que divirja dos escritores não quebra teste: ele atribui a folha errada, em silêncio, no aparelho do professor. Unificar em `capture/` é a regra 1 — contrato antes do consumidor — e a necessidade está comprovada por três consumidores, não presumida.

A unificação SHALL preservar o payload byte a byte. A prova é direta: nenhum golden muda, e o hash do pacote continua `26612ad5…`. Se algum mudar, a movimentação não foi movimentação.

### 5. Três camadas de fixture, com oracles de força diferente

| Fixture | Oracle | Pega o quê |
|---|---|---|
| Buffer sintético, disco preenchido a fração conhecida por construção | **Analítico** — a resposta vem de quem desenhou, sem compartilhar código com quem mede | Rasterização do disco, off-by-one na borda, erro de normalização |
| O mesmo buffer, distorcido em perspectiva e retificado de volta | Analítico — a fração não muda | Homografia, retificação, perda por reamostragem |
| Recorte em cinza cru da digitalização da 2b | `papel.mjs`, dentro de tolerância | Erro de porte e divergência numérica sobre papel real |
| Digitalização completa, no emulador | Detecção por dois caminhos independentes | Ver abaixo |

A independência da última é real e vale nomear: `papel.mjs` acha os marcadores por componentes conexos sobre limiar; OpenCV acha por contorno e casamento de dicionário. São detectores que não se conhecem. Se os quatro cantos baterem, a metade de detecção está provada duas vezes.

A independência da terceira é **parcial**, e não vou fingir o contrário: as duas implementações não compartilham código, mas compartilham desenho. Ela pega erro de porte; não pega erro de concepção cometido nas duas.

*Formato da fixture:* recorte em cinza cru para a camada de medição — dispensa decodificador, é idêntico nos três alvos e não depende de biblioteca de imagem. JPEG completo para a camada de detecção, que precisa dos quatro marcadores e roda no emulador, onde há decodificador.

### 6. As duas digitalizações entram versionadas

Não é conveniência de teste. A folha física não existe mais, e sem elas `docs/cobertura-fatia-2b.md` afirma 51,61% sem nada que permita reproduzir o número. São ~815 KB, sem dado pessoal — prova de referência, sem aluno —, então I5 não é tocado.

## Risks / Trade-offs

**A retificação altera o pixel que a medição julga** → retificar por média de área e provar no buffer sintético: a fração conhecida tem de sobreviver ao caminho distorce-e-retifica dentro de tolerância declarada.

**A camada pura fica fina** — projeção afim e média em disco, sem homografia, porque ela ficou no OpenCV → aceito, e é o efeito pretendido de §13. O que sobrou na camada pura é exatamente o que produz número; posicionamento é verificado por erro de reprojeção e pela redundância ArUco↔`region_idx`.

**A detecção só se verifica no emulador**, que é o alvo mais lento e mais frágil do CI → a fatia 1 já convive com isso na paridade, e o job já existe. Nada aqui aumenta o número de execuções de emulador por CI.

**Duas bibliotecas nativas novas no Android** (OpenCV, ZXing-C++) → ambas declaradas em §13, então não são tecnologia nova. O risco é de tamanho de APK e de build, não de arquitetura, e aparece na primeira execução do job.

**Medir sem classificar pode parecer entrega incompleta** → é o corte deliberado, e ele cai onde ADR-0007 desenha a linha. O consumidor da fatia é a fatia do corpus, exatamente como `tinta.mjs` foi o instrumento que deu à 2b o direito de afirmar 7,24%.

**O corredor de 20% a 40% pode não sobreviver à câmera** → não é risco desta fatia, é o que a próxima existe para medir. ADR-0010 já declarou o que cede se isso acontecer: a decoração, na ordem tom da letra, trama da faixa, letra fora do círculo.

## Open Questions

- **Tolerância entre a medição oficial e `papel.mjs` sobre o mesmo recorte.** O número sai da primeira execução comparando as duas, e não muda spec, abordagem nem tarefas — muda só a constante do teste. Fixá-lo antes de medir seria inventá-lo.
- **Tamanho do recorte em cinza cru.** Algumas questões bastam para a comparação; a região inteira são ~2 MB. Decisão de custo de repositório, resolvível na tarefa que cria a fixture.
