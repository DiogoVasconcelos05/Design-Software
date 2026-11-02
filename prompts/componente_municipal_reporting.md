# Registo de Utilização de IA — Gerar Componente municipal_reporting

* **Ferramenta / Modelo:** Gemini flash 3.6
* **Ficheiros de contexto fornecidos:** `ARCHITECTURE.md`, `ADR-001`, `ADR-005`, componente `common` e interfaces públicas trip_management.query.TripUsageQuery e station_inventory.query.StationLocationQuery

## Prompt utilizada
Actua como engenheiro de software sénior especialista em Java 21.

O teu objetivo é gerar o módulo `municipal_reporting` em `src/main/java/pt/lisboapedala/municipal_reporting/`.

--- REGRAS ESTRITAS DE ARQUITETURA (FF-01 a FF-25) ---
1. ÁRVORE DE PACOTES ESTREITA (FF-11):
   - Estrutura permitida: `pt.lisboapedala.municipal_reporting.internal`.
   - PROIBIDO criar pacotes `domain/`, `service/`, `controller/`, `repository/`.

2. ISOLAMENTO E REGRAS READ-ONLY (FF-07, FF-08, FF-18):
   - PROIBIDO o uso de JDBC, `JdbcClient`, SQL, transações (`@Transactional`) ou tabelas próprias de BD.
   - Este módulo é APENAS um agregador em memória.
   - Acede a dados de outros módulos estritamente através dos pacotes `..query..`:
     * `trip_management.query.TripUsageQuery`
     * `station_inventory.query.StationLocationQuery`
   - Os DTOs e relatórios gerados NÃO PODEM conter dados pessoais de utilizadores (cumprimento do RGPD).

3. TEMPO E IMUTABILIDADE (FF-16, FF-17):
   - Injeção do `java.time.Clock` para obtenção de datas. PROIBIDO `Instant.now()`.
   - DTOs de relatórios implementados como `record`s de Java 21.

--- REQUISITOS DE NEGÓCIO ---
- Implementa a geração do relatório mensal municipal de utilização e estimativa de CO₂ evitado por freguesia (US-4 / Req. 7).
- Agrega em memória as viagens do mês solicitado obtidas via `TripUsageQuery` e mapeia-as às freguesias via `StationLocationQuery`.

Gera o código Java completo para o módulo `municipal_reporting`.

# Resultado
* **Estado**: Aceite
* **Porquê**: TODO