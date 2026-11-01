# Especificação de Arquitetura — Lisboa Pedala

## 1. Módulos e Responsabilidades
* **Linguagem / Runtime:** Java 21
* **Particionamento:** Monólito Modular por Domínio (`src/main/java/pt/lisboapedala/`)

| Módulo / Pacote | Responsabilidade Única (1 frase) | Pacote Java |
| :--- | :--- | :--- |
| `common` | Reúne tipos, exceções e DTOs imutáveis partilhados sem lógica de negócio. | `pt.lisboapedala.common` |
| `identity_access` | Gere a identidade, autenticação e validação do cartão Navegante e passe. | `pt.lisboapedala.identity_access` |
| `trip_management` | Orquestra a máquina de estados e o ciclo de vida da viagem. | `pt.lisboapedala.trip_management` |
| `dock_gateway` | Trata da resiliência, desduplicação e reenvio de eventos das docas offline. | `pt.lisboapedala.dock_gateway` |
| `station_inventory` | Mantém o estado de ocupação de estações e docas em tempo real. | `pt.lisboapedala.station_inventory` |
| `tariff_pricing` | Função pura sem estado para cálculo de valores de viagens por tarifário. | `pt.lisboapedala.tariff_pricing` |
| `payment_invoicing` | Processa transações financeiras e emite/comunica faturas fiscais à AT. | `pt.lisboapedala.payment_invoicing` |
| `fleet_health` | Ingestão de telemetria IoT (30s) e deteção automática de anomalias. | `pt.lisboapedala.fleet_health` |
| `maintenance_orders` | Gestão do fluxo e ciclo de vida das ordens de trabalho técnicas. | `pt.lisboapedala.maintenance_orders` |
| `redistribution_planning` | Otimização e sugestão de rotas de equilíbrio para as carrinhas. | `pt.lisboapedala.redistribution_planning` |
| `municipal_reporting` | Agregação de métricas mensais de utilização e CO₂ por freguesia. | `pt.lisboapedala.municipal_reporting` |
| `api` | Camada de entrada (CLI/REST) que encaminha pedidos e não tem lógica. | `pt.lisboapedala.api` |

---

## 2. Regras Allowed-to-Use (Dependências Verificáveis)
1. **Regra de Entrada:** NENHUM módulo de domínio pode importar ou depender do pacote `api`.
2. **Isolamento de Tipos:** NENHUM módulo pode aceder a modelos de persistência (entidades BD) de outro módulo.
3. **Módulo Comum:** O pacote `common` não pode importar nenhum outro módulo da aplicação.
4. **Acoplamento Tarifário:** `tariff_pricing` não pode importar nenhum módulo de domínio (apenas `common`).
5. **Relatórios:** `municipal_reporting` apenas realiza operações de leitura (*read-only*) sobre os dados de `trip_management` e `station_inventory`.
6. **Verificação Automatizada:** Estas regras são validadas pelas *fitness functions* em `ArchUnit` localizadas em `src/test/java/pt/lisboapedala/architecture/`.

---

## 3. Características Condutoras e Impacto no Código
* **Availability (Disponibilidade):** A lógica de fecho de viagem e devolução deve utilizar garantias de idempotência no `trip_management` baseadas no UUID do evento gerado pela doca.
* **On-Demand Elasticity (Elasticidade):** As operações de leitura de disponibilidade de docas/bicicletas (`station_inventory`) devem ser separadas das operações de escrita transacional de viagens.
* **Scalability (Escalabilidade):** As funções de cálculo em `tariff_pricing` devem ser puras e imutáveis para suportar adição dinâmica de regras de municípios vizinhos sem efeitos secundários.

---

## 4. Acoplamento Temporal e Unidades de Trabalho
* **Abertura de Viagem:** 1) Validação de passe em `identity_access` $\rightarrow$ 2) Emissão de sinal físico de abertura em `dock_gateway` $\rightarrow$ 3) Mudança de estado para em andamento em `trip_management`.
* **Devolução e Fecho:** A confirmação do encaixe na doca (`station_inventory`) e o cálculo de tempo de viagem (`trip_management`) têm de ser consolidados ANTES de invocar o débito e liquidação em `payment_invoicing`.

---

## 5. Convenções e Limitações para a IA
* **Proibido:** Não utilizar *imports* curinga (`import pt.lisboapedala.*`).
* **Proibido:** Não fazer *queries* SQL com *JOINs* entre tabelas de domínios diferentes.
* **Proibido:** Não criar dependências circulares entre componentes.
* **Obrigatório:** Todos os DTOs partilhados em `common` devem ser registos imutáveis (`record` em Java 21).

---

## 6. Mapeamento de ADRs por Componente
* **`trip_management` & `dock_gateway`:** ADR-002 e ADR-003 (Reestruturação).
* **Todos os Componentes:** ADR-001 (Monólito Modular por Domínio).