# ADR-0008 — O pacote publicado é armazenado como texto canônico, não como `jsonb`

**Status:** aceito · **Data:** 2026-08-20 · **Fatia-limite:** 2a
**Referências:** `ARQUITETURA-FINAL-v3.md` §5 (`ExamPackage`), §14 (sync) · D-2a.3, D-2a.4 · ADR-0002

## Contexto

O `content_hash` do `ExamPackage` é calculado sobre uma **serialização canônica** — a mesma que o
`LayoutMap` já usa e que é comparada byte a byte entre três alvos desde a fatia 1. É essa
propriedade que permite ao dispositivo, na fatia 4, remontar o pacote que puxou e conferir se ele é
o que diz ser.

A escolha natural de coluna para um documento JSON no Postgres é `jsonb`: indexável, consultável,
compacto. E é a escolha errada aqui, por um motivo que não aparece na gravação nem na leitura.

`jsonb` não guarda o texto que recebeu: ele guarda uma **árvore normalizada**. Reordena chaves,
descarta espaçamento, colapsa duplicatas. O documento volta semanticamente idêntico e **byte a byte
diferente**.

## Decisão

`exam_package.content` é `text`. A validação de sintaxe fica a cargo de uma restrição de checagem
que usa o cast (`check (content::json is not null)`), porque o cast valida sem normalizar o que fica
gravado.

O que a coluna guarda são os bytes exatos sobre os quais o hash foi calculado — nem mais, nem menos.

## Consequências

- O artefato armazenado é conferível por si só: `sha256(content)` tem de dar `content_hash`. A
  verificação não depende de quem gravou, e é a mesma que o dispositivo faz ao receber o pacote.
- Um efeito colateral desejável apareceu com o artefato versionado: `sha256` do arquivo
  `fixtures/prova-referencia.package.json` **é** o `content_hash` do pacote. Conferir o artefato do
  repositório é um `sha256sum`.
- Perde-se consulta indexada sobre o conteúdo do pacote. É aceitável: o pacote é lido inteiro, por
  identificador, para ser distribuído — não é fonte de consulta analítica. Quando M3 precisar
  agregar, agregará sobre `assessment_fact`, que é append-only e existe para isso (I2).
- TOAST continua comprimindo a coluna em repouso, então o custo de armazenamento não muda de forma
  relevante em relação a `jsonb`.

## Como isto poderia ter falhado em silêncio

Com `jsonb`, nada quebraria na publicação. O pacote gravaria, leria e pareceria correto. A falha
apareceria na fatia 4, no único lugar onde o pacote é verificado — o dispositivo recalcularia um
hash diferente do declarado e recusaria a referência imutável. E a correção óbvia, naquele momento,
seria a errada: rehashear no servidor a partir do que o banco devolve, transformando o hash num
carimbo do armazenamento em vez de uma afirmação sobre o artefato publicado.

Por isso a decisão tem guarda executável, e a guarda foi vista falhar. Ver
`docs/cobertura-fatia-2a.md`: um gatilho que normaliza via `jsonb` mantendo a coluna `text` — a
versão silenciosa do defeito — derruba exatamente os três testes que comparam conteúdo, e nenhum
outro.

## Alternativas descartadas

**`jsonb` com o hash recalculado na leitura.** Faz o hash descrever o que o banco devolve, e não o
que foi publicado. Um pacote adulterado direto no armazenamento passaria a conferir consigo mesmo.

**`jsonb` mais uma coluna `text` com os bytes originais.** Duas fontes da verdade para o mesmo
conteúdo, que podem divergir — e a que seria distribuída é justamente a que ninguém consultaria.

**`bytea`.** Equivalente em garantia, pior em operação: o conteúdo deixa de ser legível numa
inspeção manual, e não há ganho, porque a serialização canônica já é UTF-8 por definição.
