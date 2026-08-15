## Why

A fatia 1 triplicou a superfície de build do projeto: primeiro módulo KMP com três alvos, dois módulos de app novos, Node no CI e um emulador Android. Antes de empilhar a fatia 1.5 em cima disso, vale saber o que já avisa que vai quebrar.

O levantamento foi feito (2026-08-15) e o resultado é melhor do que o esperado em um ponto e pior em outro.

**Melhor:** nenhum aviso de depreciação do Gradle vem do nosso código. Os três existentes são internos ao Android Gradle Plugin. Nenhum pacote npm está deprecado. Nenhuma mudança no ambiente local é necessária — JDK 21 e Node 24 atendem tudo que está na mesa.

**Pior:** a auditoria encontrou um defeito latente que não é depreciação nenhuma, e que ataca exatamente a garantia que a fatia 1 existe para dar. Ver `design.md`, D-A.1.

## What Changes

**Defeito de sombreamento na medição de texto (prioridade)**
- `String.codePoints()` em `TextMeasurer` é uma extensão privada sombreada por `java.lang.String.codePoints()` nos alvos JVM e Android. Só o alvo JS usa a implementação escrita aqui.
- Hoje os resultados coincidem — o golden bate byte a byte nos três alvos — mas por concordância entre duas implementações, não por haver uma só.

**Depreciações ativas, que avisam agora e quebram depois**
- Gradle 8.14 → 9.7.0. Os avisos "incompatible with Gradle 9.0" são do AGP, não nossos; subir o Gradle exige subir o AGP junto.
- AGP 8.10.1 → 9.3.1. Salto de major.
- Ações do GitHub em `v4`, rodando Node 20, que o GitHub está depreciando. O runner já força Node 24 e avisa.
- `sdkmanager` declara-se deprecado em favor do "Android CLI". Afeta só a instalação local do SDK, não o build.

**Defasagem sem depreciação, atualização opcional**
- Kotlin 2.2.0 → 2.4.10 · Ktor 3.2.0 → 3.5.2 · jOOQ 3.20.3 → 3.21.7
- React 18 → 19 · Vite 6 → 8 · vitest 2 → 4 · TypeScript 5.9 → 7.0 · `@vitejs/plugin-react` 4 → 6

## Capabilities

### New Capabilities
Nenhuma.

### Modified Capabilities
Nenhuma. Esta mudança não altera comportamento observável: se algum teste de `layout-engine` ou `print` mudar de resultado, a atualização está errada e deve ser revertida, não acomodada. Por isso `skip_specs: true`.

## Impact

**Alterado**
- `packages/domain/src/commonMain/.../TextMeasurer.kt` — renomear a extensão sombreada
- `gradle/wrapper/gradle-wrapper.properties`, `gradle/libs.versions.toml` — versões
- `.github/workflows/ci.yml` — majors das ações
- `apps/web/package.json` — dependências do front

**Dependências novas**: nenhuma. Só versões do que já existe.

**Ambiente local**: nenhuma mudança necessária. JDK 21 atende Gradle 9, AGP 9 e Kotlin 2.4; Node 24 atende Vite 8. Se isso mudar durante a execução, avisar antes de prosseguir.

**Explicitamente NÃO alterado**
- Nenhuma decisão arquitetural. Nada aqui exige ADR — e se exigir, a atualização parou de ser manutenção e vira outra mudança.
- Nenhuma geometria. O golden do `LayoutMap` não pode mudar; se mudar, é regressão.
