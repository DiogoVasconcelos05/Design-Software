# Notas de implementação

Suposições feitas pela IA (uma linha cada), por módulo.

## trip_management

* `DockCommandPort` é definida em `trip_management.port` e implementada por `dock_gateway` (inversão de dependências): `dock_gateway` pode usar `trip_management`, mas o contrário criaria um ciclo (FF-03) e viola a matriz allowed-to-use.
* A validação do passe usa a porta `identity_access.port.CredentialValidator` e a cobrança a porta `payment_invoicing.port.PaymentService`; só os contratos foram criados, as implementações ficam para os respetivos módulos.
* Enquanto `identity_access`, `payment_invoicing` e `dock_gateway` não implementarem essas portas, o contexto Spring não arranca (faltam beans); os testes de `trip_management` usam implementações falsas.
* `Credential`/`CredentialKind` (pedido do endpoint 4), `PassType` e `TripSummary` (conascência 2 em docs/02) ficam em `common`, ao lado de `PassValidation`, por serem contratos partilhados entre módulos.
* `PassValidation` ganhou o campo `passType` (obrigatório quando `VALID`), porque o `TripSummary` tem de levar o tipo de utilizador e `trip_management` só o obtém na validação.
* `durationMinutes` é a duração `occurredAt(BIKE_DOCKED) - occurredAt(BIKE_RELEASED)` arredondada para cima ao minuto; um fim anterior ao início (relógio da doca) dá 0.
* `startTrip` recebe o `tripId` (o `commandId` do comando de abertura) à parte, porque `DockEventRecord` não tem `commandId`.
* Um `BIKE_RELEASED` para uma viagem `EXPIRED` reativa-a (`IN_PROGRESS`): o evento reenviado após falha de rede (US-3) prova que a bicicleta saiu.
* Um `BIKE_RELEASED` de uma doca diferente da do pedido, ou para uma viagem já em curso/fechada com outro `eventId`, é `UNMATCHED`.
* O `BIKE_DOCKED` corresponde à viagem `IN_PROGRESS` da mesma bicicleta; sem viagem em curso devolve `UNMATCHED`.
* Idempotência (ADR-004 §3): `start_event_id` e `close_event_id` têm `UNIQUE`; além disso cada transição grava com `WHERE status = <esperado>`, e um segundo evento igual devolve `DUPLICATE` sem efeitos.
* A cobrança é pedida por um `@TransactionalEventListener(AFTER_COMMIT)` e `billing_dispatched` é marcado numa transação nova (`REQUIRES_NEW`), porque escritas na fase after-commit não seriam persistidas.
* Se `PaymentService.charge` lançar exceção, a viagem fica com `billing_dispatched = 0` e o job de outbox volta a tentar; reenviar é seguro porque a cobrança é idempotente por `tripId`.
* `requestUnlock` não é transacional: a viagem fica gravada antes de o comando de abertura ser enviado, para que um `BIKE_RELEASED` rápido a encontre.
* Se o envio do comando de abertura falhar, a viagem passa logo a `EXPIRED` e o erro é propagado (ex.: `DOCK_UNAVAILABLE`), para o utilizador poder tentar noutra doca.
* Um utilizador só pode ter uma viagem ativa (`REQUESTED` ou `IN_PROGRESS`); um segundo desbloqueio dá `CONFLICT`.
* O job de expiração corre a cada 15 s (intervalo fixo, não especificado); os prazos vêm de `lisboapedala.trip.unlock-timeout-seconds` e `lisboapedala.trip.billing-retry-interval-seconds` (60 s por omissão em `application.properties`).
* `@EnableScheduling` e o bean `Clock` (UTC) ficam em `LisboaPedalaApplication`, por serem infraestrutura partilhada por todos os módulos.
* Os instantes são guardados em SQLite como milissegundos desde a época (`INTEGER`), para que a ordenação e as comparações por intervalo sejam numéricas.
* `history` e `TripUsageQuery` aceitam `limit` entre 1 e 100 e `offset >= 0`; fora disso dão `VALIDATION`.
* `TripUsageRecord` usa as docas de origem e destino (o mapeamento doca → estação/freguesia é de `station_inventory`) e o `bikeId`; o tipo de bicicleta e a distância não vêm nos eventos das docas, por isso ficam de fora até haver fonte para eles.
* Um `BIKE_DOCKED` que chegue antes do `BIKE_RELEASED` da mesma viagem (reenvio fora de ordem) fica `UNMATCHED`; tratar essa ordem fica para `dock_gateway`/evolução futura.

## dock_gateway

* A api precisa de chamar a entrada de eventos, mas `dock_gateway.internal` é privado (FF-04a); por isso há `dock_gateway.port` (`DockEventIngestion`, `DockEventSubmission`, `IngestOutcome`), embora a árvore do §7.2 só mostre `{internal,persistence}` (o §7.2.1 permite `port`).
* Os endpoints `POST /dock-events*` (8 e 9) não estão na tabela do §7.3; assumiu-se um evento único (`ingest`) e um lote para o reenvio do buffer offline (`ingestBatch`, máx. 1000 eventos).
* `DockEventRecord` não tem `commandId`; `DockEventSubmission` leva-o à parte (só usado em `BIKE_RELEASED`, como o `tripId` de `startTrip`).
* A escrita em station_inventory usa a porta `station_inventory.port.DockOccupancyPort` (só o contrato); station_inventory não pode usar dock_gateway, por isso a interface fica no módulo dono. Até haver implementação, o contexto Spring não arranca (como já acontecia com outras portas).
* As operações de `DockOccupancyPort` definem estado (livre/ocupada/bloqueada) e levam `occurredAt`, para serem seguras de repetir e para o dono poder ignorar atualizações mais antigas.
* `DOCK_MAINTENANCE_LOCKED`/`UNLOCKED` só bloqueiam/desbloqueiam a doca em station_inventory; fleet_health não é usado (permissão allowed-to-use fica por usar).
* Desduplicação (ADR-004 §2): tabela `dock_processed_events` com `event_id` PRIMARY KEY e `INSERT OR IGNORE`; registo, station_inventory e trip_management correm na mesma transação, por isso uma falha desfaz o registo e o reenvio volta a processar o evento.
* No encaixe, a doca é marcada ocupada antes de `closeTrip` (§4: consolidar antes da cobrança); na saída, `startTrip` é chamado antes de `markDockFree` (ordem de §7.4.1).
* A doca é atualizada mesmo quando o evento fica `UNMATCHED`: a bicicleta saiu/entrou fisicamente, haja ou não viagem.
* Um `BIKE_RELEASED` sem `commandId` fica `UNMATCHED` sem chamar trip_management.
* Um `DUPLICATE` devolvido por trip_management (segunda linha de defesa) é também `DUPLICATE` para a doca.
* Um lote é aplicado por ordem de `occurredAt`, cada evento na sua transação (não bloqueia o único escritor do SQLite); os resultados voltam pela ordem recebida. Isto resolve o `BIKE_DOCKED` antes do `BIKE_RELEASED` dentro do mesmo lote, mas não entre pedidos distintos.
* Se um evento de um lote falhar, a exceção propaga-se; os já processados ficam registados e no reenvio do lote dão `DUPLICATE`.
* O comando de abertura é simulado (`SimulatedDockCommandSender`): só fica no log, sem cliente HTTP (FF-14a) nem tabela de comandos; só falha com `VALIDATION` se faltar `dockId`/`tripId`.
* `received_at` (hora do servidor, via `Clock`) só serve para a purga; as durações usam `occurredAt` (ADR-004 §5).
* A purga corre de hora a hora (intervalo não especificado); a retenção vem de `lisboapedala.dock.processed-event-retention-hours` (48 por omissão).
