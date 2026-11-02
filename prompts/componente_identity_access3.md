# Registo de Utilização de IA — Gerar Componente identity_access

* **Ferramenta / Modelo:** Gemini flash 3.6
* **Ficheiros de contexto fornecidos:** `ARCHITECTURE.md`, `ADR-001` e componente `common`

## Prompt utilizada
Actua como engenheiro de software sénior especialista em Java 21 e Spring Boot 3.3+ (JdbcClient).

O teu objetivo é gerar o módulo `identity_access` em `src/main/java/pt/lisboapedala/identity_access/`.

--- REGRAS ESTRITAS DE ARQUITETURA (FF-01 a FF-25) ---
1. ÁRVORE DE PACOTES ESTREITA (FF-11): Os únicos subpacotes permitidos em `pt.lisboapedala.identity_access` são:
   - `query/`       : Interfaces e View DTOs públicos de leitura (se necessário)
   - `port/`        : Interfaces/Portas públicas para comandos de escrita (ex.: `IdentityService` / `RegisterUserPort`)
   - `internal/`    : Serviços de aplicação e entidades de domínio (PRIVADO AO MÓDULO)
   - `persistence/` : Mappers, entidades SQL e repositórios JdbcClient (PRIVADO AO MÓDULO, acessível APENAS por `internal/`)
   - PROIBIDO criar pacotes `domain/`, `service/`, `controller/`, `repository/` ou `model/`.

2. DEPENDÊNCIAS E IMPORTAÇÕES (FF-01, FF-02, FF-20):
   - Pode importar APENAS o pacote `common`. NENHUM outro módulo ou a `api` podem ser importados.
   - PROIBIDO utilizar importações com cardinalidade curinga (`import pt.lisboapedala.*`).

3. PERSISTÊNCIA E TEMPO (FF-12, FF-16, FF-21):
   - SEM JPA: Usar exclusivamente Spring `JdbcClient` / `JdbcTemplate` com SQL manual. Injeção obrigatoriamente por construtor.
   - PROIBIDO chamar `Instant.now()` ou `LocalDateTime.now()` diretos. Injetar obrigatoriamente `java.time.Clock`.
   - SEM FOREIGN KEYS para tabelas de outros módulos nem JOINs entre domínios.

--- REQUISITOS DE NEGÓCIO ---
- Implementa a gestão de utilizadores e validação de credenciais/passes (US-1 / Req. 1).
- Suporta o registo de utilizador (`RegisterUserCommand`), consulta de perfil e validação do passe Navegante ou passe anual (Mock de elegibilidade).

Gera o código Java completo para o módulo `identity_access`.

# Resultado
* **Estado**: Aceite
* **Porquê**: O módulo cumpriu na íntegra a especificação do `ARCHITECTURE.md` e as restrições arquiteturais. Todas as fitness functions do ArchUnit passaram com sucesso: a estrutura de pastas ficou restrita aos pacotes permitidos `query/`, `port/`, `internal/` e `persistence/` (FF-11).