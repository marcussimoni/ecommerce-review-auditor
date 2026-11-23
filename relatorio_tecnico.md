# Relatório do Processo de Desenvolvimento

## Visão Geral

Este relatório apresenta o passo a passo da evolução do projeto em alto nível, com extensão adequada a uma leitura executiva. O objetivo é registrar a sequência de etapas, os aprendizados obtidos durante os testes e as decisões de escopo que orientaram o desenvolvimento, sem entrar em detalhes de código-fonte, estruturas de dados ou manuais de instalação.

Ao longo do processo, o trabalho partiu de um fluxo inicial voltado à triagem de avaliações de e-commerce e amadureceu por meio de ciclos curtos de experimentação e validação. Cada rodada de testes revelou nuances importantes sobre como o modelo interpreta relatos mistos, diferencia falhas de produto de fatores operacionais externos e lida com entradas maliciosas embutidas no texto das avaliações.

---

## Etapa 1 — Alinhamento do Escopo e Definição da Abordagem

A primeira etapa consistiu em consolidar o problema de negócio e o escopo do agente: distinguir avaliações desfavoráveis originadas por **fatores externos** ao produto (como atrasos de entrega, problemas com transportadora, avarias externas com produto íntegro ou falhas de atendimento do vendedor) daquelas causadas por **falhas ou características intrínsecas do produto**.

Nesse momento, definiu-se também uma arquitetura enxuta e transparente, adequada ao contexto acadêmico e à operação local:

- **Usuário-alvo:** Times de operações, qualidade, logística e gestão de *sellers*/parceiros de um e-commerce.
- **Dor:** Avaliações ruins nem sempre significam que o produto é ruim. Atrasos de entrega, mau atendimento do vendedor ou avarias apenas na caixa (com o produto funcionando perfeitamente) distorcem a percepção do produto e podem levar a decisões equivocadas, como pausar um bom item ou culpar o fabricante.
- **Por que usar IA generativa:** Avaliações trazem linguagem livre, ironia, erros de escrita e relatos mistos (ex.: elogio ao produto + reclamação da transportadora). Regras fixas ou palavras-chave têm dificuldade com contexto e ambiguidade; um modelo de linguagem com instruções claras e exemplos consegue interpretar a intenção e classificar quando há necessidade de revisão operacional.
- **Objetivo de negócio:** Classificar avaliações que exigem revisão por fatores externos e gerar um relatório estruturado no banco de dados — associando explicitamente os **IDs das avaliações divergentes**, motivos e detalhes — para apoiar decisões do e-commerce (como notificar transportadora/parceiro, rever embalagem ou avaliar parcerias).
- **Métrica de sucesso:**
  - Taxa de conformidade do **JSON estruturado** (`response_format` com `json_schema`) retornado pelo Ollama via `/v1/chat/completions`.
  - Taxa de acerto na **classificação esperada** (`precisaRevisao`), na identificação das **avaliações divergentes** e na persistência condicional do relatório no H2.

### Arquitetura, Decisões Técnicas e Ajustes Realizados

- **Java puro + JDBC + H2 em memória + Flyway:** Eliminou-se o uso de Spring Boot e ORM para manter o código enxuto, explícito e fácil de auditar. As tabelas e cargas iniciais são gerenciadas por migrações (`V1__create_tables.sql`, `V2__seed_avaliacoes.sql` com 5 produtos `PROD-001` a `PROD-005` e `V3__add_avaliacoes_divergentes_relatorio.sql` para armazenar IDs e detalhes das avaliações divergentes).
- **Decisão de utilizar RAG estruturado (ou determinístico via SQL/JDBC)**, a aplicação Java (`AvaliacaoJdbcService`) consulta diretamente as avaliações por `produto_id` via JDBC, injeta os dados no prompt e envia ao endpoint `/v1/chat/completions` do Ollama com `response_format` em JSON Schema estrito. A persistência do relatório (`RelatorioJdbcService`) é executada de forma determinística pelo código Java quando há fator externo real.
- **Uso de Temperatura Baixa (`0.1`) para Previsibilidade:** Para reduzir a variabilidade inerente aos modelos de linguagem e garantir que a classificação de fatores externos e o preenchimento do schema JSON sigam um comportamento estrito e reproduzível entre diferentes execuções, adotou-se uma temperatura baixa (`temperature: 0.1`) na configuração da API.
- **Rastreabilidade de avaliações divergentes no relatório:** O schema de saída e a tabela `relatorios_revisao` registram não apenas a contagem de avaliações externas, mas também quais avaliações específicas foram apontadas (`avaliacoesDivergentes` / `avaliacoes_divergentes_ids` / `detalhes_divergencias`).
- **Consolidação determinística de múltiplos motivos no Java:** Quando uma análise identifica mais de um fator externo distinto entre as avaliações divergentes (ex.: `ATENDIMENTO_VENDEDOR` e `LOGISTICA_TRANSPORTADORA` no `PROD-015`), o código Java consolida os motivos distintos a partir de `avaliacoesDivergentes` e aplica uma trava determinística (`filtrarFatoresExternosReais` e `precisaRevisaoReal`) para impedir que defeitos intrínsecos do produto (`PRODUTO`, `DEFEITO_PRODUTO`) ou `NENHUM` disparem revisão por fator externo.
- **Interação via terminal (CLI):** Interface simples em linha de comando (`Main.java`) adequada ao escopo acadêmico e à execução local.

---

## Etapa 2 — Preparação dos Cenários de Avaliação

Para testar o comportamento do agente de forma consistente, estruturou-se uma massa de dados simulada composta por **15 produtos** com múltiplas avaliações cada. A escolha de cenários variados foi fundamental para evitar uma avaliação enviesada apenas por casos simples.

Os conjuntos de avaliações foram desenhados para contemplar situações recorrentes no e-commerce:

- Produtos com avaliações majoritariamente positivas, mas com relatos pontuais de falhas operacionais ou de atendimento.
- Produtos com problemas reais de qualidade ou desempenho do próprio item, sem relação com entrega ou atendimento.
- Cenários mistos, combinando elogios ao produto, reclamações de fatores externos e tentativas de manipulação textual (*prompt injection*) dentro das avaliações.

---

## Etapa 3 — Iteração e Versionamento do System Prompt

Com a base de cenários definida, o *system prompt* passou a ser versionado em arquivos separados em `src/main/resources/prompts/` (`system-prompt-v1.txt`, `system-prompt-v2.txt`, `system-prompt-v3.txt`), permitindo comparar o impacto de cada ajuste sem perder o histórico das mudanças.

### Técnicas aplicadas no `system prompt`

- **Instrução de papel e critério de separação:** diferencia problemas intrínsecos ao produto (defeito de fabricação, bateria, zíper, costura, dimensões) de fatores externos (logística/entrega, embalagem danificada com item íntegro, atendimento do vendedor).
- **Few-shot prompting:** exemplos de avaliações coerentes positivas, defeitos/características do próprio produto e avaliações divergentes por fatores externos.
- **Delimitação de dados e anti-prompt injection:** isolamento dos comentários em `<DADOS_AVALIACOES>` com instrução explícita para ignorar comandos ou tentativas de manipulação dentro do texto das avaliações.
- **Structured output / JSON schema:** uso de `/v1/chat/completions` com `response_format` (`json_schema` estrito) para padronizar o retorno e a lista de `avaliacoesDivergentes`.

### Comparativo entre versões

| Versão | Principais características | Objetivo e evolução observada |
| :--- | :--- | :--- |
| **v1** | *System prompt* inicial com critérios de fatores externos, *few-shot* básico e regras anti-*injection*. | Estabeleceu a base da análise; em cenários com maioria positiva e minoria de fatores externos (ex.: `PROD-005`), o modelo tendia a marcar `precisaRevisao: false` e `motivoPrincipal: NENHUM`. |
| **v2** | Regra explícita de que maioria positiva não anula fatores externos, coerência entre contagem e revisão, e exemplo *few-shot* misto. | Corrigiu o falso negativo em produtos com avaliações majoritariamente boas mas com ocorrências reais de logística/atendimento (`PROD-005`). |
| **v3** | Distinção explícita entre fatores externos válidos e defeitos/características do próprio produto (`PRODUTO`, `DEFEITO_PRODUTO`), somada à trava determinística no Java. | Evitou que falhas próprias do produto (`PROD-002` e `PROD-004`) fossem tratadas como fatores externos e abrissem revisão indevida. |

### Abordagem Escolhida e Justificativa

- **Abordagem adotada:** Consulta direta via JDBC por `produto_id`, envio dos registros no prompt ao Ollama (`/v1/chat/completions` com JSON Schema estrito), consolidação/validação determinística em Java e persistência via JDBC em H2.
- **Justificativa:** Como o domínio opera sobre um conjunto delimitado de avaliações associadas a um `produto_id` em banco relacional e exige gravação estruturada de relatórios, a orquestração direta em Java com JSON Schema oferece maior previsibilidade em modelos locais do que RAG vetorial, sem introduzir complexidade de embeddings, índices vetoriais, *fine-tuning* (LoRA/QLoRA) ou multimodalidade.

---

## Etapa 4 — Consolidação de Múltiplos Motivos e Regras de Consistência

Durante a análise de produtos com mais de uma ocorrência externa relevante, surgiu outro ponto de melhoria: quando diferentes avaliações apontavam motivos distintos (por exemplo, atendimento do vendedor e logística de entrega), um único motivo principal não representava plenamente o panorama do produto.

Para resolver isso de forma previsível:

- Adotou-se a consolidação dos diferentes fatores externos identificados na mesma análise, mantendo a rastreabilidade das avaliações envolvidas.
- Incorporou-se uma camada de validação determinística para assegurar coerência entre os motivos consolidados, os fatores externos válidos e a decisão final de gerar ou não um relatório de revisão.
- Essa combinação permitiu manter o *system prompt* estável na **v3** enquanto regras objetivas garantiam consistência nos resultados.

---

## Etapa 5 — Validação por Testes, Ajustes Operacionais e Segurança

Com as regras refinadas, a validação foi realizada em múltiplas frentes:

- **Base de dados e cenários (`V2__seed_avaliacoes.sql`):** 5 produtos simulados (`PROD-001` a `PROD-015`) com múltiplas avaliações por produto, cobrindo fatores externos (logística, embalagem, atendimento), defeitos reais do produto, avaliações coerentes e tentativas de *prompt injection* embutidas em comentários.
- **Testes automatizados (`ReviewAgentTest.java`):** verificam consultas JDBC no H2, persistência e leitura de relatórios (incluindo IDs e detalhes de avaliações divergentes), formatação/isolamento no `PromptBuilder`, trava determinística de fatores externos no `RelatorioJdbcService` e testes integrados opcionais contra o Ollama.
- **Validação via CLI (`testes`):** executa o lote de produtos de exemplo (`PROD-001` a `PROD-015`), compara o resultado obtido com o esperado, registra relatórios quando aplicável e exibe o resumo de acertos no terminal, permitindo comparar o comportamento entre versões de prompt (`v1`, `v2`, `v3`).

### Resultado da execução dos testes
#### --- Executando 15 Casos de Teste Iniciais ---
| Resultado | Produto | Esperado | Obtido | Motivos | Divergentes |
| :--- | :--- | :---: | :---: | :--- | :--- |
| PASS | PROD-001 | true | true | EMBALAGEM_DANIFICADA, LOGISTICA_ENTREGA, ATENDIMENTO_VENDEDOR, LOGISTICA_ENTREGA/EMBALAGEM_DANIFICADA | [7, 5, 3, 2] |
| PASS | PROD-002 | false | false | NENHUM | [] |
| PASS | PROD-003 | true | true | EMBALAGEM_DANIFICADA, LOGISTICA_TRANSPORTADORA, LOGISTICA_ENTREGA / ATENDIMENTO_VENDEDOR | [19, 17, 16] |
| PASS | PROD-004 | false | false | NENHUM | [] |
| PASS | PROD-005 | true | true | ATENDIMENTO_VENDEDOR, LOGISTICA_ENTREGA | [32, 29] |
| PASS | PROD-006 | false | false | NENHUM | [] |
| PASS | PROD-007 | true | true | ATENDIMENTO_VENDEDOR, LOGISTICA_TRANSPORTADORA, EMBALAGEM_DANIFICADA | [47, 44, 42] |
| PASS | PROD-008 | true | true | EMBALAGEM_DANIFICADA | [53] |
| PASS | PROD-009 | true | true | ATENDIMENTO_VENDEDOR, LOGISTICA_ENTREGA | [59, 56] |
| PASS | PROD-010 | false | false | NENHUM | [] |
| PASS | PROD-011 | true | true | EMBALAGEM_DANIFICADA, ATENDIMENTO_VENDEDOR, LOGISTICA_ENTREGA | [71, 69, 68] |
| PASS | PROD-012 | false | false | NENHUM | [] |
| PASS | PROD-013 | true | true | EMBALAGEM_DANIFICADA, ATENDIMENTO_VENDEDOR, LOGISTICA_ENTREGA | [88, 85, 81] |
| PASS | PROD-014 | true | true | LOGISTICA_ENTREGA | [93] |
| PASS | PROD-015 | true | true | ATENDIMENTO, EMBALAGEM_DANIFICADA, LOGISTICA_ENTREGA | [101, 98, 95] |
#### Resumo dos testes: 15/15 passaram.
### Segurança, Guardrails e Conformidade

- **Restrição de acesso e superfície de ataque:** O modelo de linguagem não possui acesso a ferramentas do sistema operacional, terminal, rede externa ou execução direta de SQL; toda leitura e escrita no banco H2 ocorre exclusivamente no código Java via `PreparedStatement`.
- **Defesa contra *prompt injection*:** Os comentários provenientes do banco são tratados como dados não confiáveis dentro de `<DADOS_AVALIACOES>`, higienizados de quebras de linha e acompanhados de diretrizes explícitas no *system prompt* para ignorar instruções embutidas (como pedidos para alterar notas ou suprimir revisão).
- **Confiabilidade, privacidade e conformidade:** A validação via JSON Schema, o uso de temperatura baixa (`0.1`) e as regras determinísticas em Java reduzem desvios de formato e classificações indevidas por defeito do produto; nos cenários de teste são utilizados dados fictícios, evitando exposição de dados pessoais reais e mantendo o foco apenas nos fatos relatados nas avaliações.

---

## Conclusão

O desenvolvimento seguiu um ciclo iterativo de **hipótese, teste, diagnóstico e refinamento**. Em vez de buscar uma solução complexa logo no início, cada ajuste respondeu a um comportamento observado na prática — primeiro corrigindo a omissão de fatores externos em cenários majoritariamente positivos, depois evitando que falhas do próprio produto gerassem revisões indevidas e, por fim, consolidando múltiplos motivos externos em um único relatório coerente com alta previsibilidade garantida pela baixa temperatura e validação determinística em Java. O resultado é um processo claro, auditável e alinhado ao escopo proposto.