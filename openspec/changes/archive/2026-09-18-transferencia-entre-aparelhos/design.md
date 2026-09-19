## Context

A motivação está em `proposal.md` — *Why*. A medição que a decidiu está em
`docs/cobertura-transferencia-entre-aparelhos.md`, e o que ela mediu é o estado de partida deste
documento:

- O aplicativo declara `android:allowBackup="false"` e **não** declara `android:dataExtractionRules`.
- `minSdk = 26`, `targetSdk = 35`.
- Sob três transportes de backup, o `BackupManagerService` responde `Backup is not allowed`. Sob
  `com.google.android.gms/.backup.migrate.service.D2dTransport` responde `Success`, e o agente escreve
  no fluxo `f/rosters/<org>/<prova>.json`, `db/outbox.db` e `sp/platos-sessao-cifrada.xml`.
- Os três ficheiros vivem em **três árvores diferentes** do diretório de dados:
  `files/`, `databases/` e `shared_prefs/`.

Três restrições moldam a solução:

**C1 — `dataExtractionRules` só existe a partir da API 31.** Com `minSdk 26`, o atributo é ignorado
em Android 8 a 11. O que acontece nesses sistemas **não foi medido**, e este documento não afirma
nada sobre eles.

**C2 — A conferência final é de aparelho, não de suíte.** A propriedade que interessa é o veredito do
`BackupManagerService`, e só o aparelho o produz. Qualquer teste em JVM ou instrumentado é uma guarda
sobre a *entrada* do sistema, e não a observação da saída (P26).

**C3 — O critério de aprovação já está fixado, e antes da correção existir** (ADR-0007). Está escrito
em `docs/cobertura-transferencia-entre-aparelhos.md` §8, e não se mexe nele depois de medir.

## Goals / Non-Goals

**Goals**

- Que o roster, a fila de pendentes e a credencial deixem de entrar no fluxo de transferência entre
  aparelhos, **medido com o mesmo instrumento** que os viu entrar.
- Que a regra seja reprovável sem aparelho, como guarda barata, e reprovável **com** aparelho, como
  a verificação que fecha.
- Que o texto do requisito deixe de dizer "backup automático" quando quer dizer duas coisas.

**Non-Goals**

- **Cifrar o roster em repouso.** Proibido pela ETAPA 2 do plano, e com razão: não está na auditoria,
  não está em requisito nenhum, e `docs/legal/politica-de-privacidade.md` §12 atribui a segurança
  física do aparelho ao usuário. O que protege o nome é o apagamento, e ele existe.
- **Resolver Android 8–11** (C1). Vira item escrito, não implementação.
- **Mexer em `allowBackup`.** Ele fica, e continua sendo o que barra os três transportes de nuvem.
- **Tocar `result-sync`.** A razão está em `proposal.md` — *Modified Capabilities*.
- **Medir se a credencial cifrada chegaria legível ao destino.** A mudança a exclui do fluxo; depois
  disso a pergunta não tem consumidor.

## Decisions

### 1. Negar o **domínio inteiro**, e não cada arquivo pelo nome

```xml
<data-extraction-rules>
    <cloud-backup>
        <exclude domain="root"       path="." />   <!-- acrescentado pela medicao; ver a correcao abaixo -->
        <exclude domain="file"       path="." />
        <exclude domain="database"   path="." />
        <exclude domain="sharedpref" path="." />
    </cloud-backup>
    <device-transfer>
        <exclude domain="root"       path="." />
        <exclude domain="file"       path="." />
        <exclude domain="database"   path="." />
        <exclude domain="sharedpref" path="." />
    </device-transfer>
</data-extraction-rules>
```

**Por quê.** A alternativa — `path="rosters"`, `path="outbox.db"`,
`path="platos-sessao-cifrada.xml"` — nomeia exatamente os três arquivos que a medição encontrou, e
por isso parece mais preciso. É mais frágil, e **o próprio manifesto já argumenta contra ela**, no
comentário que justifica `allowBackup` ser do aplicativo inteiro: *"regra que lista arquivos silencia
quando alguém acrescenta o terceiro"*. A fatia 5 acrescenta discursiva e transcrição ao caminho do
aparelho; um arquivo novo sob `files/` nasceria **fora** de uma regra escrita por nome, e nada
falharia.

Negar o domínio inteiro inverte o ônus: o que quiser atravessar precisa ser declarado, e declarar é
visível em revisão. É a mesma forma da regra que já vale para `allowBackup`.

**O que isto custa, e é aceito:** o aplicativo perde a capacidade de levar qualquer estado para um
aparelho novo. Ele não tem estado que queira levar — tudo o que guarda é cópia de referência que o
pull refaz, exceto o pendente, que **não deve** ser levado por ser dado de aluno fora de qualquer
fronteira de organização.

**Alternativa considerada: `<exclude domain="root" path="." />`.** Nega tudo numa linha só, incluindo
`no_backup/` e `cache/`, e seria ainda mais curta. Rejeitada por ser menos legível: as três linhas
**nomeiam as três árvores que a medição encontrou**, e quem reler o arquivo depois de uma medição
futura consegue emparelhar linha com achado. `root` esconderia essa correspondência.

> **Correção de 2026-09-18, e quem a produziu foi a medição — não um argumento melhor.** O parágrafo
> acima fica (P7), e estava **errado**. A primeira passada da tarefa 4.3, com os três domínios
> negados, mostrou o fluxo ainda carregando `apps/com.platos.android/**r**/app_dxmaker_cache` — e
> `r/` é o domínio `root`. O erro do argumento é de categoria: `root` **não é um domínio a mais na
> lista**, é o próprio `/data/data/<pacote>/`, onde cai tudo o que ainda não tem domínio próprio —
> qualquer diretório criado por `getDir()`, que é API pública.
>
> **O conserto não é trocar, é somar.** `root` entra nas duas seções **junto** com os três, e a
> legibilidade que o parágrafo acima defende fica intacta: as três linhas continuam dizendo o que foi
> visto atravessando, e `root` diz o que sobra. Com os quatro, a segunda passada deixou o fluxo
> **sem entrada nenhuma**, e o transporte cancelou o pacote por não haver dado
> (`docs/cobertura-transferencia-entre-aparelhos.md` §10.2).
>
> A lição é a da própria ETAPA 2: *"Concluir por leitura de documentação"* estava proibido para o
> achado, e a mesma proibição valia para o **conserto** dele. A escolha entre `root` e os três nomes
> foi decidida por argumento de legibilidade e desfeita por medição de uma passada.

### 2. Escrever as regras **nas duas seções**, e não só em `<device-transfer>`

`allowBackup="false"` já barra a nuvem — medido em três transportes. Ainda assim a seção
`<cloud-backup>` entra com as mesmas exclusões.

**Por quê.** Não é precaução genérica: é coerência entre o arquivo e o requisito. Um arquivo de
regras de extração com `<device-transfer>` preenchido e `<cloud-backup>` vazio **diz** que o backup
em nuvem pode levar tudo, e isso contradiz o requisito, que fala dos dois caminhos. E a lição da
medição é precisamente que a repartição de responsabilidade entre atributos mudou uma vez, em silêncio,
numa subida de `targetSdk`. Deixar a intenção escrita nos dois lugares custa três linhas.

**Alternativa considerada: só `<device-transfer>`.** Rejeitada porque deixa o arquivo dizendo, sozinho,
o contrário do requisito.

### 3. Não acrescentar `android:fullBackupContent`

É o equivalente pré-31, e governa **backup em nuvem** — que `allowBackup="false"` já barra, em toda a
faixa de `minSdk`. Acrescentá-lo seria política sem consumidor (P18).

Ele **não** resolve C1: não existe regra pré-31 para transferência entre aparelhos, porque o
mecanismo medido é da API 31 em diante. Isso é leitura, não medição, e por isso C1 vira item escrito
e não afirmação fechada.

### 4. A guarda barata lê o **aplicativo instalado**, e não o repositório

Teste instrumentado que lê `context.applicationInfo.dataExtractionRulesRes` e, se ele existir, percorre
o XML de recurso afirmando as exclusões dos três domínios em `<device-transfer>`.

> **Correção de 2026-09-18.** `ApplicationInfo.dataExtractionRulesRes` **não é SDK público** e não
> compila. O teste abre o `AndroidManifest.xml` de dentro do APK instalado pelo `AssetManager`, tira
> dali o identificador de recurso e percorre o recurso empacotado. O raciocínio abaixo não muda — e
> o caminho novo é **mais forte**: afirma também que o atributo sobreviveu à mesclagem de manifestos
> e ao `aapt2`, e não só que o sistema o carregou. São **quatro** domínios, e não três, pela correção
> da decisão 1.

**Por quê.** A classe de falha que a auditoria encontrou é exatamente "a intenção está no arquivo e
não alcança o sistema". Um teste que leia `apps/android/src/main/res/xml/…` do disco afirma o que eu
escrevi; um que leia `applicationInfo` afirma o que o `PackageManager` derivou do que foi instalado —
que é outro oráculo, e é o independente dos dois. É a mesma escolha de `ConnectionRoleTest`, que lê o
catálogo do Postgres em vez de uma constante do repositório.

**Alternativa considerada: teste de JVM sobre o manifesto mesclado.** Ele roda sem aparelho, o que é
uma vantagem real. Rejeitado como *substituto* e aceito como nada: o manifesto mesclado é intermediário
de build, e afirmar sobre ele é afirmar sobre um passo antes daquele onde a falha mora.

### 5. O critério de aprovação é uma **disjunção**, e isso é deliberado

De `docs/cobertura-transferencia-entre-aparelhos.md` §8: sob `D2dTransport`, ou o veredito passa de
`Success` a `Backup is not allowed`, **ou** o fluxo deixa de conter as três entradas.

**Por quê dois desfechos aceitos.** Não se sabe, antes de medir, se o `BackupManagerService` recusa o
pacote inteiro quando não sobra nada a copiar, ou se o admite e entrega um fluxo vazio. **As duas
coisas cumprem o requisito** — o requisito é sobre o dado não sair, não sobre qual mensagem o console
imprime. Fixar um único desfecho antes de medir seria inventar um critério sobre um detalhe do
framework, e depois ter de afrouxá-lo quando o outro aparecesse: é a forma exata que P11 e P14
proíbem.

**Qual desfecho ocorreu fica escrito**, com o conjunto real ao lado do previsto.

## Risks / Trade-offs

**A regra pode não pegar: excluir tudo talvez não tire o pacote do fluxo** → É o risco central, e é
por isso que a mudança **não fecha sem a medição repetida no aparelho**. Se o fluxo continuar
carregando as três entradas, a mudança falhou e o resultado se escreve como falha — não se reescreve
o critério. A investigação seguinte seria por que a regra não é consultada, e não como fazer a
asserção passar.

**C1: Android 8 a 11 ficam sem cobertura desta regra** → Não é mitigado, é **conhecido**. Vira linha
na tabela de ponto de não-retorno da §16, com fatia-limite "antes de qualquer piloto em modo
`nominal` num aparelho abaixo de Android 12" e dono. É a mesma forma do item que a §7 da cobertura já
registra para Android 12–15.

**A medição fecha na origem, e não no destino** → `D2dTransport` é de mão única: `Can't restore from
D2d Transport`. Com um aparelho, o último elo — os bytes virarem ficheiros no par — não tem
instrumento, e a cobertura já diz isso. O que a mudança altera é o que **entra** no fluxo, e é isso
que a medição repetida observa. Fica dito que o elo final continua herdado, não observado (P26).

**A guarda instrumentada exige aparelho ou emulador** → A suíte instrumentada desta base já exige, e
o teste entra junto dos que já existem. Não cria dependência nova.

**Negar o domínio inteiro poderia apagar algo que o aplicativo quisesse levar** → Não há nada. Se a
fatia 5 criar estado que deva sobreviver à troca de aparelho, a decisão será explícita e a linha de
exceção será visível — que é a propriedade pela qual esta forma foi escolhida.

## Migration Plan

Não há migração: nenhum dado gravado muda de forma, nenhum contrato muda, e a regra só governa o que
o sistema operacional copia.

**Reversão:** remover o atributo do manifesto e o arquivo de recurso devolve o comportamento medido
em 2026-09-18. A reversão é de uma linha, e a guarda instrumentada falha se ela acontecer sem
intenção.

**Ordem dos commits** (regra 1 do `CLAUDE.md`): o recurso e o manifesto **antes** da guarda que os
afirma — a guarda precisa poder ser vista falhar contra o estado anterior, e só depois passar.

## Open Questions

Nenhuma que altere as specs, a abordagem ou as tarefas. As duas incógnitas reais — qual dos dois
desfechos do critério ocorre, e o comportamento abaixo da API 31 — estão tratadas: a primeira pela
disjunção da decisão 5, a segunda como item escrito em C1.
