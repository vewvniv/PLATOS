## 1. Defeito de sombreamento (antes de qualquer atualização)

- [ ] 1.1 Renomear a extensão `String.codePoints()` de `TextMeasurer` para um nome que nada sombreie, eliminando o aviso do compilador (D-A.1). Resultado: `./gradlew build` sem o aviso `This extension is shadowed by a member`.
- [ ] 1.2 Testar a medição de texto com surrogate solto e com par de surrogates bem formado, afirmando o mesmo resultado nos três alvos. Resultado: o caminho único de contagem de code points fica coberto por teste, e não apenas pelo corpus da fixture.
- [ ] 1.3 Confirmar que o golden do `LayoutMap` não mudou. Resultado: `GoldenLayoutTest` verde nos três alvos sem regravar o golden — se ele mudar, a extensão sombreada estava produzindo geometria diferente e isso vira achado próprio.

## 2. Base de comparação

- [ ] 2.1 Registrar os números de referência antes de atualizar qualquer coisa: desvio máximo da fidelidade nos dois PDFs e maior divergência da paridade. Resultado: valores anotados nesta tarefa, para comparar depois de cada passo.

## 3. Gradle e AGP

- [ ] 3.1 Subir o wrapper do Gradle de 8.14 para 9.x e o AGP de 8.10.1 para 9.x no mesmo commit (D-A.2). Resultado: `./gradlew build` verde e os três avisos de `is-` property desaparecidos.
- [ ] 3.2 Rodar a tríade de validação (D-A.3): build nos três alvos, fidelidade a 0,05 mm e paridade a 0,3 mm. Resultado: mesmos números da tarefa 2.1.
- [ ] 3.3 Rodar o teste instrumentado no emulador. Resultado: dois testes verdes e PDF do Android gerado.
- [ ] 3.4 Se o AGP 9 não fechar, reverter e registrar o motivo aqui. Resultado: decisão documentada; ficar no AGP 8.10 é aceitável enquanto os avisos forem informativos.

## 4. Ações do GitHub

- [ ] 4.1 Subir as majors em bloco (D-A.4): checkout v7, setup-java v5, setup-node v7, upload-artifact v7, download-artifact v8, gradle/actions v6. Resultado: pipeline verde e sem aviso de Node 20 depreciado.
- [ ] 4.2 Confirmar que o job de paridade continua colhendo o PDF do Android pelo `additionalTestOutputDir` e que o passo de liberar disco segue necessário. Resultado: job `paridade` verde no PR.

## 5. Decisões adiadas

- [ ] 5.1 Avaliar Kotlin 2.2 → 2.4 depois de Gradle e AGP estabilizarem. Resultado: decisão registrada — atualizar agora ou fixar prazo.
- [ ] 5.2 Avaliar o front (React 19, Vite 8, vitest 4) como bloco separado. Resultado: decisão registrada.
- [ ] 5.3 Avaliar TypeScript 7 isoladamente, por ser reescrita de compilador. Resultado: decisão registrada.

## 6. Verificação final

- [ ] 6.1 Rodar a suíte completa e confirmar que nenhum número mudou em relação à tarefa 2.1, e que `docs/cobertura-fatia-1.md` continua válido. Resultado: atualização de toolchain sem efeito observável, que é o único desfecho aceitável.
