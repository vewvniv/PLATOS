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

**Nota sobre as três linhas `5` (resolvida).** Na medição original, esta árvore era `origin/main` no
commit `9be6356` (merge do PR #74), que ainda não incluía a reconciliação do §16 feita ao arquivar a
`5b-2` — essa reconciliação estava no PR #75, aberto e não mergeado no momento da medição. As três
linhas `5` (`Acurácia em manuscrito`, `Modo degradado`, `O limiar do OMR`) apareciam de novo em
"vence nesta fatia" por isso, e não por dívida nova desta mudança (`exit 0`, "nenhuma linha vencida").
O PR #75 foi mergeado em `30c729d`, e esta branch foi rebaseada sobre o `main` atualizado antes da
task 1.1: `node tools/divida/divida.mjs` volta a dizer "vence nesta fatia (5b): nenhuma", e
`openspec validate --strict` não reporta mais o `MODIFIED` sem cabeçalho correspondente na spec
principal.
