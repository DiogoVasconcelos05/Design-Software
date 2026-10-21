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
| **`user_account`** | **Funcional** | **2** | **0** | **0.00** *(Altamente Estável)* | **Coesão:** Apresenta coesão funcional porque todas as suas operações servem unicamente para gerir a identidade e estado de subscrição dos utilizadores. Passa no *teste das conjunções* (não mistura "e também calcula pagamentos").<br>**Acoplamento:** É consultado pelo `trip_management` (verificar se o passe está ativo) e pelo `billing_payment` (obter dados fiscais) ($C_A = 2$). Não depende de nenhum outro componente lógico ($C_E = 0$).<br>**Papel:** Funciona como componente de núcleo/estável na arquitetura. |
| **`trip_management`** | **Funcional** | **2** | **2** | **0.50** *(Equilibrado)* | **Coesão:** Trata exclusivamente do ciclo de vida da viagem (início, andamento, fim).<br>**Acoplamento:** Fornece dados ao `billing_payment` e ao `reporting_analytics` ($C_A = 2$). Para autorizar/fechar viagens, necessita de consultar a elegibilidade do utilizador no `user_account` e a confirmação do encaixe físico no `station_dock` ($C_E = 2$).<br>**Papel:** Nó intermédio de orquestração do fluxo de utilização. |
| **`billing_payment`** | **Funcional** | **1** | **1** | **0.50** *(Equilibrado)* | **Coesão:** Responsável unicamente pela tarifação, cobrança financeira e integração com a Autoridade Tributária.<br>**Acoplamento:** É invocado pelo `trip_management` para liquidação no fim da viagem ($C_A = 1$) e consulta dados de faturação no `user_account` ($C_E = 1$).<br>**Papel:** Módulo isolado de liquidação, permitindo alterar gateways de pagamento sem afetar a lógica de desbloqueio de bicicletas. |

## 5. Acoplamento Temporal

O fluxo transacional da **US-1 (Desbloqueio e Cobrança de Viagem)** exige o cumprimento de duas regras estritas de **acoplamento temporal** (ordem sequencial de execução necessária):

1. **Validação Preliminar do Desbloqueio:**
   O `trip_management` necessita de invocar sincronicamente o `user_account` para validar a validade da anuidade/passe **antes** de emitir a ordem de abertura física à doca em `station_dock`. Se a ordem for invertida, a bicicleta pode ser libertada para um utilizador sem passe válido ou suspenso.
2. **Fecho da Transação e Cobrança:**
   A devolução da bicicleta (confirmada pelo `station_dock`) obriga a que o `trip_management` determine a duração exata da viagem **antes** de disparar o cálculo e cobrança de blocos de 15 minutos em `billing_payment`. A cobrança e a emissão de fatura constituem a unidade final da transação de devolução.

## 6. Conascencia

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