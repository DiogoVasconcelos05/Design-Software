# Componentes

## 1. Abordagem
A abordagem escolhida foi Actor/Action para realizar a primeira identificação dos componentes lógicos. 

### Justificação:
* **Diversidade de Perfis:** O sistema interage com atores com responsabilidades muito distintas: Assinantes, Utilizadores Ocasionais, Técnicos de Manutenção, Equipas de Carrinhas e Gestores Municipais. Mapear os componentes pelas ações de cada ator garante que a lógica de manutenção ou relatórios municipais não se mistura com o fluxo crítico de desbloqueio.

## 2. Atores, Ações e Componentes Identificados

| Ator | Ação / Evento no Sistema | Componente Lógico Resultante | Responsabilidade Única |
| :--- | :--- | :--- | :--- |
| **Assinante / Ocasional** | Autenticar utilizador, validar passe/cartão Navegante e QR Code | `identity_access` | Gestão de identidade, autenticação e validação de elegibilidade de passes/cartões. |
| **Assinante / Ocasional** | Solicitar desbloqueio, monitorizar viagem e registar devolução | `trip_management` | Orquestração e controlo da máquina de estados e ciclo de vida da viagem. |
| **Doca / Estação (Ator)** | Transmitir eventos físicos, operar offline (24h) e reenviar na reconexão | `dock_gateway` | Ponto de entrada de eventos das docas, resiliência offline e desduplicação. |
| **Utilizador / Sistema** | Consultar disponibilidade de bicicletas/docas livres em tempo real | `station_inventory` | Gestão do estado de ocupação das estações e exposição de dados em tempo real. |
| **Sistema Central** | Calcular o valor da viagem com base no tempo decorrido e regras do passe | `tariff_pricing` | Motor de cálculo puro de tarifários e regras de tempo excedente. |
| **Sistema Central** | Processar pagamento (MB WAY/Cartão), emitir fatura e comunicar à AT | `payment_invoicing` | Processamento de transações financeiras, faturação e integração fiscal. |
| **Bicicleta Elétrica** | Enviar dados de telemetria a cada 30s e alertar falhas de bateria/travões | `fleet_health` | Ingestão e análise contínua de telemetria para deteção de anomalias na frota. |
| **Técnico / Utilizador** | Abrir, atribuir e acompanhar ordens de reparação | `maintenance_orders` | Gestão do fluxo de trabalho das ordens de manutenção para os técnicos. |
| **Carrinha de Transporte** | Receber sugestões de rotas para equilíbrio de bicicletas nas estações | `redistribution_planning` | Cálculo e recomendação de rotas otimizadas para redistribuição da frota. |
| **Gestor Municipal** | Consultar métricas mensais de utilização por freguesia e CO₂ evitado | `municipal_reporting` | Agregação e geração de relatórios analíticos para a Câmara Municipal. |

## 3. Mapeamento User Story → Componente

| Referência Oficial | Ator & Descrição da Story / Requisito | Componente Principal | Componentes Secundários / Envolvidos | Notas Arquiteturais & Fluxo de Dependências |
| :--- | :--- | :--- | :--- | :--- |
| **US-1** *(Obrigatória)* | **Como assinante**, desbloqueio e devolvo uma bicicleta e a viagem fica registada e cobrada. | `trip_management` | `identity_access`, `dock_gateway`, `station_inventory`, `payment_invoicing` | `trip_management` valida o passe em `identity_access`; `dock_gateway` confirma devolução e notifica `station_inventory`; `payment_invoicing` efetua a cobrança e invoca indiretamente `tariff_pricing`. |
| **US-2** | **Como técnico**, recebo uma ordem de manutenção quando uma bicicleta reporta avaria. | `maintenance_orders` | `fleet_health` | Suporta dois canais: automático (ingestão IoT de 30s via `fleet_health`) e manual (reporte de utilizador que entra na API e cria diretamente a ordem em `maintenance_orders`). |
| **US-3** | **Como doca sem rede**, reenvio os eventos guardados quando a rede volta, sem duplicar viagens. | `dock_gateway` | `trip_management`, `station_inventory` | `dock_gateway` faz a desduplicação primária; `trip_management` aplica idempotência ao fechar a viagem; `station_inventory` atualiza a ocupação das docas. |
| **US-4** | **Como gestor municipal**, obtenho o relatório de utilização por freguesia. | `municipal_reporting` | `trip_management`, `station_inventory` | Processamento analítico em modo leitura (*read only*) para emissão de relatórios e cálculo de CO₂. |
| **Req. 1** | **Assinante / Ocasional:** Registo na plataforma, compra e validação do passe anual Navegante. | `identity_access` | `payment_invoicing` | Validação da elegibilidade do utilizador e emissão/validação do código QR e cartão Navegante. |
| **Req. 4** | **Utilizador:** Consulta do mapa em tempo real com bicicletas e docas livres por estação. | `station_inventory` | `dock_gateway` | Leitura desacoplada e de alto desempenho para consulta da app e dados abertos da Câmara. |
| **Req. 6** | **Equipa de Carrinha:** Sugestão de rotas para equilibrar a ocupação das estações. | `redistribution_planning` | `station_inventory` | Algoritmo de otimização de rotas que lê o estado de ocupação do `station_inventory`. |
| **Req. 8a** | **Utilizador:** Consulta do histórico pessoal de viagens efetuadas. | `trip_management` | - | A camada de API consulta diretamente o `trip_management`, evitando a criação de ciclos com o `identity_access`. |
| **Req. 8b** | **Utilizador:** Consulta do histórico de faturas e recibos emitidos. | `payment_invoicing` | - | A camada de API consulta diretamente o `payment_invoicing` em isolamento, cumprindo a coesão do módulo. |

## 4. Análise de Coesão, Acoplamento e Instabilidade

| Componente | Nível de Coesão | $C_A$ | $C_E$ | Instabilidade ($I$) | Justificação Arquitetural |
| :--- | :--- | :---: | :---: | :---: | :--- |
| **`identity_access`** | **Funcional** | 2 | 1 | **0.33** *(Altamente Estável)* | **Coesão:** Gere exclusivamente a identidade, autenticação e validação do passe/cartão Navegante.<br>**Acoplamento:** Consultado por `trip_management` e `api` ($C_A = 2$). Depende apenas de `common` ($C_E = 1$).<br>**Papel:** Componente de núcleo/estável na arquitetura. |
| **`trip_management`** | **Funcional** | 3 | 4 | **0.57** *(Equilibrado)* | **Coesão:** Trata unicamente da máquina de estados e do ciclo de vida da viagem (início, andamento e fim).<br>**Acoplamento:** Invocado por `dock_gateway`, `municipal_reporting` e `api` ($C_A = 3$). Para operar, consome `identity_access`, `station_inventory`, `payment_invoicing` e `common` ($C_E = 4$).<br>**Papel:** Nó de orquestração do fluxo de utilização. |
| **`dock_gateway`** | **Sequencial** | 1 | 4 | **0.80** *(Instável)* | **Coesão:** Trata do processamento, desduplicação e tradução de eventos vindos das docas.<br>**Acoplamento:** Usado pela `api` ($C_A = 1$). Notifica `trip_management`, `station_inventory`, `fleet_health` e `common` ($C_E = 4$).<br>**Papel:** Ponto de entrada e fronteira I/O de resiliência. |
| **`station_inventory`** | **Funcional** | 4 | 1 | **0.20** *(Altamente Estável)* | **Coesão:** Mantém em tempo real o estado e ocupação física de estações e docas.<br>**Acoplamento:** Usado por `trip_management`, `dock_gateway`, `redistribution_planning` e `municipal_reporting` ($C_A = 4$). Depende apenas de `common` ($C_E = 1$).<br>**Papel:** Fonte da verdade do estado da rede de estações. |
| **`tariff_pricing`** | **Funcional** | 1 | 1 | **0.50** *(Equilibrado)* | **Coesão:** Função pura sem estado para calcular o valor de uma viagem a partir de regras de tarifário.<br>**Acoplamento:** Invocado por `payment_invoicing` ($C_A = 1$) e depende apenas de `common` ($C_E = 1$).<br>**Papel:** Módulo isolado de cálculo que permite adicionar novos tarifários municipais sem impactar outros componentes. |
| **`payment_invoicing`** | **Sequencial** | 1 | 2 | **0.67** *(Equilibrado)* | **Coesão:** Executa o processamento financeiro (MB WAY/cartão), emissão de faturas e comunicação à AT.<br>**Acoplamento:** Invocado por `trip_management` ($C_A = 1$) e depende de `tariff_pricing` e `common` ($C_E = 2$).<br>**Papel:** Camada de cobrança e liquidação fiscal isolada das regras da viagem. |
| **`fleet_health`** | **Comunicacional** | 1 | 2 | **0.67** *(Equilibrado)* | **Coesão:** Processa telemetria IoT (30s) e deteta anomalias na frota sobre os mesmos dados de entrada.<br>**Acoplamento:** Notificado por `dock_gateway` ($C_A = 1$). Invoca `maintenance_orders` e usa `common` ($C_E = 2$). |
| **`maintenance_orders`** | **Funcional** | 2 | 1 | **0.33** *(Estável)* | **Coesão:** Gere o ciclo de vida e estado das ordens de reparação técnicas.<br>**Acoplamento:** Alimentado por `fleet_health` e pela `api` ($C_A = 2$). Depende apenas de `common` ($C_E = 1$). |
| **`redistribution_planning`** | **Funcional** | 1 | 2 | **0.67** *(Instável)* | **Coesão:** Algoritmo dedicado de cálculo e sugestão de rotas para as carrinhas.<br>**Acoplamento:** Invocado pela `api` ($C_A = 1$). Lê dados de `station_inventory` e usa `common` ($C_E = 2$). |
| **`municipal_reporting`** | **Comunicacional** | 1 | 3 | **0.75** *(Instável)* | **Coesão:** Agrega dados analíticos de utilização e $CO_2$ evitado para relatórios municipais.<br>**Acoplamento:** Invocado pela `api` ($C_A = 1$). Lê em modo *read-only* de `trip_management`, `station_inventory` e `common` ($C_E = 3$). |
| **`common`** | **Lógica (Baixa)** | 10 | 0 | **0.00** *(Altamente Estável)* | **Coesão:** Reúne DTOs, excepções e eventos globais partilhados.<br>**Acoplamento:** Usado por todos os componentes do sistema ($C_A = 10$) e não depende de ninguém ($C_E = 0$). |
| **`api`** | **Lógica** | 0 | 10 | **1.00** *(Totalmente Instável)* | **Coesão:** Ponto de entrada exterior (REST / CLI) sem lógica de negócio associada.<br>**Acoplamento:** Não é usado por nenhum componente do domínio ($C_A = 0$) e pode invocar qualquer componente ($C_E = 10$). |

## 5. Acoplamento Temporal

O fluxo transacional da **US-1 (Desbloqueio e Cobrança de Viagem)** exige o cumprimento de duas regras estritas de **acoplamento temporal** (ordem sequencial de execução necessária):

1. **Validação Preliminar do Desbloqueio:**
   O `trip_management` necessita de invocar sincronicamente o `identity_access` para validar a validade da anuidade/passe **antes** de emitir a ordem de abertura física à doca em `dock_gateway`. Se a ordem for invertida, a bicicleta pode ser libertada para um utilizador sem passe válido ou suspenso.
2. **Fecho da Transação e Cobrança:**
   A devolução da bicicleta (confirmada pelo `dock_gateway`) obriga a que o `trip_management` determine a duração exata da viagem **antes** de disparar o cálculo e cobrança de blocos de 15 minutos em `tariff_pricing`. A cobrança e a emissão de fatura constituem a unidade final da transação de devolução e será responsabilidade de `payment_invoicing`.

## 6. Conascencia

| # | Componentes | Tipo de conascência | Força | Localidade | Grau | Descrição | Ação para reduzir |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| 1 | `dock_gateway` <-> `trip_management` | Dinâmica: execução e identidade | Alta  | Entre componentes (distante) | 2 componentes, mas afeta todas as viagens | A viagem só pode fechar depois de a doca confirmar o encaixe, e o mesmo evento reenviado (US-3) tem de corresponder sempre à mesma viagem, para não a duplicar. | Identificador único por evento, gerado na doca. Desduplicação em `dock_gateway` e operação de fecho idempotente em `trip_management` (segunda linha de defesa). Registar em ADR. |
| 2 | `trip_management` <-> `payment_invoicing` | Estática: tipo e significado | Média | Entre componentes (distante) | 2 componentes | Ambos têm de concordar sobre os dados de uma viagem terminada (duração, tipo de utilizador, ID) e sobre o significado de cada campo (por exemplo, se a duração está em segundos ou em minutos). | Contrato explícito e tipado em `common` (uma classe imutável `TripSummary`)(`duration_minutes`). |
| 3 | `payment_invoicing` <-> `tariff_pricing` | Estática: posição e significado | Média | Entre componentes (distante) | 2 componentes | Se o cálculo do preço receber parâmetros soltos (`calc(52, "annual")`), os dois lados dependem da ordem dos argumentos e do significado do texto `"annual"`. | Passar um objeto tipado (`PricingRequest`) com enumeração para o tipo de passe. A conascência desce para nome e tipo, que são as formas mais fracas. |

## 7. Iteração de Reestruturação

Nesta iteração, **3 componentes iniciais genéricos foram desdobrados**:

```text
[ ARQUITETURA INICIAL ]                         [ REESTRUTURAÇÃO (REFINADA) ]

┌────────────────────────┐                    ┌───────────────────────────────┐
│     station_dock       │ ──(Tolerância)───> │ dock_gateway                  │
│  (Estações e Docas)    │ ──(Desempenho)───> │ station_inventory             │
└────────────────────────┘                    └───────────────────────────────┘

┌────────────────────────┐                     ┌───────────────────────────────┐
│    billing_payment     │ ──(Extensibilidade)>│ tariff_pricing                │
│  (Faturação e Cobrança)│ ──(Segurança)─>     │ payment_invoicing             │
└────────────────────────┘                     └───────────────────────────────┘

┌────────────────────────┐                    ┌───────────────────────────────┐
│   fleet_maintenance    │ ──(Escalabilidade)>│ fleet_health                  │
│  (Gestão Manutenção)   │ ──(Workflow)────>  │ maintenance_orders            │
└────────────────────────┘                    └───────────────────────────────┘