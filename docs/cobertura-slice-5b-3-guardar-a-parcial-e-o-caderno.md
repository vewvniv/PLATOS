# Cobertura — `slice-5b-3-guardar-a-parcial-e-o-caderno`

## 0. Antes do primeiro commit

### 0.1 — linha de base em `origin/main` (9be6356)

Janela: 2026-09-26T21:06Z – 2026-09-26T23:13Z.

| Task | Comando | Resultado | Testes |
|---|---|---|---|
| `./gradlew build --rerun-tasks` | build completo, `packages:domain` incluído | `BUILD SUCCESSFUL` em 3m52s | `packages:domain:testAndroidHostTest`: 399 métodos com `@Test`, todos com resultado no relatório |
| `./gradlew -p buildSrc test --rerun-tasks` | testes do `buildSrc` | `BUILD SUCCESSFUL` em 25s | suíte do `buildSrc` |
| `./gradlew :apps:android:connectedDebugAndroidTest` (sem filtro, `platos-atd34`) | instrumentado no emulador | `BUILD SUCCESSFUL` em 1m01s | 91 testes, 2 pulados propositalmente (`AcumuloDeInstanciasProbe`), 0 falhas |
| `npx vitest run` (`apps/web`) | testes do web | 2 arquivos, `PASSED` | 18 testes |

Nenhuma falha na baseline.

### 0.2 — `node tools/divida/divida.mjs`

```
fatia corrente: 5b, de slice-5b-2-a-nota-objetiva-parcial (ativa), slice-5b-3-guardar-a-parcial-e-o-caderno (ativa), slice-5b-0-a-regiao-discursiva-compacta (arquivada), slice-5b-1-o-aparelho-reconhece-a-discursiva (arquivada)
...
vence nesta fatia (5b):
  Acurácia em manuscrito (`5`)
  Modo degradado (§10) não existe (`5`)
  O limiar do OMR foi apurado sobre um aparelho e uma impressora (`5`)

nenhuma linha vencida: 21 linhas lidas
exit 0
```

**Nota sobre as três linhas `5`.** Esta árvore é `origin/main` no commit `9be6356` (merge do PR #74), que
**não** inclui a reconciliação do §16 feita ao arquivar a `5b-2` — essa reconciliação está no PR #75
(`vewvniv/archive-slice-5b-2-a-nota-objetiva-parcial`), aberto e ainda não mergeado no momento desta
medição. Nele, as três linhas `5` (`Acurácia em manuscrito`, `Modo degradado`, `O limiar do OMR`) já
foram reagendadas para a fatia `6`. A reaparição delas aqui é um artefato de ter separado o archive em
sua própria branch/PR, e não dívida nova desta mudança: `exit 0` e "nenhuma linha vencida" (vencida ≠
vence nesta fatia) satisfazem a verificação da 0.2. Elas devem sair da lista de "vence nesta fatia"
depois que o PR #75 for mergeado e esta branch atualizada a partir de `main`.

`slice-5b-2-a-nota-objetiva-parcial` aparece como "ativa" pela mesma razão: o `mv` para
`openspec/changes/archive/` também está no PR #75.
