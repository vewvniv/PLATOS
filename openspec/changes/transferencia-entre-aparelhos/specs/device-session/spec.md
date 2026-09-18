## MODIFIED Requirements

### Requirement: A credencial guardada não fica legível no aparelho

A credencial de sessão guardada no aparelho SHALL estar cifrada em repouso, e SHALL NOT ser recuperável pela leitura direta do armazenamento.

A organização escolhida não é credencial e não exige cifragem.

**Nada do que o aparelho guarda SHALL sair dele por cópia automática do sistema operacional.** Isso
vale para a credencial, a organização escolhida, a última visão conhecida, os pacotes guardados, os
rosters guardados e os resultados pendentes, e vale nos **dois** caminhos de cópia automática: o
**backup** do aparelho e a **transferência entre aparelhos**.

Os dois caminhos são nomeados separadamente, e não cobertos por "backup" como palavra guarda-chuva,
porque **o sistema operacional não os trata como o mesmo caminho**. Desligar o backup deixou de
desligar a transferência, e a diferença só apareceu porque foi medida: sob o transporte de
transferência o aparelho admitiu para cópia exatamente o roster e a fila de pendentes que o
transporte de backup recusava. Requisito que diz "backup automático" é fácil de dar como cumprido por
quem leu o atributo que desliga o backup — e foi o que aconteceu.

O que a regra protege é diferente em cada item, e vale dizer qual é o pior. A credencial cifrada
chegaria ilegível a outro aparelho, porque a chave que a decifra não acompanha a cópia; o pacote é
público dentro da organização e endereçado por conteúdo. **O roster é nome de aluno, e num piloto em
modo `nominal` é nome civil de menor** — uma cópia dele para um aparelho que a organização não
conhece é um caminho pelo qual o direito de eliminação deixa de alcançar, e é o mesmo caminho que
`ARQUITETURA-FINAL-v3.md` §16 já registra sob a classe H. **O resultado pendente é correção já feita
que ainda não existe em nenhum outro lugar**, e uma cópia dele é uma nota de aluno fora de qualquer
fronteira de organização.

A regra SHALL ser **negação por domínio de armazenamento**, e SHALL cobrir **todo** o diretório de
dados do aplicativo: os domínios de arquivos, bancos e preferências, onde vivem os dados nomeados
acima, **e a raiz do diretório de dados**, onde nasce tudo o que ainda não tem domínio próprio.

Negar só o domínio de arquivos deixaria a fila de pendentes passando, porque ela não é arquivo
comum: é banco, e mora noutra árvore. E negar apenas os três domínios dos dados nomeados deixaria
passar qualquer diretório que o aplicativo crie direto na raiz — o que **foi medido acontecendo**, com
os três já negados. A distinção não é detalhe de implementação: é onde a regra falha em silêncio se
for escrita pela metade, e a primeira tentativa falhou exatamente assim.

#### Scenario: A credencial não está em claro
- **WHEN** a sessão é guardada e o armazenamento do aplicativo é lido diretamente
- **THEN** o token não aparece como texto legível em lugar nenhum do que foi gravado

#### Scenario: A credencial não sai no backup
- **WHEN** o aparelho executa backup automático
- **THEN** nem a credencial nem a organização escolhida são incluídas

#### Scenario: O dado guardado não sai na transferência entre aparelhos
- **WHEN** o aparelho executa uma transferência entre aparelhos, com roster guardado, resultado
  pendente na fila e credencial gravada
- **THEN** o aplicativo é recusado pela transferência, ou o conteúdo transferido NÃO contém o roster,
  nem a fila de pendentes, nem a credencial

#### Scenario: A recusa vale para todo o diretório de dados
- **WHEN** a transferência entre aparelhos é executada com dado do aplicativo em arquivos, em banco
  local, em preferências e num diretório criado direto na raiz do diretório de dados
- **THEN** nenhuma das quatro árvores é incluída, e a ausência de uma delas NÃO é suficiente para dar
  a regra por cumprida
