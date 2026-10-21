# Componentes

## 1. Abordagem
A abordagem escolhida foi Actor/Action para realizar a primeira identificação dos componentes lógicos. 

### Justificação:
* **Diversidade de Perfis:** O sistema interage com atores com responsabilidades muito distintas: Assinantes, Utilizadores Ocasionais, Técnicos de Manutenção, Equipas de Carrinhas e Gestores Municipais. Mapear os componentes pelas ações de cada ator garante que a lógica de manutenção ou relatórios municipais não se mistura com o fluxo crítico de desbloqueio.

## 2. Atores, Ações e Componentes Identificados

| Ator | Ação / Evento no Sistema | Componente Lógico Resultante | Responsabilidade Única (1 Frase) |
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

| Referência | User Story / Requisito do Enunciado | Componente Principal | Componentes Secundários Envolvidos |
| :--- | :--- | :--- | :--- |
| **US-1** *(Obrigatória)* | Como **assinante**, desbloqueio e devolvo uma bicicleta e a viagem fica registada e cobrada. | `trip_management` | `identity_access`, `dock_gateway`, `tariff_pricing`, `payment_invoicing` |
| **US-2** | Como **técnico**, recebo uma ordem de manutenção quando uma bicicleta reporta avaria. | `maintenance_orders` | `fleet_health`, `dock_gateway` |
| **US-3** | Como **doca sem rede**, reenvio os eventos guardados quando a rede volta, sem duplicar viagens. | `dock_gateway` | `trip_management` |
| **US-4** | Como **gestor municipal**, obtenho o relatório de utilização por freguesia. | `municipal_reporting` | `trip_management`, `station_inventory` |
| *(Req. 4)* | Como **utilizador**, consulto o mapa em tempo real com bicicletas e docas livres. | `station_inventory` | `dock_gateway` |
| *(Req. 6)* | Como **equipa de carrinha**, recebo sugestões de rotas para equilibrar a ocupação das estações. | `redistribution_planning` | `station_inventory` |
| *(Req. 8)* | Como **utilizador**, consulto o meu histórico de viagens e as faturas emitidas. | `identity_access` | `trip_management`, `payment_invoicing` |

## 4. Análise de Coesão, Acoplamento e Instabilidade

| Componente | Nível de Coesão | $C_A$ | $C_E$ | Instabilidade ($I$) | Justificação Arquitetural |
| :--- | :--- | :---: | :---: | :---: | :--- |
| **`user_account`** | **Funcional** | **2** | **0** | **0.00** *(Altamente Estável)* | **Coesão:** Apresenta coesão funcional porque todas as suas operações servem unicamente para gerir a identidade e estado de subscrição dos utilizadores. Passa no *teste das conjunções* (não mistura "e também calcula pagamentos").<br>**Acoplamento:** É consultado pelo `trip_management` (verificar se o passe está ativo) e pelo `billing_payment` (obter dados fiscais) ($C_A = 2$). Não depende de nenhum outro componente lógico ($C_E = 0$).<br>**Papel:** Funciona como componente de núcleo/estável na arquitetura. |
| **`trip_management`** | **Funcional** | **2** | **2** | **0.50** *(Equilibrado)* | **Coesão:** Trata exclusivamente do ciclo de vida da viagem (início, andamento, fim).<br>**Acoplamento:** Fornece dados ao `billing_payment` e ao `reporting_analytics` ($C_A = 2$). Para autorizar/fechar viagens, necessita de consultar a elegibilidade do utilizador no `user_account` e a confirmação do encaixe físico no `station_dock` ($C_E = 2$).<br>**Papel:** Nó intermédio de orquestração do fluxo de utilização. |
| **`billing_payment`** | **Funcional** | **1** | **1** | **0.50** *(Equilibrado)* | **Coesão:** Responsável unicamente pela tarifação, cobrança financeira e integração com a Autoridade Tributária.<br>**Acoplamento:** É invocado pelo `trip_management` para liquidação no fim da viagem ($C_A = 1$) e consulta dados de faturação no `user_account` ($C_E = 1$).<br>**Papel:** Módulo isolado de liquidação, permitindo alterar gateways de pagamento sem afetar a lógica de desbloqueio de bicicletas. |

| Componente | Nível de Coesão | $C_A$ | $C_E$ | Instabilidade ($I$) | Justificação Arquitetural |
| :--- | :--- | :---: | :---: | :---: | :--- |
| `payment_invoicing` | Sequencial | 2 | 2 | 0,50 | Cobrar, emitir a fatura, guardá-la e comunicá-la à AT são passos em que a saída de um alimenta o seguinte. Depende de `tariff_pricing` e `common`; é usado por `trip_management` e `api`. Está no limite: se crescer (por exemplo, mais fornecedores de pagamento), separar a parte fiscal. |
| `tariff_pricing` | Funcional | 2 | 1 | 0,33 | Um único propósito: calcular o preço de uma viagem a partir de um tarifário. Função pura, sem estado, só depende de `common`. Estável, o que permite acrescentar tarifários das câmaras vizinhas sem tocar noutros componentes. |
| `fleet_health` | Comunicacional | 2 | 2 | 0,50 | Receber a telemetria e detetar avarias operam sobre os mesmos dados (as leituras da frota). Depende de `maintenance_orders` para abrir ordens; é usado por `dock_gateway` e `api`. |
| `maintenance_orders` | Funcional | 2 | 1 | 0,33 | Só abre e acompanha ordens de manutenção. Não conhece a origem da avaria (automática ou reportada por um utilizador), o que o mantém estável e fácil de testar. |
| `redistribution_planning` | Funcional | 1 | 2 | 0,67 | Só sugere rotas, a partir da ocupação lida em `station_inventory`. Nada depende dele a não ser a `api`, por isso pode mudar de algoritmo à vontade. Instabilidade alta é aceitável aqui. |
| `municipal_reporting` | Comunicacional | 1 | 3 | 0,75 | Agrega a utilização por freguesia e o CO₂ evitado, ambos sobre os dados das viagens. Só lê, e depende de `trip_management`, `station_inventory` e `common`. É o mais instável deste grupo, o que é aceitável: ninguém depende dele, e o relatório muda com as exigências da Câmara. |
| `common` | Lógica (baixa) | 11 | 0 | 0,00 | Reúne tipos e eventos partilhados sem relação funcional entre si, por isso a coesão é baixa por natureza. Compensa-se com a estabilidade total ($C_E = 0$). Todos os componentes o usam, logo cada alteração tem custo alto: mantê-lo pequeno, sem lógica de negócio. |
| `api` | Lógica | 0 | 11 | 1,00 | Ponto de entrada que agrupa os pedidos externos e compõe os componentes; não tem lógica de negócio. Instabilidade máxima é esperada e correta: depende de todos, ninguém depende dele (regra de dependências 1). |

## 5. Acoplamento Temporal

O fluxo transacional da **US-1 (Desbloqueio e Cobrança de Viagem)** exige o cumprimento de duas regras estritas de **acoplamento temporal** (ordem sequencial de execução necessária):

1. **Validação Preliminar do Desbloqueio:**
   O `trip_management` necessita de invocar sincronicamente o `user_account` para validar a validade da anuidade/passe **antes** de emitir a ordem de abertura física à doca em `station_dock`. Se a ordem for invertida, a bicicleta pode ser libertada para um utilizador sem passe válido ou suspenso.
2. **Fecho da Transação e Cobrança:**
   A devolução da bicicleta (confirmada pelo `station_dock`) obriga a que o `trip_management` determine a duração exata da viagem **antes** de disparar o cálculo e cobrança de blocos de 15 minutos em `billing_payment`. A cobrança e a emissão de fatura constituem a unidade final da transação de devolução.

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