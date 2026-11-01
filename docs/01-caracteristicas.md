# Características

## 1. Candidatas

### Implícitas
- Availability
    - "O contrato de concessão aplica uma multa por cada hora em que mais de 10% das
estações não conseguem desbloquear bicicletas." e "Cerca de 70% dos desbloqueios acontecem entre as 8h00–9h30 e as 17h30–19h00. Em
eventos (Santos Populares, maratona) a procura em algumas estações multiplica por 4."
- Security
    - "As faturas são comunicadas à Autoridade Tributária e guardadas durante 10 anos. O
percurso de cada viagem é dado pessoal (RGPD).
"
- Privacy
    - "As faturas são comunicadas à Autoridade Tributária e guardadas durante 10 anos. O
percurso de cada viagem é dado pessoal (RGPD).
"
- Extensibility
    - "Duas câmaras vizinhas mostraram interesse em usar a plataforma com tarifários
próprios.
"

### Explícitas
- Scability
    - "Cerca de 70% dos desbloqueios acontecem entre as 8h00–9h30 e as 17h30–19h00. Em
eventos (Santos Populares, maratona) a procura em algumas estações multiplica por 4".
- On-Demand Elasticity
    - "Cerca de 70% dos desbloqueios acontecem entre as 8h00–9h30 e as 17h30–19h00. Em
eventos (Santos Populares, maratona) a procura em algumas estações multiplica por 4".
- Supportability
    - "Abrir uma ordem de manutenção quando uma bicicleta reporta avaria (bateria, travões) ou um utilizador a reporta."

## Top 3 Características e Cenários Concretos

### 1. Availability (Disponibilidade)
* **Justificação:** O sistema tem um impacto operacional crítico na mobilidade urbana de Lisboa e obrigações contratuais severas ("multa por cada hora em que mais de 10% das estações não conseguem desbloquear bicicletas").
* **Cenário Concreto:** 
  * **Fonte do Estímulo:** Falha temporária de rede móvel numa estação de docas durante a hora de ponta.
  * **Estímulo:** Uma doca perde a ligação à Internet enquanto vários assinantes tentam devolver ou desbloquear bicicletas.
  * **Ambiente:** Operação normal em pico matinal (8h00–9h30).
  * **Artefacto Afetado:** Módulos `dock_gateway` e `trip_management`.
  * **Resposta do Sistema:** A doca opera em modo autónomo/offline, registando e validando as devoluções localmente no seu armazenamento de 24h sem depositar erros ao utilizador. Assim que a rede é restabelecida, os eventos acumulados são reenviados para o `dock_gateway` sem duplicar registos de viagem.
  * **Medida de Resposta:** O serviço de desbloqueio/devolução mantém-se disponível a 100% para o utilizador na estação, garantindo que o limiar de 10% de estações indisponíveis nunca é atingido e evitando sanções contratuais.

---

### 2. On-Demand Elasticity (Elasticidade Sob Procura)
* **Justificação:** A procura digital e física flutua drasticamente ao longo do dia (70% das viagens ocorrem em janelas de 1h30) e sofre picos extraordinários durante eventos da cidade (multiplicando a procura por 4).
* **Cenário Concreto:**
  * **Fonte do Estímulo:** Início de um evento de grande dimensão em Lisboa (ex.: Santos Populares ou Maratona de Lisboa).
  * **Estímulo:** O volume de pedidos simultâneos de consulta de mapa, validação de passes e pedidos de desbloqueio numa determinada área geográfica aumenta 400% em menos de 15 minutos.
  * **Ambiente:** Carga do sistema passa repentinamente do estado *baseline* para carga máxima de pico.
  * **Artefacto Afetado:** Módulos `station_inventory`, `identity_access` e `trip_management`.
  * **Resposta do Sistema:** O sistema aloca dinamicamente mais recursos computacionais/instâncias nos componentes de consulta e validação para absorver o fluxo de requisições sem degradar a base de dados.
  * **Medida de Resposta:** As operações de leitura de ocupação e validação de QR Code/Navegante mantêm um tempo de resposta (latência) inferior a 1,5 segundos (p99), sem qualquer perda de pedidos ou indisponibilidade do serviço.

---

### 3. Scalability (Escalabilidade)
* **Justificação:** A plataforma deve suportar o crescimento progressivo da frota (1 500 bicicletas, 180 estações), o aumento da base de utilizadores (60 000 assinantes + 15 000 ocasionais/mês) e a futura adesão de municípios vizinhos com tarifários próprios.
* **Cenário Concreto:**
  * **Fonte do Estímulo:** Expansão da rede para duas câmaras municipais vizinhas e aumento da frota e da base de utilizadores em 50%.
  * **Estímulo:** Ingestão contínua de telemetria a cada 30s de centenas de novas bicicletas elétricas combinada com a adição de novas regras tarifárias específicas por município no `tariff_pricing`.
  * **Ambiente:** Crescimento estrutural e contínuo do sistema a médio/longo prazo.
  * **Artefacto Afetado:** Módulos `fleet_health`, `tariff_pricing` e modelo de dados de persistência.
  * **Resposta do Sistema:** O particionamento por domínio e o isolamento funcional do cálculo de tarifários permitem integrar as regras dos novos municípios e absorver o débito acrescido de telemetria sem necessidade de reestruturar o core do sistema.
  * **Medida de Resposta:** O débito de ingestão de telemetria (IoT) escala linearmente sem impactar o desempenho transacional das viagens (`trip_management`), mantendo o custo operacional e de computação proporcional ao crescimento da frota.

## 2. Decomposição de Característica Composta

**Característica Escolhida:** `Supportability` (Suportabilidade)

A suportabilidade é uma característica transversal essencial para garantir que as equipas técnicas conseguem diagnosticar, gerir e resolver anomalias na frota e no sistema sem interromper a operação. É decomposta nas seguintes sub-características:

* **Observabilidade:** Capacidade de registar eventos de telemetria, erros e transações com *correlation IDs* para reconstruir o estado do sistema através dos vários componentes.
* **Testabilidade:** Facilidade em isolar componentes e executar testes automatizados (*fitness functions* e testes de integração) para despistar falhas.
* **Analisabilidade:** Capacidade de correlacionar reportes manuais de utilizadores com alertas automáticos vindos das bicicletas elétricas (ex.: bateria/travões).
* **Monitorização em Tempo Real:** Facilidade de gerar alertas automáticos imediatos quando uma doca fica sem rede ou entra em estado crítico.
