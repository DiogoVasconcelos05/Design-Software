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