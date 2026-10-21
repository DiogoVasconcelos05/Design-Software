# Componentes

## 1. Abordagem
A abordagem escolhida foi Actor/Action para realizar a primeira identificação dos componentes lógicos. 

### Justificação:
* **Diversidade de Perfis:** O sistema interage com atores com responsabilidades muito distintas: Assinantes, Utilizadores Ocasionais, Técnicos de Manutenção, Equipas de Carrinhas e Gestores Municipais. Mapear os componentes pelas ações de cada ator garante que a lógica de manutenção ou relatórios municipais não se mistura com o fluxo crítico de desbloqueio.

## 2. Atores, Ações e Componentes Identificados

| Ator | Ação no Sistema | Componente Lógico Resultante | Responsabilidade Única (1 Frase) |
| :--- | :--- | :--- | :--- |
| **Assinante / Ocasional** | Registar conta, gerir passe anual, consultar histórico e faturas | `user_account` | Gestão de perfis de utilizadores, subscrições de passes anuais e histórico pessoal. |
| **Assinante / Ocasional** | Solicitar desbloqueio da bicicleta (QR/Navegante) e terminar viagem | `trip_management` | Controlo do ciclo de vida das viagens, validação de autorização e registo do percurso. |
| **Doca / Estação (Ator Físico)** | Validar encaixe, monitorizar ocupação, operar em modo offline e retransmitir eventos | `station_dock` | Gestão do estado físico das estações, estado das docas e sincronização autónoma. |
| **Sistema Central (Ator)** | Calcular tarifário (45 min grátis / blocos 15 min), cobrar via MB WAY/cartão e emitir faturas à AT | `billing_payment` | Processamento do cálculo de tarifas, integração com gateways de pagamento e emissão fiscal. |
| **Técnico de Manutenção** | Registar avaria manual ou receber alerta automático (bateria/travões) e gerir ordens | `fleet_maintenance` | Gestão de ordens de trabalho de manutenção e estado de operacionalidade da frota. |
| **Equipa de Carrinha** | Consultar sugestões de rotas para equilibrar a ocupação das estações | `rebalancing` | Otimização e recomendação de rotas de redistribuição de bicicletas entre estações. |
| **Gestor Municipal** | Gerar relatórios mensais por freguesia e calcular CO₂ evitado | `reporting_analytics` | Agregação analítica e geração de relatórios de utilização para a Câmara Municipal. |

## 3. Mapeamento User Story → Componente

| User Story do Enunciado | Componente Principal | Componentes Secundários Envolvidos |
| :--- | :--- | :--- |
| **US-1:** Desbloquear e devolver bicicleta com registo e cobrança | `trip_management` | `user_account`, `station_dock`, `billing_payment` |
| **US-2:** Receber ordem de manutenção quando a bicicleta reporta avaria | `fleet_maintenance` | `station_dock` |
| **US-3:** Doca sem rede reenviar eventos guardados sem duplicar viagens | `station_dock` | `trip_management` |
| **US-4:** Gestor municipal obter relatório por freguesia e CO₂ | `reporting_analytics` | `trip_management`, `station_dock` |
| **US-5:** Consultar mapa em tempo real de bicicletas e docas livres | `station_dock` | - |
| **US-6:** Sugerir rotas às carrinhas para equilibrar a ocupação | `rebalancing` | `station_dock` |
| **US-7:** Consultar histórico de viagens e faturas | `user_account` | `trip_management`, `billing_payment` |

## 4. Análise de Coesão, Acoplamento e Instabilidade

| Componente | Nível de Coesão | $C_A$ | $C_E$ | Instabilidade ($I$) | Justificação Arquitetural |
| :--- | :--- | :---: | :---: | :---: | :--- |
| **`user_account`** | **Funcional** | **2** | **0** | **0.00** *(Altamente Estável)* | **Coesão:** Apresenta coesão funcional porque todas as suas operações servem unicamente para gerir a identidade e estado de subscrição dos utilizadores. Passa no *teste das conjunções* (não mistura "e também calcula pagamentos").<br>**Acoplamento:** É consultado pelo `trip_management` (verificar se o passe está ativo) e pelo `billing_payment` (obter dados fiscais) ($C_A = 2$). Não depende de nenhum outro componente lógico ($C_E = 0$).<br>**Papel:** Funciona como componente de núcleo/estável na arquitetura. |
| **`trip_management`** | **Funcional** | **2** | **2** | **0.50** *(Equilibrado)* | **Coesão:** Trata exclusivamente do ciclo de vida da viagem (início, andamento, fim).<br>**Acoplamento:** Fornece dados ao `billing_payment` e ao `reporting_analytics` ($C_A = 2$). Para autorizar/fechar viagens, necessita de consultar a elegibilidade do utilizador no `user_account` e a confirmação do encaixe físico no `station_dock` ($C_E = 2$).<br>**Papel:** Nó intermédio de orquestração do fluxo de utilização. |
| **`billing_payment`** | **Funcional** | **1** | **1** | **0.50** *(Equilibrado)* | **Coesão:** Responsável unicamente pela tarifação, cobrança financeira e integração com a Autoridade Tributária.<br>**Acoplamento:** É invocado pelo `trip_management` para liquidação no fim da viagem ($C_A = 1$) e consulta dados de faturação no `user_account` ($C_E = 1$).<br>**Papel:** Módulo isolado de liquidação, permitindo alterar gateways de pagamento sem afetar a lógica de desbloqueio de bicicletas. |

## 4. Acoplamento Temporal

O fluxo transacional da **US-1 (Desbloqueio e Cobrança de Viagem)** exige o cumprimento de duas regras estritas de **acoplamento temporal** (ordem sequencial de execução necessária):

1. **Validação Preliminar do Desbloqueio:**
   O `trip_management` necessita de invocar sincronicamente o `user_account` para validar a validade da anuidade/passe **antes** de emitir a ordem de abertura física à doca em `station_dock`. Se a ordem for invertida, a bicicleta pode ser libertada para um utilizador sem passe válido ou suspenso.
2. **Fecho da Transação e Cobrança:**
   A devolução da bicicleta (confirmada pelo `station_dock`) obriga a que o `trip_management` determine a duração exata da viagem **antes** de disparar o cálculo e cobrança de blocos de 15 minutos em `billing_payment`. A cobrança e a emissão de fatura constituem a unidade final da transação de devolução.
## Conascencia

## Iteração de Reestruturação
