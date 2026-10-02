# ADR-0022 — O critério do corpus de medição, fixado antes da primeira amostra

**Status:** aceito · **Data:** 2026-10-02 · **Fatia-limite:** 5d (`slice-5d-corpus-de-medicao`)
**Referências:** `ARQUITETURA-FINAL-v3.md` §9.2, §9.3, §15, §16 · ADR-0007 · ADR-0012 · `docs/legal/politica-de-privacidade.md` v2.0 §6.3 e §7 · `docs/superpowers/specs/2026-10-02-slice-5d-corpus-de-medicao-design.md` · `docs/protocolo-corpus-de-medicao.md`

## Contexto

O mantenedor quer saber se um OCR embarcado no aplicativo lê a letra do aluno bem o bastante para que o **texto
transcrito**, e não a imagem, vá à IA na correção discursiva. O §9.2 diz que o corte de fiabilidade "tem que sair de
medição, nunca de intuição", e o §9.3 que a decisão sobre o TexTeller espera "evidência do corpus real". Isso exige
três coisas, em três mudanças: **os dados** (esta), **uma bancada** que roda motores candidatos sobre eles e escolhe o
motor (ADR próprio), e **o motor no aplicativo** com o indicador de fiabilidade.

ADR-0007 exige que toda medição feita para decidir tenha o critério registrado **antes** da primeira execução, com a
grandeza, o que aprova, o que reprova e o que acontece se reprovar. O que esta mudança "mede" é se o corpus **basta**
para a bancada decidir; ela não mede leitura.

## Decisão

1. **Grandeza: amostra válida.** Uma amostra do corpus (foto, pontuação do professor, máximo, item) é **válida** quando
   o campo `referencia` está preenchido e `descartar` é `false`. `referencia` é o texto correto da resposta, digitado
   pelo mantenedor com a foto aberta. A contagem sai de `node tools/corpus/formato.mjs <pasta>` (`validas`).
2. **O corpus basta quando** tem **60 ou mais amostras válidas**, de **uma ou duas turmas** do mantenedor, coletadas
   pelo protocolo. 60 é o piso que o mantenedor declarou conseguir; 150 é a meta.
3. **Se não basta:** a bancada pode rodar, mas o resultado **orienta a próxima rodada de coleta e não decide** o motor
   nem o corte. A coleta continua. Nenhum número é reinterpretado depois de visto (P11).
4. **O que o corpus permite decidir:** o motor de OCR candidato (na bancada, ADR próprio) e, depois, o corte de
   fiabilidade (na mudança que integra o motor).
5. **O que o corpus não decide, e o limite que fica escrito:** nada sobre outras turmas, outros alunos, outras
   matérias nem outro instrumento de escrita (lápis, caneta) além do coletado. **Uma ou duas turmas não separam exatas
   de humanas**; qualquer conclusão vale para esse recorte. A cobertura da mudança registra quantas turmas, quais
   matérias e qual instrumento.
6. **Nenhum limiar de leitura é fixado aqui.** Nem taxa de erro de caractere ou de palavra aceitável, nem corte de
   fiabilidade. A bancada fixa o seu, por ADR, **antes** de rodar sobre o corpus (ADR-0007; P11).
7. **A divergência de nota ("a nota não muda", §9.2) não é medida por esta mudança nem pela bancada de leitura:** exige
   corrigir pelos dois caminhos com um LLM, que só existe com o `AiGateway` (fatia 6) e a correção por IA (fatia 8). A
   bancada mede **erro de leitura contra a referência**; a divergência de nota é medida depois, sobre o mesmo corpus.

### Convenções de transcrição (a referência)

- Transcrever **literalmente**, inclusive erros de ortografia, de acentuação e de pontuação do aluno. O motor lê o
  que está escrito; corrigir o aluno mede outra coisa.
- Trecho ilegível: `[ilegivel]`, **nunca** uma adivinhação. Palavra riscada pelo aluno: omitir.
- Quebra de linha da folha: espaço. Parágrafo novo do aluno: linha em branco.
- Fórmula ou símbolo matemático que o teclado não escreve: descrever entre colchetes (`[fracao 3/4]`). A medição de
  fórmula é do TexTeller, que continua **fora de v1** sem evidência (CLAUDE.md).
- Nome ou dado pessoal escrito no corpo da resposta: marcar `descartar: true` e **não** transcrever.

## Consequências

- A coleta existe só no APK de depuração e é do mantenedor; o professor pagante não a vê.
- As fotos ficam fora do repositório. Letra de menor sai do aparelho **por cabo**, pelo mantenedor, em APK de depuração
  (§16, linha "LGPD com dados de menores": reescrita no archive; segue aberta ao jurídico externo).
- A medição de leitura e a escolha do motor ganham veículo próprio (a bancada), com critério próprio escrito antes.
- Reprovar o piso de 60 não invalida nada: adia a decisão e diz o que falta.
