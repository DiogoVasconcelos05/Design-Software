## 2. Decomposição de Característica Composta

**Característica Escolhida:** `Supportability` (Suportabilidade)

A suportabilidade é uma característica transversal essencial para garantir que as equipas técnicas conseguem diagnosticar, gerir e resolver anomalias na frota e no sistema sem interromper a operação. É decomposta nas seguintes sub-características:

* **Observabilidade:** Capacidade de registar eventos de telemetria, erros e transações com *correlation IDs* para reconstruir o estado do sistema através dos vários componentes.
* **Testabilidade:** Facilidade em isolar componentes e executar testes automatizados (*fitness functions* e testes de integração) para despistar falhas.
* **Analisabilidade:** Capacidade de correlacionar reportes manuais de utilizadores com alertas automáticos vindos das bicicletas elétricas (ex.: bateria/travões).
* **Monitorização em Tempo Real:** Facilidade de gerar alertas automáticos imediatos quando uma doca fica sem rede ou entra em estado crítico.