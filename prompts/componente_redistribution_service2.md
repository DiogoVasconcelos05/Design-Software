# Registo de Utilização de IA — Gerar Componente redistribution_service

* **Ferramenta / Modelo:** Gemini flash 3.6
* **Ficheiros de contexto fornecidos:** `ARCHITECTURE.md`, `ADR-001`, `ADR-005`, componente `common` e a interface pública station_inventory.query.StationOccupancyQuery

## Prompt utilizada
Actua como engenheiro de software sénior especialista em Java 21.

O teu objetivo é gerar o módulo `redistribution_planning` em `src/main/java/pt/lisboapedala/redistribution_planning/`.

--- REGRAS ESTRITAS DE ARQUITETURA (FF-01 a FF-25) ---
1. ÁRVORE DE PACOTES ESTREITA (FF-11):
   - Estrutura permitida: `pt.lisboapedala.redistribution_planning.internal` e `pt.lisboapedala.redistribution_planning.query`.
   - PROIBIDO criar pacotes `domain/`, `service/`, `controller/`, etc.

2. REGRAS ESTRITAS DE PACOTES `..query..` (FF-05, FF-06):
   - Os pacotes `..query..` DEVEM CONTER EXCLUSIVAMENTE `interface`s e `record`s imutáveis de nível superior.
   - PROIBIDO criar ou embutir `enum`s (ex.: `public enum StopAction`), `class`es ou tipos não-record dentro do pacote `query` (viola a regra ArchUnit FF-06).
   - Para representar tipos de ação ou estado nos DTOs de `query`, usa tipos simples como `String` (ex.: `String action` com valores `"PICKUP"` / `"DROP_OFF"`) ou utiliza um `enum` existente no pacote `common`.

3. VERIFICAÇÃO "READ-ONLY" E SEM BD (FF-07, FF-08):
   - PROIBIDO o uso de JDBC, `JdbcClient`, SQL, anotações `@Transactional` ou persistência própria. Este módulo é puramente computacional.
   - Apenas pode aceder aos dados de outros módulos importando interfaces do pacote `..query..` (especificamente `station_inventory.query.StationOccupancyQuery`).
   - Nota sobre os DTOs do `station_inventory.query`: a capacidade de uma estação é obtida via `station.totalDocks()` (e NÃO `capacity()`).
   - PROIBIDO aceder ao pacote `internal` ou `persistence` de qualquer outro módulo.

4. TEMPO E ESTRUTURA (FF-10, FF-16):
   - Injeção obrigatoriamente por construtor e uso de `java.time.Clock` se necessário.
   - DTOs e View Models devem ser `record`s imutáveis.

--- REQUISITOS DE NEGÓCIO ---
- Implementa o algoritmo de sugestão de rotas para carrinhas de redistribuição (Req. 6 / Endpoint `/redistribution/routes`).
- Lê o estado de ocupação atual das estações via `StationOccupancyQuery` e calcula as rotas de reequilíbrio em memória.

Gera o código Java completo para o módulo `redistribution_planning`.

# Resultado
* **Estado**: Aceite
* **Porquê**: A regeneração corrigiu a violação detetada anteriormente na FF-06 ao remover o `enum` do pacote público `..query..`, passando a expor apenas `record`s e `interface`s de nível superior. O módulo passou em todas as fitness functions de arquitetura: operou exclusivamente em memória sem acesso direto à base de dados ou anotações `@Transactional` (FF-08), consumiu o estado de ocupação através da interface pública `StationOccupancyQuery` (FF-07) utilizando corretamente o método `station.totalDocks()`, e injetou `java.time.Clock` para controlos temporais (FF-16).