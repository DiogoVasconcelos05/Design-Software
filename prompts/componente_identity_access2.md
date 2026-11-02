# Registo de Utilização de IA — Gerar Componente identity_access

* **Ferramenta / Modelo:** Gemini flash 3.6
* **Ficheiros de contexto fornecidos:** `ARCHITECTURE.md`, `ADR-001` e componente `common`

## Prompt utilizada
Actua como engenheiro de software sénior especialista em Java 21 e Spring JdbcClient.

O teu objetivo é gerar o módulo `identity_access` em `src/main/java/pt/lisboapedala/identity_access/`.

--- REGRAS ESTRITAS DE ESTRUTURA (FF-11, FF-04b, FF-16) ---
1. A árvore de pacotes só pode ter: `query/`, `port/`, `internal/` e `persistence/`. PROIBIDO criar pacotes `domain/`, `service/` ou `controller/`.
2. O pacote `persistence/` só pode ser acedido pelo pacote `internal/`.
3. Injeção obrigatória de `java.time.Clock` para gerir tempo/datas (proibido Instant.now() sem clock).
4. Acesso a dados feito exclusivamente com Spring `JdbcClient` e SQL à mão (SEM JPA).
5. Pode importar apenas o pacote `common`.
6. Implementa a gestão de utilizadores e validação de passes/credenciais para a US-1 (`IdentityService` dentro de `internal/`).

Gera as classes e interfaces Java completas para o pacote `identity_access`.

# Resultado
* **Estado**: Rejeitado
* **Porquê**: O problema ocorre porque a regra FF-18 de ArchUnit deteta campos identificadores como userId dentro do pacote público query do módulo, aplicando o princípio de privacidade desde a conceção (ADR-005). A camada query destina-se a expor vistas de dados públicos e agregados entre módulos, enquanto os dados pessoais ou identificadores diretos de utilizadores devem ser isolados ou truncados/anonimizados.