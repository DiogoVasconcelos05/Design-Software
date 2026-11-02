# Relatório de Reflexão sobre o Processo de Engenharia de Prompts e Arquitetura

**Projeto:** Lisboa Pedala  
**Autores:** Diogo Vasconcelos & Rodrigo Santos  
**Data:** Novembro de 2026  

---

## 1. Introdução e Estratégia de Geração

Durante o desenvolvimento do sistema **Lisboa Pedala**, a utilização de inteligência artificial generativa (com recurso a modelos como **Gemini 3.6 Flash** e **Claude Opus 5.5 / Claude Code**) foi integrada de forma disciplinada no fluxo de trabalho de design de software. 

A premissa central estabelecida pela equipa foi utilizar o ficheiro `ARCHITECTURE.md`, apoiado pelas ADRs (001 a 005) e pelos diagramas C4 (Níveis 1, 2 e 3), como a **fonte única da verdade (*single source of truth*)**. Para garantir que nenhum código desalinhado ou com dívida técnica entrava no repositório, adotou-se o princípio rigoroso de **validação por Fitness Functions automatizadas (ArchUnit)** em ambiente local antes de qualquer consolidação no repositório.

A estratégia de apoio de IA assentou em dois pilares principais:
1. **Atuação como Revisora Crítica de Arquitetura:** Interações de discussão de alto nível para validar cenários de qualidade, desconstruir acoplamento entre domínios e refinar trade-offs.
2. **Geração Módulo a Módulo em Chats Limpos:** Para evitar a acumulação de contexto irrelevante, viés (*hallucinations*) ou degradação por limites de *tokens*, cada componente de domínio foi gerado num chat isolado, fornecendo apenas as interfaces públicas dependentes e as regras estritas da especificação.

---

## 2. Análise do Desempenho e Violações Detetadas pela IA

A execução das Fitness Functions (`FF-01` a `FF-25`) foi fundamental para atuar como guardiã da arquitetura. A análise do histórico de prompts revela padrões claros em relação às limitações iniciais da IA e como a evolução do *Prompt Engineering* corrigiu esses comportamentos.

### 2.1 Principais Violações Apanhadas pelas Fitness Functions

* **Violação da Convenção Estrita de Pacotes (FF-11):**
  * *Ocorrência:* Na 1.ª tentativa do módulo `identity_access`.
  * *Causa:* A IA tendeu a replicar padrões genéricos da indústria (criando pastas como `domain/`, `service/` e `repository/`).
  * *Deteção & Correção:* O teste ArchUnit `knownPackagesOnly` falhou imediatamente. A prompt foi reformulada para proibir explicitamente estes nomes e forçar a árvore estrita: `query/`, `port/`, `internal/` e `persistence/`.

* **Regra de Privacidade e Proteção de Dados (FF-18 / RGPD):**
  * *Ocorrência:* Na 2.ª tentativa do módulo `identity_access`.
  * *Causa:* A IA expôs identificadores diretos de utilizadores (`userId`) dentro das DTOs do pacote público `query/`.
  * *Deteção & Correção:* A regra de privacidade em ArchUnit chumbou a estrutura. O módulo passou a ser configurado de modo a que a camada `query/` sirva exclusivamente visões agregadas/públicas, mantendo os dados de identidade encapsulados.

* **Involução no Contrato do Pacote `..query..` (FF-06):**
  * *Ocorrência:* Na 1.ª tentativa do `redistribution_planning`.
  * *Causa:* O modelo colocou um `enum` (`StopAction`) dentro do subpacote `query/`.
  * *Deteção & Correção:* A `FF-06` dita que pacotes `query/` só podem conter `interface`s e `record`s de nível superior. A prompt seguinte instruiu expressamente a IA a utilizar tipos primitivos/strings ou a reutilizar tipos de `common`, resolvendo o problema.

* **Violação da Regra de Injeção de Tempo e Determinismo (FF-16):**
  * *Ocorrência:* Tentativas iniciais em `identity_access` chamando `LocalDate.now()` ou `Instant.now()` estáticos.
  * *Deteção & Correção:* Chumbo imediato pela `FF-16`. A inclusão da obrigatoriedade da dependência `java.time.Clock` nas prompts garantiu a testabilidade e o cálculo correto em reenvios assíncronos das docas.

---

## 3. Avaliação da Robustez das Fitness Functions (Mecanismo de Canários)

Para demonstrar ao corpo docente que a ausência de violações no código final não resultou de *fitness functions* permissivas ou "fracas", o repositório foi equipado com a suíte **Canary Tests (FF-25)** em `src/test/java/canary/`.

Esta suíte compila propositadamente classes "canário" que cometem infrações intencionais (como acessos diretos à persistência de outros módulos, chamadas a `Instant.now()`, ou adição de estado mutável no `tariff_pricing`). Se qualquer regra ArchUnit for acidentalmente enfraquecida por um desenvolvedor ou pela IA, o teste canário passa incorretamente e falha a compilação do projeto.

---

## 4. Lições Aprendidas e Conclusão

1. **Prompt Descritiva vs. Constraints Negativas:** Instruções afirmativas ("cria um serviço de utilizadores") falham se não forem acompanhadas de proibições explícitas (*negative constraints*), tais como: *"PROIBIDO criar a pasta domain/ ou usar JPA"*.
2. **Ciclo de Feedback Rápido:** A combinação do `ARCHITECTURE.md` rigoroso com testes de compilação em ArchUnit locais permitiu rejeitar código inválido em menos de 30 segundos, sem poluir a árvore principal do Git.
3. **Padrões de Leitura Desacoplada (ADR-005):** A obrigatoriedade de separar `port/` (escrita) e `query/` (leitura read-only) provou ser a ferramenta mais eficaz para manter os módulos de relatórios (`municipal_reporting`) e planeamento (`redistribution_planning`) totalmente isolados e livres de dependências transacionais ou da base de dados.