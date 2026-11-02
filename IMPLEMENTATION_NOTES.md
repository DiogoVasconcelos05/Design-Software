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

## payment_invoicing

* `PaymentService` passou a ter também `registerPaymentMethod` e `invoicesOf` (endpoints 3 e 7 do §7.3); as portas falsas nos testes de trip_management e dock_gateway foram atualizadas em conformidade.
* O cálculo do preço usa a porta `tariff_pricing.port.TariffPricing` com o `record` `PricingRequest` (docs/02, Conascência 3); só o contrato foi criado. `tariff_pricing.internal` é privado (FF-04a), por isso há `tariff_pricing.port`, embora a árvore do §7.2 só mostre `internal` (o §7.2.1 permite `port`). Até haver implementação, o contexto Spring não arranca (como já acontecia com outras portas).
* `PricingRequest` fica em `tariff_pricing.port` e não em `common`, para manter `common` pequeno (ADR-001, ADR-005).
* O preço vem em euros (`BigDecimal`) e é guardado em cêntimos (`INTEGER`), arredondado ao cêntimo com `HALF_UP`; os valores incluem IVA e a decomposição do IVA fica fora do âmbito.
* Idempotência (ADR-004 §4): `payment_charges.trip_id` é PRIMARY KEY e `invoices.trip_id` é UNIQUE (`INSERT OR IGNORE`); um segundo `charge` para a mesma viagem não faz nada.
* `charge` regista a cobrança e faz logo a primeira tentativa; depois de registada, uma recusa do PSP ou uma falha da AT não se propagam a trip_management (o outbox de payment_invoicing trata dos reenvios). Só uma falha a registar a cobrança se propaga, e aí o outbox de trip_management volta a chamar `charge`.
* `charge` tem `@Transactional(NOT_SUPPORTED)` e todas as escritas passam por `PaymentLedger` com `REQUIRES_NEW`: é chamado na fase after-commit do fecho, quando a transação original ainda está ligada à thread.
* O PSP é chamado fora de qualquer transação (FF-14b) com o `tripId` como chave de idempotência, por isso tentativas repetidas ou concorrentes (pedido inicial e job) nunca cobram duas vezes; não há estado intermédio "em curso".
* Marcar a cobrança `PAID` e emitir a fatura acontecem na mesma transação local, para não haver cobrança paga sem fatura; a comunicação à AT é feita a seguir, fora da transação.
* Uma viagem com valor 0 fica `PAID` sem chamar o PSP e sem fatura.
* Sem meio de pagamento registado, a cobrança fica `FAILED` e conta como tentativa; o job volta a tentar (ex.: depois de o utilizador registar um meio). payment_invoicing não pode usar identity_access, por isso o `userId` não é validado contra os utilizadores.
* Estados da cobrança: `PENDING`, `PAID`, `FAILED`. O job retoma `PENDING`/`FAILED` com menos de `lisboapedala.payment.max-attempts` tentativas (5 por omissão) e paradas há mais de um intervalo; esgotadas as tentativas, a cobrança fica `FAILED` para tratamento manual.
* As faturas com `at_status = PENDING` são reenviadas sem limite de tentativas (obrigação fiscal); a AT é idempotente pelo número da fatura.
* O intervalo do job vem de `lisboapedala.payment.retry-interval-seconds` (60 s por omissão), propriedade não especificada, por analogia com `lisboapedala.trip.billing-retry-interval-seconds`.
* O número da fatura é uma série sequencial única (`invoice_number INTEGER PRIMARY KEY AUTOINCREMENT`), apresentada como `FT LP/<n>`.
* As faturas nunca são apagadas (retenção de 10 anos, FF-23); depois de emitidas só mudam os campos da comunicação à AT.
* PSP e AT são simulados (`SimulatedPaymentGateway`, `SimulatedTaxAuthority`): aceitam tudo, só ficam no log e não usam cliente HTTP; atrás das interfaces `PaymentGateway` e `TaxAuthorityGateway` para poderem ser trocados por clientes reais (só payment_invoicing pode tê-los, FF-14a).
* O meio de pagamento é um por utilizador (registar outro substitui o anterior); o `token` é a referência opaca do PSP, nunca o número do cartão, e não vai para o log.
* `invoicesOf` aceita `limit` entre 1 e 100 e `offset >= 0`; fora disso dá `VALIDATION`. Ordena da mais recente para a mais antiga.

## tariff_pricing

* As regras concretas do Requisito 3 não estão em `ARCHITECTURE.md`, `docs/adr` nem `docs/diagramas`; só se sabe que há assinantes (`ANNUAL`) e ocasionais (`OCCASIONAL`) e que se cobra por blocos de 15 min (ADR-004 §5). Os valores de Lisboa são por isso **provisórios** e estão num único sítio (`TariffSchedule.lisboa()`): assinante com 45 min incluídos e 0,50 € por cada bloco de 15 min começado a seguir; ocasional com 1,00 € por cada bloco de 15 min começado.
* Os valores ficam no código e não em `application.properties`: a FF-10a só permite a `tariff_pricing` depender de `common`, do JDK puro e de `org.springframework.stereotype`, por isso não pode usar `@Value`/`@ConfigurationProperties`.
* Implementação de `TariffPricing` em `tariff_pricing.internal` (`DefaultTariffPricing`), com `TariffSchedule` (um tarifário por município, uma `TariffRule` por tipo de passe) e `BlockTariff` (minutos incluídos + preço por bloco começado); tudo `record`/campos `final`, sem relógio, I/O nem estado (FF-10).
* Um tarifário tem de cobrir todos os `PassType`; um tipo novo sem regra falha ao construir o tarifário, e não na cobrança.
* Um município vizinho é outra instância de `TariffSchedule` (ADR-003); `PricingRequest` não leva o município porque nenhum fluxo do §7.4 o fornece. Escolher o tarifário por município fica para quando houver essa informação na viagem.
* A duração chega já arredondada para cima ao minuto (`trip_management`); 0 minutos dá 0 € (nenhum bloco começado), que `payment_invoicing` trata como `PAID` sem PSP nem fatura.
* O valor devolvido tem no máximo 2 casas decimais, mas o arredondamento ao cêntimo continua a ser de `payment_invoicing` (`HALF_UP`).

## fleet_health

* A api precisa de chamar a entrada de telemetria, mas `fleet_health.internal` é privado (FF-04a); por isso há `fleet_health.port` (`TelemetryIngestion`, `TelemetryReading`), embora a árvore do §7.2 só mostre `{internal,persistence}` (o §7.2.1 permite `port`). `TelemetryService` (§7.4.3) é a implementação, em `internal`.
* A abertura de ordens usa a porta `maintenance_orders.port.MaintenanceService` (`open`, com `OpenMaintenanceOrder`, `FaultType`, `MaintenanceOrigin`, `MaintenanceOrderView`, `MaintenanceOrderStatus`); só os contratos foram criados, a implementação fica para maintenance_orders. Até haver implementação, o contexto Spring não arranca (como já acontecia com outras portas).
* `MaintenanceService.open` devolve `MaintenanceOrderView` porque o endpoint 13 responde com ela; os campos e os estados (`OPEN`, `ASSIGNED`, `IN_PROGRESS`, `COMPLETED`) foram deduzidos dos endpoints 13 a 17 e podem ser ajustados por maintenance_orders.
* O conteúdo de `TelemetryReading` não está especificado; assumiu-se `bikeId`, `occurredAt` (hora da bicicleta), `batteryTemperatureCelsius` e `brakeFault` (autodiagnóstico), o mínimo para as falhas de bateria e travões da US-2.
* Anomalias detetadas: `BATTERY_OVERHEAT` (temperatura ≥ `lisboapedala.fleet.battery-max-temperature-celsius`, 60 °C por omissão, valor provisório) → `FaultType.BATTERY`; `BRAKE_FAULT` (`brakeFault = true`) → `FaultType.BRAKES`. `FaultType` tem também `OTHER` para reportes de utilizadores.
* Cada deteção é um bean `AnomalyRule` (função pura da leitura); acrescentar uma deteção é acrescentar uma regra e um valor em `AnomalyType`, sem mexer em `TelemetryService`. Duas regras para o mesmo tipo falham no arranque.
* "Anomalia nova" (§7.4.3) é uma anomalia que não está ativa para a bicicleta: a tabela `fleet_active_anomalies` tem `PRIMARY KEY (bike_id, anomaly_type)` e `INSERT OR IGNORE`, por isso cada anomalia abre no máximo uma ordem enquanto estiver ativa, mesmo com várias réplicas. maintenance_orders continua a garantir uma só ordem ativa por `(bikeId, faultType)`.
* Uma anomalia fica resolvida quando uma leitura mais recente do que a deteção (`occurredAt`) já não a mostra; se reaparecer, pede nova ordem. Uma leitura antiga reenviada fora de ordem não a resolve.
* A telemetria não é guardada: cada leitura faz só uma consulta indexada às anomalias ativas e só escreve quando o estado muda, para que as leituras a cada 30 s não disputem o único escritor do SQLite com as viagens (cenário de escalabilidade em docs/01).
* Registar a anomalia e abrir a ordem acontecem na mesma transação local (`AnomalyRecorder`); se a abertura falhar, nada fica registado e a leitura seguinte da bicicleta volta a tentar, sem precisar de job de reenvio.
* `dock_gateway` não usa fleet_health (a permissão allowed-to-use continua por usar): a telemetria chega pelo endpoint 10, não pelos eventos das docas.
* Não é usado `Clock`: as horas vêm de `occurredAt` da leitura (como em ADR-004 §5 para as docas).
