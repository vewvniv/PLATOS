# Proposal

## Why

O mantenedor quer saber se um **OCR embarcado no aplicativo** lê a letra do aluno bem o bastante para que o **texto transcrito**, e não a imagem, vá à IA na correção discursiva: a visão do LLM consome mais tokens (§9.1). A **fiabilidade da transcrição** decidiria, por resposta, se o texto segue, se a imagem vai à IA ou se o professor corrige à mão (§9.2; D42).

Isso não se decide sem letra real. O §15 põe o "corpus de medição" na fatia 5 como "os dados para decidir §9", e o §9 diz que o número "tem que sair de medição, nunca de intuição" (§9.2) e "medir antes de otimizar" (§9.3). Hoje não há como obter essa letra: a imagem da resposta fica no aparelho e é **eliminada quando o servidor confirma a nota do professor** (5c-3), e nada nela diz se o texto certo era aquele.

São três mudanças, nesta ordem, e **esta é só a primeira**: (1) **os dados** — letra real, a nota do professor e uma referência do texto correto; (2) **uma bancada** que roda motores candidatos sobre esses dados, no computador, e escolhe o motor por ADR; (3) **o motor no aplicativo**, com o indicador de fiabilidade. Esta mudança não escolhe motor, não mede e não fixa limiar.

O bloqueio que a 5c-1 anunciou (a política dizer que dado real de aluno não é usado em desenvolvimento ou teste) foi resolvido pelo mantenedor em 2026-10-02, com a edição da política (v2.0, §6.3 "Corpus de Medição e Calibração", §7 item 2, classe A do §10).

## What Changes

Decisões do mantenedor de 2026-10-02 que esta proposta fixa:
- **Só o mantenedor coleta, nas turmas dele.** A coleta **não é recurso do produto**: o professor pagante não a vê, e o aplicativo de release não a contém.
- **O código da coleta existe só no APK de depuração.** O APK de release recebe uma implementação vazia da mesma interface. A garantia é de compilação, e não de configuração.
- **Escopo: só a coleta e o protocolo.** OCR, escolha de motor, bancada, limiar e roteamento texto/imagem são as mudanças seguintes. A ordem do §15 não muda.
- **Corpus: 60 a 150 respostas de aluno real, de uma ou duas turmas.** Isso não separa exatas de humanas, e o ADR de critério diz isso antes de qualquer resultado.

O que muda, em comportamento:
- **Interruptor de coleta, desligado por padrão (`scan-session`, `measurement-corpus`).** Ligado, **toda nota confirmada copia todas as discursivas da folha** para uma pasta privada do aparelho. Desligado, nada é copiado. A escolha não é por resposta.
- **A cópia vem antes da gravação da nota e não a impede.** A imagem pode ser eliminada assim que o servidor confirma a nota, e por isso a cópia lê a imagem antes. Falha da cópia não derruba a nota; nota que não foi gravada não deixa amostra.
- **O que uma amostra leva:** a foto (os mesmos bytes que o aparelho guardou), a pontuação dada pelo professor e o máximo da questão (decimal exata, como em `scoring`), o hash do pacote e o item (o enunciado e a rubrica não são copiados: o sistema já os tem em texto exato), um identificador **aleatório** e os campos `referencia` e `descartar`, que o mantenedor preenche no computador. **Não leva** nome, turma, matrícula, o token de aluno, a captura, o caderno, o caminho do arquivo no aparelho, data nem hora (I5; política §6.3).
- **As amostras têm prazo e somem com a sessão.** No máximo 30 dias, o mesmo teto das respostas; sair da sessão e a revogação do vínculo as eliminam, porque são **cópia** e não o único exemplar de um trabalho já feito.
- **A saída é por cabo (`adb`, `run-as`), sem tela de exportação, sem compartilhar, sem rota e sem tabela.** Um script confere que cada foto tem o seu JSON e que a contagem bate. As fotos ficam **fora do repositório**; o `.gitignore` é a segunda rede.
- **A referência é digitada no computador**, com a foto aberta, no campo do JSON, e o mantenedor descarta as amostras com nome ou dado pessoal escrito. O app não tem nada disso.
- **O critério é escrito antes do primeiro resultado (P11, ADR-0007):** um ADR com a composição do corpus, o que ele permite decidir (o motor; depois o corte de fiabilidade), o que não decide e as convenções de transcrição, mais o protocolo da coleta em `docs/`. **Nenhum número de limiar é fixado aqui.**

**Premissas minhas, para a revisão** (não perguntadas ao mantenedor):
1. O interruptor é um ajuste local do APK de depuração, e o estado dele é por aparelho.
2. O prazo de 30 dias é o teto das respostas, e não um número novo medido.
3. O formato do JSON da amostra tem uma versão, e a bancada futura o lê em outra linguagem; o formato é contrato digitado uma vez e conferido nos dois lados (P28).
4. Corrigir depois uma nota (revisão nova) não altera a amostra já copiada.

**Não será alterado** (limite desta mudança):
- Servidor, `apps/api`, banco, `result-sync`, o contrato do fio e o domínio KMP.
- A imagem da resposta, o prazo de 30 dias, a eliminação por confirmação da nota, e o que sair e a revogação preservam (5c-1, 5c-3).
- A nota do professor, a regra "revisão humana vence" e o desvio.
- **OCR, escolha ou integração de motor, bancada, qualquer limiar, `AiGateway`, `TranscriptionProvider`, roteamento texto/imagem, sugestão de correção manual, qualquer chamada de LLM.** TexTeller continua fora de v1 (CLAUDE.md).
- Tela de exportação, declaração, compartilhar, hash de arquivo, confirmação de entrega.
- A edição da política e a reconciliação dos documentos que citam a v1.0 (ver Impacto): é do mantenedor e vai em commit próprio.

## Capabilities

### New Capabilities
- `measurement-corpus`: o que é uma amostra do corpus e o que ela nunca contém; a coleta existe só no APK de depuração e vem desligada; a saída é por cabo e não passa pelo servidor; o prazo e a eliminação das amostras.

### Modified Capabilities
- `scan-session`: um requisito acrescentado (com a coleta ligada, a nota confirmada deixa a cópia das discursivas, antes da gravação, sem impedi-la).

## Impact

- **Android** (`apps/android`): `src/debug` e `src/release` (hoje inexistentes) para a implementação real e a nula da interface de coleta; a interface e o ponto de chamada em `src/main`; a eliminação por prazo dentro da varredura periódica da 5c-3 e na abertura; a verificação do APK de release (`VerificarApkSemPacoteTask` e a vacuidade por variante) passa a conferir a **ausência** da coleta. Nenhuma tecnologia nova.
- **Ferramentas e documentos:** `tools/corpus/` (o script que puxa e confere), uma linha no `.gitignore`, `docs/protocolo-corpus-de-medicao.md`, o ADR do critério do corpus (próximo número livre depois do 0021) e `docs/cobertura-slice-5d-corpus-de-medicao.md` com como cada verificação foi vista falhar. O protocolo entra como referência no roteiro da sessão única de papel (`docs/protocolo-medicao-impressa.md`); a coleta de alunos não depende da impressora.
- **Contrato digitado uma vez (regra 7, P28):** o JSON da amostra é lido depois por outra linguagem; precisa de conferência cruzada no molde de `tools/parity/fio.mjs`.
- **Política v2.0 e documentos que citam a v1.0 (declarado, fora do escopo):** a edição de 2026-10-02 removeu o que ADR-0012, o §16 e a spec `scan-session` citam: §3.5 (coerção do papel), §4 (bloqueio de roster nominal), **§6.4** ("nenhuma imagem sai do dispositivo"), **§10.8 / classe H** (a imagem no aparelho), **§12** ("dados reais de alunos não são utilizados em desenvolvimento ou teste") e as "oito classes" (a v2.0 tem seis, A a F). Esta mudança só cita a v2.0 onde a usa (§6.3, §7, classe A). A reconciliação do ADR-0012, do §16 e da spec `scan-session` é do mantenedor, e **não é feita em silêncio** (P7, P17): o que fazer com a classe H e a coerção do papel, que o código implementa, precisa ser dito antes.
- **Dívida (§16, P27).** `node tools/divida/divida.mjs`: fatia corrente `5d` (exit 0, 24 linhas lidas). Linhas que esta mudança alcança, e o que o **archive** diz de cada uma:
  - **`LGPD com dados de menores` (`antes-de:primeiro-piloto`)**: pela primeira vez **imagem de manuscrito de menor sai do aparelho**, por cabo, só pelo mantenedor, em APK de depuração. O archive reescreve a linha com isso e com o que a política v2.0 §6.3 passa a sustentar; continua **aberto: jurídico externo**. Não é paga.
  - **`Acurácia em manuscrito` (`6`)**: a mudança **coleta**, e não mede. Segue `6`; o archive diz quantas amostras, de quantas turmas, ou que nenhuma foi coletada ainda.
  - **`A política §10.8 diverge do comportamento` (`antes-de:publicacao-da-politica`)**: a §10.8 deixou de existir na v2.0. O archive diz se a linha muda de referência ou se sai, conforme a decisão do mantenedor.
  - **`A guarda de dívida não lê a tabela "Aberto"` (`5`)**: **não é tomada aqui**; segue `5` (limite a abertura da fatia 6). Silêncio não é reconciliação.
  - **`O limiar do OMR foi apurado sobre um aparelho e uma impressora` (`6`)**: é medida de impressão, e não de letra; entra na sessão de papel. O archive diz que não é paga aqui.
- **Conferência física:** a coleta com turma real, num aparelho físico, é feita pelo Leon no fim, com a prova final (não para o fluxo).
