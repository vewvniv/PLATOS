# Protocolo — coleta do corpus de medição

**Critério:** `docs/adr/0022-o-criterio-do-corpus-de-medicao.md` (lido **antes** de começar). **Quem coleta:** só o
mantenedor, nas turmas dele, com o APK de **depuração**. O professor pagante não vê isto, e o APK de release não contém
o código (`./gradlew :apps:android:verificarApkSemColeta`).

## 1. Preparar o aparelho

1. `./gradlew :apps:android:installDebug` (não apaga os dados do aplicativo).
2. Ligar a coleta: `adb shell run-as com.platos.android touch files/coleta-ligada`.
   Desligar: `adb shell run-as com.platos.android rm files/coleta-ligada`. Desligada (o padrão), nada é copiado.

## 2. Corrigir a turma, normalmente

Escaneie as folhas e **dê a nota de cada caderno** na tela de nota. A cada nota confirmada, o aplicativo copia **todas
as discursivas** da folha para `files/corpus/` (foto + um arquivo de dados), **antes** de gravar a nota. Se a cópia
falhar, a nota é gravada do mesmo jeito e aparece um aviso nomeando a questão: anote qual.

## 3. Puxar antes de sair da sessão

**Sair da sessão e a revogação do vínculo apagam `files/corpus/`** (a amostra é cópia), e o prazo apaga o que passa de
30 dias. Puxe antes:

    node tools/corpus/puxar.mjs C:\caminho\fora\do\repositorio\corpus-de-medicao

O script recusa destino dentro do repositório, puxa por `adb`/`run-as`, e confere que cada foto tem o seu arquivo de
dados e vice-versa. `exit 0` é íntegro; `1`, a conferência reprovou (leia as linhas `::error::`); `2`, não puxou.
Com mais de um aparelho: `ANDROID_SERIAL=<serial>`.

## 4. A referência, no computador

Para cada `<id>.png`, com a foto aberta, abra `<id>.json` e preencha:

- `"referencia"`: o texto correto da resposta, pelas **convenções do ADR-0022** (literal, `[ilegivel]`, sem corrigir o
  aluno).
- `"descartar": true` se houver **nome ou dado pessoal escrito** na resposta (e não transcreva).

Depois de editar, confira o formato e conte as válidas (o piso do ADR-0022 é 60):

    node tools/corpus/formato.mjs C:\caminho\fora\do\repositorio\corpus-de-medicao

Imprime `{"amostras":N,"validas":V,"descartadas":D,"semReferencia":S}`; `exit 1` se algum arquivo de dados perdeu uma
chave ou uma foto ficou sem o par.

## 5. Guardar

- **Fora do repositório.** `.gitignore` tem `/corpus-de-medicao/` como segunda rede, e não é a primeira.
- **Não enviar a terceiros nesta etapa.** Nenhuma foto vai a LLM, OCR em nuvem ou serviço de transcrição antes da bancada
  e do ADR dela (política v2.0 §6.3: o envio a provedor exige o contrato que proíbe treino com os dados).
- Registrar, para a cobertura: **quantas turmas, quais matérias, qual instrumento** (lápis ou caneta), **quantas
  amostras válidas** (com referência, sem descarte) e quantas falhas de cópia.

## 6. Quando parar

O corpus basta com **60 ou mais amostras válidas** (ADR-0022, item 2), lidas em `"validas"` da saída de
`node tools/corpus/formato.mjs`; a meta é 150. Abaixo disso a bancada orienta e não decide.

## 7. A sessão única de papel

A coleta de alunos **não depende da impressora** e pode ocorrer à parte. Este protocolo entra na sessão única de papel
(`docs/protocolo-medicao-impressa.md`) só como referência.
