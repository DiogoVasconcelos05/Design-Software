# Características

## 1. Candidatas

### Implícitas
- Availability
    - "Cerca de 70% dos desbloqueios acontecem entre as 8h00–9h30 e as 17h30–19h00. Em
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
    - "O contrato de concessão aplica uma multa por cada hora em que mais de 10% das
estações não conseguem desbloquear bicicletas.
"

## Top 3
- Availability
    - Como o sistema inteiro depende do uptime dos servidores, a availability tem de ser superior a 99.9%.
- Scability
    - Como o sistema é desenhado para poder ser instalado em qualquer lugar e qualquer bicicleta é compatível com qualquer doca e utilizam todas o mesmo serviço, é crucial que o sistema seja "extensível" de forma a poder acomodar a expansão do serviço de forma a garantir o bom funcionamento de todos os componentes.
- On-Demand Elasticity
    - Como existem dias e períodos de tempo com pico de utilização e carga física e digital no sistema, é crucial que haja alocação dinâmica de recursos de forma a não bloquear os servidores e acomodar todos os potenciais utilizadores.

## 2. Decomposição de Característica Composta

**Característica Escolhida:** `Supportability` (Suportabilidade)

A suportabilidade é uma característica transversal essencial para garantir que as equipas técnicas conseguem diagnosticar, gerir e resolver anomalias na frota e no sistema sem interromper a operação. É decomposta nas seguintes sub-características:

* **Observabilidade:** Capacidade de registar eventos de telemetria, erros e transações com *correlation IDs* para reconstruir o estado do sistema através dos vários componentes.
* **Testabilidade:** Facilidade em isolar componentes e executar testes automatizados (*fitness functions* e testes de integração) para despistar falhas.
* **Analisabilidade:** Capacidade de correlacionar reportes manuais de utilizadores com alertas automáticos vindos das bicicletas elétricas (ex.: bateria/travões).
* **Monitorização em Tempo Real:** Facilidade de gerar alertas automáticos imediatos quando uma doca fica sem rede ou entra em estado crítico.
