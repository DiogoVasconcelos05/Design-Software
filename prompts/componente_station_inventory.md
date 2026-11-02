# Registo de Utilização de IA — Gerar Componente station_inventory

* **Ferramenta / Modelo:** Gemini flash 3.6
* **Ficheiros de contexto fornecidos:** `ARCHITECTURE.md`, `ADR-001`,`ADR-005` e componente `common`

## Prompt utilizada
Actua como engenheiro de software sénior especialista em Java 21 e Spring Boot 3.3+ (JdbcClient).

O teu objetivo é gerar o módulo `station_inventory` em `src/main/java/pt/lisboapedala/station_inventory/`.

--- REGRAS ESTRITAS DE ARQUITETURA (FF-01 a FF-25) ---
1. ÁRVORE DE PACOTES ESTREITA (FF-11): Subpacotes estritos permitidos:
   - `query/`       : Interface pública `StationOccupancyQuery` e View DTOs (`record`s imutáveis) para consulta do estado das estações.
   - `port/`        : Interfaces públicas para alteração do estado das docas (ex.: `InventoryUpdatePort`).
   - `internal/`    : Lógica de negócio privada.
   - `persistence/` : Mappers e Repositórios `JdbcClient` privados (acessíveis APENAS por `internal/`).
   - PROIBIDO criar pacotes `domain/`, `service/`, `controller/` ou `repository/`.

2. CONTRATO DE CONSULTA E PRIVACIDADE (FF-05, FF-06, FF-18):
   - O pacote `query/` só pode conter interfaces e `record`s imutáveis. Nenhum método pode ter retorno `void`.
   - Os `record`s de consulta NÃO podem conter dados pessoais nem percursos de utilizadores (RGPD).

3. PERSISTÊNCIA E TEMPO (FF-12, FF-16, FF-21):
   - Acesso a dados com Spring `JdbcClient` (Sem JPA). Injeção obrigatoriamente por construtor.
   - Tempo via dependência injetada de `java.time.Clock`. Sem invocação direta de `Instant.now()`.
   - Isolamento de tabela SQL (sem JOINs externos nem Foreign Keys entre módulos).

--- REQUISITOS DE NEGÓCIO ---
- Gere o estado em tempo real de estações, docas e bicicletas (vagas livres, ocupadas, fora de serviço).
- Suporta a atualização de estado quando disparada pelo `dock_gateway` (ao libertar ou encaixar uma bicicleta).

Gera o código Java completo para o módulo `station_inventory`.

# Resultado
* **Estado**: Aceite
* **Porquê**: O código gerado para o módulo `station_inventory` cumpriu estritamente a especificação do `ARCHITECTURE.md` e a ADR-005. Todas as fitness functions aplicáveis passaram com sucesso: a árvore de pacotes foi mantida estritamente dentro de `query/`, `port/`, `internal/` e `persistence/` (FF-11), a consulta do estado de ocupação foi exposta através de `record`s imutáveis na interface pública `StationOccupancyQuery` (FF-05/06) sem violação de dados do RGPD (FF-18), e o acesso à base de dados utilizou `JdbcClient` privado ao módulo com injeção de tempo via `java.time.Clock` (FF-12/16).