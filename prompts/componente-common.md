# Registo de Utilização de IA — Gerar Componente common

* **Ferramenta / Modelo:** Gemini flash 3.6
* **Ficheiros de contexto fornecidos:** `ARCHITECTURE.md`, `ADR-001` e `ADR-004`

## Prompt utilizada
Actua como engenheiro de software sénior especialista em Java 21.

O teu objetivo é gerar o código para o pacote `common` do sistema "Lisboa Pedala", localizado em `src/main/java/pt/lisboapedala/common/`.

--- REGRAS E CONTRATOS ---
1. Respeita estritamente o `ARCHITECTURE.md` e as decisões nas ADRs anexadas.
2. O pacote `common` NÃO PODE importar nenhum outro pacote ou módulo do projeto (Allowed-to-use).
3. Todos os DTOs e Eventos partilhados DEVEM ser imutáveis e implementados como `record` de Java 21.
4. Inclui o `DockEventRecord` (com UUID `eventId`, `dockId`, `bikeId`, `eventType` e `occurredAt` LocalDateTime/Instant) para suportar a idempotência definida na ADR-004.
5. Define as exceções genéricas da aplicação (ex.: `DomainException`, `EntityNotFoundException`, `BusinessRuleException`).

Gera o código Java completo para o pacote `common`, organizado com a estrutura de ficheiros correta e limpo de dependências externas.

# Resultado
* **Estado**: Aceite
* **Porquê**: TODO