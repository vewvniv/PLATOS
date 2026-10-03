# O que falta da fatia 5, em ordem de dependência

Escrito em 2026-10-01 (hora local +02:00), depois do archive da `slice-5c-1-a-resposta-fica-no-aparelho` (#85).
**Este documento ordena; não decide.** Cada item abaixo tem um `/opsx:explore` ou um `/opsx:propose` a fazer, e é lá que
escopo, ADR e nome definitivos se fecham. O que está **fixado por fonte** vem com a fonte; o que é **proposta minha**
vem dito; o que é **pergunta** vem como pergunta. A tabela "Ponto de não-retorno" do §16 continua sendo o único
registro de dívida (P27): este plano a **lê**, e não a substitui.

## Onde estamos

O §15 define a fatia 5 como "Regiões discursivas · completude · deviants · correção manual · **corpus de medição**".
Entregue e arquivado: `slice-5a-regiao-discursiva`, `slice-5b-0` a `5b-4` (reconhecer, parcial objetiva, caderno,
envio da parcial), `slice-5c-0` (o recorte), `5c-1` (a resposta fica no aparelho, o professor a vê, o prazo de
30 dias), `5c-2` (a nota do professor: contrato e servidor), `5c-3` (a nota no aparelho) e `slice-5d` (o corpus: o
instrumento de coleta e o critério, sem nenhuma amostra de aluno ainda). **Falta o fechamento de caderno incompleto e a
guarda de dívida**, mais o que precede o papel. A bancada de motores de OCR e o OCR no aplicativo são mudanças novas, ainda
não propostas. A fatia corrente derivada pela guarda é `5d`.

## A ordem

```
1  5c-2 · a nota do professor ─┐
                               ├─▶ 2  corpus de medição ─▶ 4  finalizar caderno incompleto ─▶ 3  a guarda lê a tabela "Aberto"
   (contrato, fato, envio)     │                                                                      │
                               └─ (o 4 também depende do contrato do 1)                               ▼
                                          mudança da folha de teste ─▶ sessão única de papel ─▶ fatia 6
```

| Ordem | Item | Depende de | Por quê (e de que tipo é a dependência) |
|---|---|---|---|
| **1** | `5c-2` — a nota do professor | `5c-1` (feita) | **Dura.** A nota é entrada sobre a imagem que a 5c-1 guardou, e o fato de correção, o envio e o servidor nascem aqui. |
| **2** | Corpus de medição (§9) | 1 | **Dura.** O alvo da calibração é "a nota não muda" (§9.2): "corrija cada resposta pelos dois caminhos" contra um corpus real. Sem a **nota do professor** como referência, não há o que comparar. O §9.2 pede ainda "guarde sempre a imagem e registre por qual caminho a nota saiu": é uma coluna do fato do item 1. |
| **4** | Finalizar caderno incompleto (§8) | 1 (dura), 2 (mole) | **Dura com o 1:** "registra o que faltou" é campo do que o envio leva, e o contrato do envio é do item 1. **Mole com o 2:** a taxa de recusa do recorte em foto real (hoje desconhecida) diz **quanto** o professor vai usar isto e se a tela oferece "refotografar" antes. Pode trocar de lugar com o 2 se a decisão for desenhar sem esse número. |
| **3** | A guarda de dívida lê a tabela "Aberto" | 2 (ordem, ver abaixo) | **Ordem, e não dependência técnica**: não achei no código nem nas propostas nada do item 3 que precise do corpus. O motivo de pô-lo por último é que o §16 manda reconciliar a linha de injeção de prompt "no archive da **última** mudança da fatia 5", e uma guarda que já lê as duas tabelas audita a fatia inteira no fecho. Precisa estar pronto **antes de a 6 abrir** (limite `5`). |
| — | Mudança da folha de teste de impressão | nenhuma técnica | Já é linha `6` do §16 ("ainda não proposta"). Entra antes da sessão de papel, que a aprova. |
| — | **Sessão única de papel** | 1, 2, 4, 3 e a folha de teste | Decisão do mantenedor: uma ida só, com protocolo escrito antes, depois de todo o resto que roda no aparelho. Fecha as linhas `6` sobre papel e letra. |

## O que isto força, e o que a guarda já cobra

- **A ordem 1 antes de tudo é imposta pela guarda, não só combinada.** Verificado em 2026-10-01 com cópias do diretório de
  mudanças (`node tools/divida/divida.mjs --mudancas …`, cópias criadas na hora): com uma mudança `slice-5c-2-…`, `exit 0`;
  com uma `slice-5d-…`, **`exit 1`** — "linha vencida sem reconciliação: O teto de 30 dias das respostas só roda quando o
  aplicativo abre (`5c`): a fatia 5c já passou, e a corrente é 5d". Ou seja: nenhuma mudança `slice-5d-…` pode ser
  proposta enquanto o item 1 não pagar ou reagendar essa linha, e **o item 1 pode se dividir em `5c-2`, `5c-3`… sem
  mover a fatia corrente**.
- **Nomes e a fatia corrente.** A convenção do repositório é: mudança que não é fatia não carrega `slice-`
  (`docs/plano-de-correcao-antes-da-fatia-5.md`). Proposta: os itens 2 e 4 são produto da fatia 5 e levam `slice-5d-…` e
  `slice-5e-…`; o item 3 é ferramenta e **não leva `slice-`** (como `registro-de-divida-executavel`), para não mover a
  corrente sozinho. Nomes definitivos são do `/opsx:propose`.
- **O `6` do §16 não obriga "antes da 6".** Uma fatia-limite sem letra cobre a fatia inteira e só vence quando a corrente é
  estritamente depois: a linha `6` vence na **7**. "A sessão única de papel antes da 6" é compromisso escrito em prosa,
  como o §16 diz, e nenhuma verificação o faz valer. Vale o mantenedor decidir se isso basta (P27 pede que prazo não more
  em prosa).
- **Dois eventos de implantação** não são fatias, mas a mudança do item 1 cai neles: `antes-de:migration-da-5-em-producao` e
  `antes-de:implantar-api-da-5a`. Nenhuma migration da fatia 5 vai a produção antes de resolver os dois.

## Item 1 — `5c-2`, a nota do professor

**Fixado por fonte.** Proposta da 5c-1 (arquivada): nota, entrada de nota, fato de correção, outbox, servidor, migration,
`result-sync`, `apps/api` e `apps/web` são da 5c-2; "a forma do fato (revisão nova de `grading_result` ou tabela própria)
é ADR dela". A tela de correção parte do sinal de desvio (§16, limiar do desvio). Gatilho da classe H "após a
sincronização": nasce com a nota (§16, linha `5c`), que o item 1 também **paga ou reagenda**: o teto de 30 dias só roda
quando o aplicativo abre, e o veículo escrito é um `WorkManager` periódico mais uma trava entre a varredura e o analisador.

**Proposta minha — provavelmente são mais de uma mudança.** A regra 3 do `CLAUDE.md` manda reavaliar e dividir quando
uma mudança toca mais de duas capabilities; aqui toca `scan-session`, `result-sync` e, pelo menos, uma capability de
correção e a api. Pela regra 1 (contrato antes de consumidor), a divisão natural é **contrato e servidor primeiro** (o
ADR do fato, migration, `result-sync`, api) e **o aparelho depois** (tela de nota, outbox, envio). Isto é para o
`/opsx:explore` confirmar.

**Perguntas.** Revisão de `grading_result` ou tabela própria? A nota é do aluno inteiro ou por questão? O caminho pelo
qual a nota saiu (professor, OCR, IA) é coluna do fato desde já (§9.2)? A nota do professor substitui ou acompanha a
parcial objetiva que já está no outbox? A imagem continua não subindo (política §6.4)?

## Item 2 — corpus de medição

**Fixado por fonte.** §15: "corpus de medição" é da fatia 5, "os dados para decidir §9". §9.2: calibrar o limiar contra
"a nota não muda"; confiança de OCR em manuscrito é descalibrada, então "o número tem que sair de medição". §9.3 e D43:
TexTeller só entra em v1 com evidência do corpus real. Linhas do §16 que ele alimenta: acurácia em manuscrito (`6`, "medir antes de
construir a 8") e o limiar do OMR apurado num aparelho e numa impressora (`6`, "é a fatia do corpus"). O limiar do desvio e
o teto do resíduo, fixados sem letra de aluno (`6`), têm como veículo escrito a sessão de papel, e não o corpus.

**Bloqueio que não é código.** A política §12 diz que dado real de aluno não é usado em desenvolvimento ou teste, e a
proposta da 5c-1 já avisou que o corpus **resolve isso antes**. Sem essa resolução (parecer, consentimento, ou corpus
com escrita de adultos), a mudança não começa. É o único item cujo primeiro passo é decisão do mantenedor e não do código.

**Perguntas.** Quem escreve o corpus, e como a imagem sai do aparelho para ser medida, se "nenhuma imagem sai do
dispositivo" (política §6.4)? A coleta divide a ida com a sessão de papel (o §16 diz que toda conferência em papel é "numa sessão única antes da
fatia 6")
ou é outra? O que a medição decide, por escrito, antes de olhar o resultado (P11)?

## Item 4 — finalizar caderno incompleto

**Fixado por fonte.** §8, "Completude": "Finalizar incompleto exige confirmação explícita e registra o que faltou." É
tudo o que a arquitetura diz. Hoje, caderno incompleto **só se perde**: a folha de outro aluno o substitui e nada é
entregue (spec `scan-session`, cenário "Caderno incompleto substituído por outro aluno não é entregue"), e uma
página perdida impede o caderno de completar enquanto ela não for escaneada. A necessidade existe, portanto, **independente** da taxa de
recusa; o que a taxa muda é o peso.

**Sem escopo definido — o que o `/opsx:explore` precisa fechar.** O que o envio leva de um caderno incompleto (parcial
marcada como tal, com a lista de regiões que faltaram)? Isso é campo novo do contrato do item 1. A região sem resposta
ainda aguarda correção? A confirmação é por região ou por caderno? O `entregue` de um caderno finalizado incompleto
pode depois ser completado? Como isto convive com o "refazer" da 5c-1?

## Item 3 — a guarda lê a tabela "Aberto"

**Fixado por fonte.** Linha `5` do §16: `tools/divida/divida.mjs` lê só "Ponto de não-retorno"; a tabela "Aberto" tem
linhas com limite 5 que venceriam, calados, na abertura da 6. "Antes de a fatia 6 abrir; uma mudança própria dentro da
fatia 5." Duas saídas legítimas já escritas, a decidir na mudança: a guarda passar a ler as duas tabelas, ou as linhas da
"Aberto" migrarem para a primeira. A linha de injeção de prompt manuscrita é reconciliada — paga ou reagendada para `8`,
com o motivo — no archive da **última** mudança da fatia 5. **Não a localizei como linha própria da "Aberto"** em
2026-10-01: só aparece citada dentro da linha da guarda; confirmar no `/opsx:explore` antes de assumir que existe.

**Cuidado.** É mudança de ferramenta e de CI; o nome não leva `slice-`, ou ela move a fatia corrente.

## Decisões que são do mantenedor

1. **Os nomes `5d` e `5e`** para o corpus e o fechamento de caderno incompleto, ou outro esquema.
2. **Dividir o item 1** (contrato e servidor, depois aparelho), o que a regra 3 sugere.
3. **A política §12 e o corpus:** o caminho para ter escrita de aluno a medir.
4. **O corpus divide a ida ao papel ou não.**
5. **O item 3 por último** é ordem sua, e não dependência técnica (ver a tabela). Se preferir cedo, nada técnico impede, e
   a guarda passa a auditar as mudanças seguintes em vez de só o fecho.
6. **"Antes da 6" está só em prosa** (a linha `6` vence na 7).
7. **O item 4 antes ou depois do 2** (dependência mole).
