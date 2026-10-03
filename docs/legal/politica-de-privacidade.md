# POLÍTICA DE PRIVACIDADE E PROTEÇÃO DE DADOS PESSOAIS

**Última atualização:** [DATA]  
**Versão:** 2.0 (Alinhada à Arquitetura de IA e Calibração de OCR)  
**Vigência:** a partir de [DATA]

---

### AVISO PRELIMINAR SOBRE ESTE DOCUMENTO

Esta política foi redigida para uma plataforma que trata dados pessoais e acadêmicos de crianças e adolescentes. Os campos entre colchetes precisam ser preenchidos antes da publicação. Os prazos de retenção e as finalidades foram desenhados para viabilizar o funcionamento de modelos de Inteligência Artificial e OCR, em conformidade com a legislação aplicável.

---

## 1. QUEM SOMOS

**[RAZÃO SOCIAL COMPLETA]**, inscrita no CNPJ sob o nº **[CNPJ]**, com sede em **[ENDEREÇO COMPLETO]**, doravante denominada **"Plataforma"**, é a responsável pelo desenvolvimento e operação do **[NOME COMERCIAL DA PLATAFORMA]**, um sistema de elaboração, impressão, aplicação, leitura óptica e correção de avaliações escolares com apoio de Inteligência Artificial.

Esta política descreve como tratamos dados pessoais, em conformidade com a Lei Geral de Proteção de Dados Pessoais (Lei nº 13.709/2018 - LGPD), o Marco Civil da Internet (Lei nº 12.965/2014) e o Estatuto da Criança e do Adolescente (Lei nº 8.069/1990).

**Encarregado pelo Tratamento de Dados Pessoais (DPO)**
* **Nome:** [NOME DO ENCARREGADO]
* **E-mail:** [privacidade@dominio]
* **Endereço para correspondência:** [ENDEREÇO]

Todos os pedidos relativos a dados pessoais devem ser dirigidos ao canal acima. O prazo de resposta é de até 15 (quinze) dias, conforme o art. 19, II, da LGPD.

---

## 2. DEFINIÇÕES USADAS NESTE DOCUMENTO

* **Titular:** pessoa natural a quem se referem os dados pessoais (professores, administradores escolares, alunos e seus responsáveis legais).
* **Controlador:** quem toma as decisões sobre a finalidade e os meios do tratamento (Instituição de Ensino, Professor Autônomo ou a Plataforma, a depender do contexto).
* **Operador:** quem trata dados pessoais em nome do controlador e seguindo suas instruções.
* **Organização:** unidade de isolamento de dados dentro da plataforma ("Escola" ou "Pessoal").
* **Roster:** conjunto de dados de identificação direta do aluno (nome, turma, número de chamada, matrícula). É armazenado em estrutura isolada, alterável e eliminável.
* **Fato de Avaliação:** registro de desempenho quantitativo `(aluno, habilidade BNCC, avaliação, pontuação, período)`.
* **Recorte de Resposta:** imagem da área delimitada de resposta discursiva capturada pelo dispositivo, contendo o manuscrito do aluno.
* **Corpus de Medição / Calibração:** conjunto de imagens de manuscritos e transcrições desidentificadas ou anonimizadas, utilizado para medição experimental, calibração do limiar de confiança e testes de acurácia de modelos de visões/OCR.

---

## 3. PAPÉIS: QUEM É CONTROLADOR E QUEM É OPERADOR

### 3.1. Dados dos usuários da plataforma (Professores e Administradores)
A Plataforma atua como **CONTROLADORA** dos dados cadastrais, de autenticação, uso, cobrança e suporte dos próprios professores e gestores.

### 3.2. Dados de alunos — Modalidade Escola
A **INSTITUIÇÃO DE ENSINO** é a **CONTROLADORA** dos dados dos alunos. A Plataforma atua exclusivamente como **OPERADORA**, tratando esses dados conforme as instruções documentadas da instituição e nos limites do instrumento contratual aplicável.

### 3.3. Dados de alunos — Modalidade Autoatendimento (Planos Individual/Pro)
* **Professor vinculado a escola:** A Instituição de Ensino permanece CONTROLADORA dos dados. O professor declara possuir autorização institucional para o uso da ferramenta. A Plataforma atua como OPERADORA.
* **Professor autônomo:** O PRÓPRIO PROFESSOR é o CONTROLADOR dos dados dos seus alunos, assumindo as obrigações previstas na LGPD. A Plataforma atua como OPERADORA.

### 3.4. Modo sem Identificação Nominal
A plataforma oferece o **Modo sem Identificação Nominal**: o aluno é identificado apenas por código, número de chamada ou apelido. O Roster nominal é omitido, minimizando drasticamente a retenção de dados pessoais diretos.

---

## 4. CONTRATO DE OPERADOR COM A INSTITUIÇÃO DE ENSINO

Nos termos do art. 39 da LGPD, a Plataforma trata dados de alunos em nome das instituições mediante instrumento contratual específico que disciplina:

1. A limitação do tratamento às instruções documentadas do Controlador;
2. As obrigações de segurança, confidencialidade e notificação de incidentes (em até 48 horas);
3. A autorização para subprocessamento necessário à execução do serviço (provedores de infraestrutura e IA) sob as mesmas garantias contratadas;
4. O uso de dados desidentificados para calibração, auditoria e medição de acurácia dos sistemas de correção por IA/OCR no interesse da precisão pedagógica.

---

## 5. DADOS PESSOAIS QUE TRATAMOS

### 5.1. Dados de Professores e Administradores
* **Cadastrais:** nome completo, e-mail, *hash* de senha, telefone, disciplina e etapa de ensino.
* **Uso e Pagamento:** endereço IP, registros de acesso, históricos de assinatura e faturamento (dados bancários/cartão processados diretamente via gateway de pagamento).
* **Conteúdo Autoral:** questões, provas, gabaritos, rubricas.

### 5.2. Dados de Alunos
* **Identificação (Roster):** nome, turma, número de chamada, matrícula (quando informados).
* **Desempenho:** notas, pontuações por habilidade da BNCC, histórico de correções.
* **Respostas:** marcações de leitura óptica (processadas localmente no dispositivo) e recortes de imagem de respostas discursivas (manuscrito).
* **Metadados de IA:** versão do *prompt*, identificador de modelo, contagem de *tokens*, parâmetros e pontuações de confiança de OCR.

Não coletamos intencionalmente dados sensíveis, biometria facial, endereço ou geolocalização de alunos.

---

## 6. TRATAMENTO DE DADOS DE CRIANÇAS E ADOLESCENTES E USO DE IA

### 6.1. Princípio do Melhor Interesse
Todo o tratamento é realizado em observância ao melhor interesse do titular (art. 14 da LGPD e art. 100 do ECA).

### 6.2. Bases Legais
* **Crianças (menores de 12 anos):** Consentimento específico obtido pelo Controlador (escola/professor) ou execução de contrato/procedimentos necessários para a prestação do serviço educacional.
* **Adolescentes (12 a 17 anos):** Execução de contrato ou legítimo interesse do Controlador e da Operadora para a prestação da atividade educacional regular, avaliação pedagógica e melhoria da acurácia das correções.

### 6.3. Uso de Dados para Calibração, Medição de OCR e Modelos de IA
Para garantir a precisão pedagógica, atribuição justa de notas e funcionamento adequado dos sistemas de Inteligência Artificial e OCR, adotam-se as seguintes diretrizes:

* **Corpus de Medição e Calibração (Fatia 5):** A plataforma poderá utilizar imagens de recortes manuscritos de respostas discursivas e suas respectivas transcrições em um ambiente de medição experimental. As imagens utilizadas para este fim são estritamente desvinculadas de qualquer dado de identificação do aluno (sem Roster, nome ou matrícula), destinando-se exclusivamente ao teste de acurácia de escrita, ajuste de limiares de confiança e comparação entre modelos de OCR/Visão.
* **Vedação ao Treinamento Público por Terceiros:** Fica expressamente vedado o envio de dados de alunos para treinamento de modelos comerciais públicos de terceiros. Os provedores de API de IA contratados pela Plataforma estão sujeitos a cláusulas contratuais de **Zero Data Retention (ZDR)** ou retenção restrita à execução da chamada, não utilizando os recortes para treinar seus próprios modelos.
* **Minimização Geométrica:** A captura da resposta discursiva restringe-se à área da pauta/caixa de resposta, excluindo do recorte o cabeçalho do documento contendo o nome ou identificação do aluno.

---

## 7. FINALIDADES E BASES LEGAIS

1. **Prestação do Serviço de Avaliação Escolar:** Leitura óptica, geração de relatórios e correção assistida por IA (*Execução de Contrato / Legítimo Interesse*).
2. **Calibração e Auditoria de Acurácia do Pipeline de IA:** Medição experimental de manuscritos para calibração do limiar de confiança do OCR e avaliação de custo-eficiência (*Legítimo Interesse / Desenvolvimento Tecnológico com dados desidentificados*).
3. **Auditoria de Contestação de Nota:** Conservação de metadados de chamadas de IA e justificativas para garantir rastreabilidade e revisão (*Cumprimento de Obrigação Legal / Exercício Regular de Direitos*).
4. **Guarda de Registros de Acesso:** Manutenção de logs nos termos do art. 15 do Marco Civil da Internet (*Obrigação Legal*).

---

## 8. COMPARTILHAMENTO E SUBOPERADORES

O compartilhamento de dados ocorre unicamente para viabilizar a execução da infraestrutura tecnológica da Plataforma.

### Relação de Suboperadores Homologados:
* **Banco de Dados e Armazenamento:** Provedor de Nuvem [Ex: Supabase Inc.] — armazenamento encriptado de banco de dados e arquivos.
* **Hospedagem de Aplicação:** Provedor de Servidor [Ex: Render / AWS] — execução do backend e API Gateway.
* **Processamento de Inteligência Artificial:** Provedor de API de LLM / Visão [Ex: OpenAI / Anthropic / Google Vertex AI] — processamento dos recortes de resposta discursiva sob contratos que proíbem o treino de modelos com os dados enviados.
* **Monitoramento de Erros:** Provedor de Logs [Ex: Sentry] — filtrado para não capturar imagens ou conteúdos de alunos.

---

## 9. TRANSFERÊNCIA INTERNACIONAL DE DADOS

O processamento de imagens por APIs de Inteligência Artificial e a hospedagem em nuvem podem envolver a transferência internacional de dados para servidores localizados nos Estados Unidos ou União Europeia.

Tais transferências observam o art. 33 da LGPD e utilizam mecanismos de salvaguarda adequados (como Cláusulas-Padrão Contratuais - STCs), garantindo que os suboperadores internacionais sigam padrões rígidos de confidencialidade e segurança equivalentes aos da LGPD.

---

## 10. RETENÇÃO E ELIMINAÇÃO DE DADOS

Os prazos de retenção são categorizados conforme a natureza do dado e sua necessidade técnica e legal:

| Classe | Categoria de Dado | Descrição | Prazo de Retenção | Destino / Trata-se de |
| :--- | :--- | :--- | :--- | :--- |
| **Classe A** | **Imagem de Manuscrito & Transcrição** | Recortes de respostas discursivas vinculadas a uma avaliação ativa. | Até o fim do ano letivo + 180 dias (janela de contestação). | **Eliminação Definitiva.** Imagens desidentificadas integradas ao *Corpus de Medição* para testes de OCR são mantidas sob anonimização técnica. |
| **Classe B** | **Fato de Avaliação & Nota** | Notas, pontuações por habilidade BNCC e histórico de correções. | Durante o vínculo do aluno + 5 anos. | **Anonimização Irreversível** após o prazo (desvinculação do aluno). |
| **Classe C** | **Roster (Identificação)** | Nome, turma, matrícula e número de chamada. | Durante o vínculo com a instituição + 12 meses. | **Eliminação Definitiva** via comando direto do sistema. |
| **Classe D** | **Dados da Conta do Professor** | Cadastro, dados fiscais e de cobrança. | Vigência do contrato + 5 anos (obrigação fiscal). | **Eliminação / Guarda Fiscal.** |
| **Classe E** | **Logs de Acesso** | IP, data, hora e conexões. | 6 meses (Art. 15 do Marco Civil da Internet). | **Eliminação Automática.** |
| **Classe F** | **Metadados de Chamadas de IA** | Prompts, versão do modelo, limiar de confiança, tokens e latência. | 24 meses. | **Guarda para auditoria e calibração.** |

---

## 11. DECISÕES AUTOMATIZADAS E CORREÇÃO POR IA

A plataforma utiliza IA para apoiar a correção de respostas discursivas sob as seguintes garantias obrigatórias:

1. **Revisão Humana Obrigatória:** Toda sugestão de nota emitida pela IA é submetida ao professor. Nenhuma nota é atribuída de forma 100% automatizada sem a intervenção ou validação do docente.
2. **Prevalência da Decisão Humana:** O professor tem total autonomia para alterar, anular ou sobrescrever a nota sugerida pela IA.
3. **Sinalização de Limiar de Confiança:** Caso a acurácia de leitura da escrita do aluno esteja abaixo do limiar de segurança estabelecido na calibração do sistema, a plataforma sinalizará a resposta para **revisão manual prioritária**.
4. **Direito à Explicação:** Professores, alunos e responsáveis podem solicitar os critérios e parâmetros utilizados na correção (rubrica analítica e metadados de avaliação).

---

## 12. SEGURANÇA DA INFORMAÇÃO

Adotamos medidas técnicas e administrativas avançadas para a proteção dos dados:

* **Isolamento Lógico em Nível de Linha (RLS):** Garantia de que cada instituição ou professor acesse estritamente os seus próprios dados.
* **Criptografia em Trânsito e Repouso:** Comunicações via protocolo TLS e armazenamento encriptado no banco de dados.
* **Privacidade desde a Concepção (Privacy by Design):** Processamento local no dispositivo para questões objetivas; isolamento do Roster nominal em relação às imagens de respostas discursivas sent para a IA.

---

## 13. DIREITOS DO TITULAR

Os titulares (ou seus responsáveis legais) possuem os direitos previstos no art. 18 da LGPD (confirmação, acesso, correção, eliminação, portabilidade e revogação de consentimento).

* **Alunos de Escolas:** As solicitações devem ser encaminhadas diretamente à **Instituição de Ensino** (Controladora). A Plataforma prestará o suporte técnico ao estabelecimento para a execução do pedido.
* **Usuários Diretos (Professores):** Pedidos podem ser feitos diretamente ao Encarregado de Dados pelo e-mail `[privacidade@dominio]`.

---

## 14. ALTERAÇÕES DESTA POLÍTICA

Esta Política poderá ser atualizada periodicamente para refletir evoluções tecnológicas ou regulatórias. Mudanças substanciais nas práticas de tratamento de dados serão notificadas aos usuários com antecedência mínima de 30 (trinta) dias.

---

## 15. CONTATO DO ENCARREGADO (DPO)

Para esclarecimentos sobre esta política ou sobre o tratamento de dados pessoais na plataforma:

* **Encarregado pelo Tratamento de Dados Pessoais:** [NOME DO ENCARREGADO]
* **E-mail de Contato:** `[privacidade@dominio]`
* **Endereço:** [ENDEREÇO COMPLETO]