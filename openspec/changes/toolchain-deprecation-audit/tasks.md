## 1. Defeito de sombreamento (antes de qualquer atualização)

- [x] 1.1 Renomear a extensão `String.codePoints()` de `TextMeasurer` para um nome que nada sombreie, eliminando o aviso do compilador (D-A.1). Resultado: `./gradlew build` sem o aviso `This extension is shadowed by a member`.
- [x] 1.2 Testar a medição de texto com surrogate solto e com par de surrogates bem formado, afirmando o mesmo resultado nos três alvos. Resultado: o caminho único de contagem de code points fica coberto por teste, e não apenas pelo corpus da fixture.
  - `CodePointMeasurementTest`, 5 testes nos três alvos: par bem formado contando como um único code point, surrogate alto solto, surrogate baixo solto, par invertido e code point fora do BMP caindo em `.notdef`.
  - **O teste foi escrito e executado antes da correção, de propósito, e o resultado mudou o diagnóstico:** as duas implementações concordavam em todos os casos, inclusive nos de surrogate. O sombreamento era risco latente, não defeito ativo — nenhuma folha já impressa está errada por causa dele.
  - As três falhas que apareceram nessa primeira execução eram do próprio teste: eu assumi que duas larguras medidas em separado somam a largura do texto inteiro, o que é falso porque a conversão arredonda uma vez sobre o total. A diferença era de 1 µm (`4290` contra `4289`) — exatamente o mesmo engano que já estava documentado em `TextMeasurerTest` e que eu repeti.
- [x] 1.3 Confirmar que o golden do `LayoutMap` não mudou. Resultado: `GoldenLayoutTest` verde nos três alvos sem regravar o golden — se ele mudar, a extensão sombreada estava produzindo geometria diferente e isso vira achado próprio.

## 2. Base de comparação

- [x] 2.1 Registrar os números de referência antes de atualizar qualquer coisa: desvio máximo da fidelidade nos dois PDFs e maior divergência da paridade. Resultado: valores anotados nesta tarefa, para comparar depois de cada passo.

  | Medida | Valor de referência | Tolerância |
  |---|---|---|
  | Paridade web × Android | **0,041 mm** em `r0-m1`, 116 de 116 elementos | 0,3 mm |
  | Fidelidade do documento web | **0,039 mm** em `marcador 2: borda superior`, 32 verificações | 0,05 mm |
  | Fidelidade do documento Android | **0,017 mm** em `bolha r0-bq01-C: diametro externo`, 32 verificações | 0,05 mm |
  | Suíte | 98 JVM · 94 JS · 94 Android debug · 6 `apps/android` · 69 `apps/api` · 7 web | 0 falhas |
  | Golden do `LayoutMap` | inalterado | byte a byte |

  Os três primeiros valores já foram reproduzidos em quatro execuções independentes — local e três no CI, em máquinas e sistemas diferentes — sempre na mesma terceira casa. É contra eles que Gradle e AGP serão julgados: qualquer desvio é regressão, não ajuste.

## 3. Gradle e AGP

- [x] 3.1 Subir o wrapper do Gradle de 8.14 para 9.x e o AGP de 8.10.1 para 9.x no mesmo commit (D-A.2). Resultado: `./gradlew build` verde e os três avisos de `is-` property desaparecidos.
- [x] 3.2 Rodar a tríade de validação (D-A.3): build nos três alvos, fidelidade a 0,05 mm e paridade a 0,3 mm. Resultado: mesmos números da tarefa 2.1.
- [x] 3.3 Rodar o teste instrumentado no emulador. Resultado: dois testes verdes e PDF do Android gerado.
- [x] 3.4 Se o AGP 9 não fechar, reverter e registrar o motivo aqui. Resultado: decisão documentada; ficar no AGP 8.10 é aceitável enquanto os avisos forem informativos.
  - Não foi preciso reverter, mas o caminho até fechar teve três paradas que valem registro. AGP 9 traz Kotlin embutido e registra ele próprio a extensão `kotlin`, então `org.jetbrains.kotlin.android` aplicado à mão passou a colidir e saiu de `apps/android`. Em seguida, AGP 9 recusa `com.android.library` junto de `kotlin.multiplatform`, e a saída recomendada pelo próprio AGP é o plugin `com.android.kotlin.multiplatform.library`, com `androidLibrary {}` dentro do bloco `kotlin` no lugar do `android {}` de topo. Por fim, o lock do Yarn do alvo JS precisou ser regravado.
  - Também foi testado Gradle 9.7 com AGP 8.13.2, para evitar a migração de DSL: falha ao criar serviço interno do AGP. A matriz não fecha por esse lado, então Gradle 9 exige mesmo AGP 9.

## 4. Ações do GitHub

- [x] 4.1 Subir as majors em bloco (D-A.4): checkout v7, setup-java v5, setup-node v7, upload-artifact v7, download-artifact v8, gradle/actions v6. Resultado: pipeline verde e sem aviso de Node 20 depreciado.
- [ ] 4.2 Confirmar que o job de paridade continua colhendo o PDF do Android pelo `additionalTestOutputDir` e que o passo de liberar disco segue necessário. Resultado: job `paridade` verde no PR.

## 5. Decisões adiadas

- [ ] 5.1 Avaliar Kotlin 2.2 → 2.4 depois de Gradle e AGP estabilizarem. Resultado: decisão registrada — atualizar agora ou fixar prazo.
- [ ] 5.2 Avaliar o front (React 19, Vite 8, vitest 4) como bloco separado. Resultado: decisão registrada.
- [ ] 5.3 Avaliar TypeScript 7 isoladamente, por ser reescrita de compilador. Resultado: decisão registrada.

## 6. Verificação final

- [ ] 6.1 Rodar a suíte completa e confirmar que nenhum número mudou em relação à tarefa 2.1, e que `docs/cobertura-fatia-1.md` continua válido. Resultado: atualização de toolchain sem efeito observável, que é o único desfecho aceitável.
