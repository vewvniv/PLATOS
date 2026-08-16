## 1. Base de comparação

- [ ] 1.1 Registrar os números atuais antes de tocar em qualquer coisa: paridade, fidelidade nos dois PDFs, contagem da suíte e o hash do golden. Resultado: valores anotados nesta tarefa — a fatia altera o golden de propósito, então a base precisa existir antes.

## 2. Conversão em `tools/math`

- [ ] 2.1 Criar `tools/math` com MathJax fixado por lock, convertendo LaTeX e MathML em SVG (D-1.5.2). Resultado: uma fórmula de entrada produz SVG determinístico, byte a byte igual entre execuções.
- [ ] 2.2 Derivar o raster a 600 dpi a partir do SVG e extrair largura e altura em micrômetros (D-1.5.1). Resultado: a mesma fórmula produz sempre os mesmos bytes de imagem e as mesmas dimensões.
- [ ] 2.3 Emitir o manifesto que a fixture consome — identificador, dimensões e caminho do raster. Resultado: entrada pura para o Layout Engine, sem que ele precise abrir imagem.
- [ ] 2.5 Embutir os rasters para os três alvos pelo mesmo caminho da fonte embarcada: task de build gerando código para o KMP e assets no módulo Android (D-1.5.5). Resultado: os dois renderizadores desenham comprovadamente os mesmos bytes, e não bytes que coincidem por configuração.
- [ ] 2.4 Verificar que a conversão é reprodutível: rodar duas vezes e comparar bytes. Resultado: saída idêntica, ou a causa da variação identificada e eliminada.

## 3. Domínio: entrada e caixa da fórmula

- [ ] 3.1 Estender a definição de prova para aceitar fórmula em bloco com dimensões declaradas e referência ao recurso (D-1.5.3). Resultado: entrada versionada em JSON, sem dependência de banco ou rede.
- [ ] 3.2 Estreitar a recusa de entrada não suportada: fórmula em linha, imagem de enunciado e discursiva continuam recusadas; fórmula em bloco passa. Resultado: cobre "Fórmula em linha ainda é recusada" e "Fórmula em bloco é aceita".
- [ ] 3.3 Recusar fórmula mais larga que a coluna, com erro identificável. Resultado: cobre "Fórmula mais larga que a coluna".
- [ ] 3.4 Incluir a fórmula no bloco indivisível da questão, entre enunciado e alternativas, com altura arredondada à grade de 3 mm. Resultado: cobre "Fórmula reserva espaço próprio", "Fórmula não é reescalada" e "Ordem dentro do bloco".
- [ ] 3.5 Testar que o layout não depende do conteúdo matemático, só das dimensões. Resultado: cobre "Layout não depende do conteúdo matemático".
- [ ] 3.6 Testar que a fórmula não se separa do enunciado na paginação. Resultado: cobre "Fórmula não se separa do enunciado".
- [ ] 3.7 Emitir a primitiva de imagem no `LayoutMap` para a fórmula posicionada. Resultado: o mapa declara posição, dimensões e referência do recurso.

## 4. Renderizadores

- [ ] 4.1 Desenhar `DrawImage` no renderizador web, a partir dos bytes referenciados, sem reamostrar nem reescalar (D-1.5.1). Resultado: cobre "Imagem ocupa a caixa declarada" no web.
- [ ] 4.2 Desenhar `DrawImage` no renderizador Android, com a mesma regra de conversão de unidade dos demais elementos. Resultado: cobre o mesmo cenário no Android.
- [ ] 4.3 Falhar explicitamente quando os bytes referenciados não estiverem disponíveis, nos dois renderizadores. Resultado: cobre "Bytes ausentes", sem documento parcial.
- [ ] 4.4 Confirmar que nenhum dos dois interpreta LaTeX, MathML ou SVG. Resultado: cobre "Renderizador não tipografa matemática", com a mesma evidência estrutural já usada para medição de texto.

## 5. Fixture e golden

- [ ] 5.1 Acrescentar à fixture de referência questões com fórmula, exercitando ao menos fração, raiz e somatório. Resultado: a folha de referência passa a ter matemática.
- [ ] 5.2 Regravar o golden do `LayoutMap` deliberadamente e registrar aqui o antes e o depois (D-1.5.4). Resultado: a mudança do golden fica auditável, e não confundível com regressão aceita por engano.

## 6. Verificação

- [ ] 6.1 Rodar a suíte completa nos três alvos. Resultado: golden novo estável byte a byte em JVM, Node e Android.
- [ ] 6.2 Medir fidelidade do documento nos dois PDFs, agora com fórmula. Resultado: dentro de 0,05 mm, comparado com a base da tarefa 1.1.
- [ ] 6.3 Medir paridade web × Android com fórmula na folha. Resultado: cobre "Fórmula equivalente entre renderizadores" dentro de 0,3 mm.
- [ ] 6.4 Provar que a verificação continua capaz de falhar: deslocar a caixa da fórmula de propósito e confirmar que paridade e fidelidade acusam, com o elemento e a distância. Resultado: as duas saem com código 1; reverter em seguida.
- [ ] 6.5 Registrar o peso dos rasters da fixture. Resultado: número anotado, para a fatia 2 decidir empacotamento com dado em vez de estimativa (§5).
- [ ] 6.6 Imprimir a folha com fórmula e conferir legibilidade a olho, seguindo o protocolo. Resultado: registrado se a fórmula a 600 dpi sai nítida na impressora medida no ADR-0001 — é o que confere a escolha de resolução no papel, e não só no argumento.
- [ ] 6.7 Atualizar `docs/cobertura-fatia-1.md` com os cenários novos. Resultado: nenhum cenário das duas specs sem verificação.
- [ ] 6.8 Registrar a fatia 1.6 — matemática em linha — como próxima da lista, com o gatilho de precisar existir antes da fatia 6. Resultado: o caso predominante das exatas não fica só numa nota de design.
