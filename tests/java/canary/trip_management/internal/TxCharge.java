package canary.trip_management.internal;

import canary.payment_invoicing.port.Charger;
import org.springframework.transaction.annotation.Transactional;

/** Canário: cobra dentro da transação de fecho. */
@Transactional
public class TxCharge {
    void close(Charger charger) {
        charger.charge("trip-1");
    }
}
