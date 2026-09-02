# ADR-0013 — O aparelho puxa o pacote por rota própria, confere em três camadas e cacheia por conteúdo

**Status:** proposto · **Data:** 2026-08-29 · **Fatia-limite:** 4a
**Referências:** `ARQUITETURA-FINAL-v3.md` §10 (sincronização e offline), §13 (stack) · ADR-0008 · ADR-0009 · `openspec/specs/scan-session` · `CLAUDE.md` regras 2 e 8

## Contexto

Até a fatia 3c o aparelho carregava o `ExamPackage` como asset embutido, e o próprio código marcava
isso como provisório. A fatia 4 é onde §10 manda o pull de referência imutável existir de verdade.

O terreno, medido antes de decidir:

- a API tem **duas rotas** — `/health` e `/me/organizations`. Não há superfície de pacote;
- o aparelho **não tem autenticação nenhuma**;
- o aparelho não tem Room, WorkManager nem cliente HTTP;
- ADR-0008 escolheu `text` em vez de `jsonb` explicitamente para que o dispositivo pudesse conferir
  o pacote na fatia 4, e o `sha256` da fixture versionada **é** o `content_hash`, afirmado em duas
  suítes independentes;
- ADR-0009 fixa prova↔pacote em 1:1 e imutável, porque o QR impresso não carrega identificador de
  pacote. Não existe "pacote desatualizado" para uma prova: existe presente ou ausente.

## Decisão

### 1. Autenticação é fatia própria, e vem antes do pull

O login no aparelho vira a fatia **4a-zero**, separada da 4a.

O corte alternativo — 4a com credencial configurada fora de banda — foi **rejeitado**: um critério
de aceite que não exercita o caminho real adia o custo e esconde o risco. A 4a-zero tem critério
próprio e vertical porque `/me/organizations` já existe: o aparelho autentica e mostra a
organização que veio da API. Isso reprova bastante coisa sozinho.

**Logout entra na 4a-zero**, e não é conveniência: ele é a contraparte do escopo de cache da
decisão 3. Sair apaga o cache da organização, senão o aparelho compartilhado guarda pacote de quem
saiu.

### 2. O transporte é rota Ktor própria, com os bytes exatos e o hash em cabeçalho

O caminho direto à tabela por `supabase-kt` foi descartado por duas razões independentes.

**A de byte:** PostgREST devolveria `content` como string JSON escapada dentro de um envelope. O
desescape deveria ser sem perda, mas normalização silenciosa de JSON é exatamente o que ADR-0008
existe para impedir, e submeter o hash a mais uma etapa de codificação contraria o motivo daquela
decisão.

**A de fronteira:** rota própria dá um único ponto de autenticação. O caminho direto obrigaria a
adotar RLS como fronteira de segurança do aplicativo — compromisso grande demais para esta fatia.

A rota devolve os bytes exatos gravados em `exam_package.content`, e o `content_hash` em cabeçalho.

### 3. O cache é arquivo endereçado por conteúdo

`packages/<organization_id>/<content_hash>.json`, escrita atômica por arquivo temporário seguido
de rename.

O nome do arquivo **é** a afirmação de integridade, e invalidação deixa de ser problema: conteúdo
diferente é arquivo diferente, e pacote publicado nunca muda (ADR-0009).

**O diretório é escopado por organização, e isso corrige um furo na fronteira.** Endereçamento por
conteúdo é global por natureza, e o cache é um caminho de leitura que não passa pela rota — um
aparelho compartilhado, com troca de usuário, entregaria da pasta um pacote que a rota recusaria.
O escopo por organização põe o cache atrás da mesma fronteira que a decisão 2 concentrou num ponto
só; o endereçamento por conteúdo continua, dentro dela.

**Ler do disco re-verifica.** O hash é recalculado sobre os bytes lidos, e não sobre o nome do
arquivo: divergência descarta o arquivo e recai no pull. Sem isso, a conferência da decisão 4 vale
uma vez, na primeira gravação, e o que o aparelho usa em toda abertura seguinte é um arquivo que
ninguém mais olhou — corrupção em repouso, escrita truncada por queda de energia, ou adulteração
local passariam caladas.

Room fica para a 4b, onde o outbox é de fato relacional. §13 sanciona Room, e a regra 8 do
`CLAUDE.md` diz para não abstrair antes da necessidade comprovada — o que a 4a guarda é um blob
imutável endereçado por identificador, que é trabalho de sistema de arquivos.

### 4. A verificação tem três camadas, e nenhuma delas é autenticidade

**(a) `sha256(bytes recebidos) == content_hash`.** Prova integridade de transporte.

**(b) `toCanonicalJson(parse(bytes)) == bytes`.** Prova fidelidade de ida e volta do parser.

`toCanonicalJson()` e `contentHash()` moram em `commonMain` — uma implementação, compilada para o
servidor e para o aparelho. Isso torna (b) barata, e **estreita o que ela prova**: ela não confere
a serialização canônica contra uma segunda implementação, e sim que parsear e re-serializar é
identidade. O que ela pega é desalinhamento de versão — campo que um APK antigo descarta em
silêncio, ou default que ele injeta. O hash responde "recebi o que foi publicado?"; (b) responde
"eu entendi o que recebi?", e as duas perguntas são rotineiramente confundidas. (b) subsume o
parsing estrito e ainda pega a injeção de default, que o estrito não pega.

Some junto o guarda que já é declarado e que nada aplica no caminho de captura: o gate confere
`min_renderer_version` do pacote. Ele pega o desalinhamento que o publicador previu; (b) pega o que
ninguém lembrou de declarar.

**(c) O pacote afirma que é desta prova e desta variante,** conferido contra o payload do QR e não
contra o que o pedido pediu. Confiar no pedido deixaria um pacote íntegro da prova errada passar
pelo portão — íntegro e errado é exatamente o que este ADR existe para impedir.

**Nada disto é autenticidade.** O hash vem do mesmo servidor que os bytes: quem controlar a resposta
controla os dois. Autenticidade é do TLS, e escrever isso aqui impede que a conferência tripla seja
lida como uma garantia que ela não dá.

### 5. O asset provisório sai na mesma fatia

`assets.open` deixa de existir quando o pull entra. Se sobreviver "por enquanto", vira fallback
permanente, e o portão binário de ADR-0009 morre em silêncio — o aparelho passaria a escanear com
um pacote que ninguém puxou nem conferiu.

O comportamento de falha é **recusar a escanear, com motivo explícito**. Nunca degradar.

## Consequências

- A 4a-zero introduz autenticação no aparelho **e o cliente HTTP**, e a 4a introduz a primeira rota
  de pacote da API. Nenhum dos três é tecnologia nova: §13 já os prevê. O cliente veio para a
  4a-zero, e não para a 4a como este ADR previa, porque a decisão 9 daquela fatia dispensou
  `supabase-kt` e fez a autenticação sair pelo mesmo Ktor — o mesmo raciocínio da decisão 2 aqui.
- O gate de pré-voo é binário por construção, e isso vem de ADR-0009 e não de simplificação.
- A 4a desbloqueia a tarefa **6.4b**, herdada da 3c: com duas provas publicadas e puxáveis, a recusa
  de folha de outra prova ganha o oráculo físico que hoje não existe. **É o candidato a corte se a
  fatia crescer**: a conferência de identidade já é coberta em unidade, e 6.4b é a versão física
  dela. Cortá-la deixa a dívida da 3c aberta e sem outra fatia natural antes do MVP, e essa é a
  troca a fazer conscientemente — não por omissão.
- `min_renderer_version` passa a ser imposto no caminho de captura, e não só na renderização.
- Modo degradado e outbox continuam fora: são 4b e 4c.
- A herança entre usuários no aparelho compartilhado fica escrita **nos dois lados**, e de propósito:
  na 4a-zero o logout apaga sessão e organização escolhida; na 4a ele apaga também o diretório de
  cache de pacotes daquela organização, e a entrada seguinte refaz o pull. Um requisito genérico de
  "limpar dados locais" é fácil de dar como cumprido sem reler, então cada fatia nomeia o que
  apaga. Não há registro de "estado local" — com dois itens seria abstração prematura (regra 8).

## Como isto poderia falhar em silêncio

O asset sobrevivendo à fatia é a falha mais provável, e a mais quieta: tudo continua funcionando, e
o que se perde é a garantia de que o pacote em uso foi conferido. Por isso a remoção é critério de
aceite, e não tarefa de limpeza.

A segunda é aceitar (a) como se fosse (b). Um pacote cujo hash confere e cujo parse perdeu um campo
produz nota plausível e errada, e não há sintoma na tela — é a mesma forma de falha que a fatia 3c
verificou não existir sob luz e inclinação, chegando por outra porta.
