# ADR-0009 — Uma prova publicada tem um pacote, e corrigir é publicar prova nova

**Status:** aceito · **Data:** 2026-08-20 · **Fatia-limite:** 2a
**Referências:** `ARQUITETURA-FINAL-v3.md` §5 (`ExamPackage`), §8 (regiões escaneáveis e captura) ·
D-2a.3 · ADR-0002

## Contexto

`ExamPackage` publicado é imutável e tem hash. Isso resolve "não se altera um pacote" e deixa em
aberto uma pergunta que o armazenamento precisa responder: **quantos pacotes uma prova pode ter?**

A resposta não vem da modelagem, vem do papel. §8 fixa o payload do QR impresso:

```
{exam_short_id}.{student_token}.{variant}.{region_idx}.{crc}
```

Não há identificador de pacote ali, e não pode haver: o QR é lido offline, a partir de uma folha que
já foi impressa, por um aparelho que talvez nunca tenha visto o pacote antes. O que ele carrega é a
prova, o aluno, a variante e a região.

Se a mesma prova pudesse ter dois pacotes publicados, a folha em cima da mesa deixaria de dizer de
qual geometria ela saiu. O OMR leria bolhas em coordenadas de outra publicação — e leria **em
silêncio**, porque coordenadas normalizadas ao quad dos ArUcos continuam sendo coordenadas válidas.

## Decisão

`exam_package` tem `unique (exam_id)`: uma prova publicada tem exatamente um pacote.

`exam.short_id` é único **global**, e não por organização, porque a captura resolve a prova por ele
sem saber de organização.

Corrigir uma prova já publicada é publicar **prova nova**, com `short_id` próprio e pacote próprio —
que é exatamente o que a folha já distribuída diz que ela é: outra prova.

Republicar sobre uma prova existente é recusado pela restrição do banco, e a recusa é traduzida para
um erro identificável (`ExamAlreadyPublishedException`), nunca por consulta prévia — um `select`
antes do `insert` deixaria janela entre a checagem e a gravação.

## Consequências

- A folha impressa determina, sozinha, qual geometria a corrige. É a propriedade que a fatia 3
  depende para ser offline.
- O roster pertence à prova. Corrigir uma prova publicada obriga a recriar o roster da prova nova.
  É custo real, e é o preço de a folha ser autodescritiva; a alternativa transfere o custo para o
  dia da correção, onde ele é pior.
- Não há lineage entre a prova corrigida e a original nesta fatia. Se for preciso, entra como campo
  próprio (`supersedes_exam_id`) sem mexer no artefato publicado.
- A recusa de republicação é `unique_violation` traduzida, e **só** ela: traduzir a classe 23 inteira
  diria "prova já publicada" para um `short_id` fora do formato, que é outro defeito e outra
  correção.

## Alternativas descartadas

**Vários pacotes por prova, com versão.** Preserva o roster e a identidade da prova entre correções,
e quebra a captura: o QR não tem onde carregar a versão, e acrescentá-la ao payload significaria
mudar o contrato do impresso — que é o artefato mais caro de mudar do sistema, porque já está na mão
do aluno.

**Vários pacotes por prova, com o mais recente vencendo.** Pior que a anterior: a folha antiga
passaria a ser corrigida pela geometria nova sem nada acusar. É o modo de falha que este ADR existe
para tornar impossível.

**Pacote mutável com histórico de alterações.** Contradiz D-2a.3 diretamente. Imutabilidade que
guarda o que havia antes ainda é mutabilidade para quem já copiou o artefato.
