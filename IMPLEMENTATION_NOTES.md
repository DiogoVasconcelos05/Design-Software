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
