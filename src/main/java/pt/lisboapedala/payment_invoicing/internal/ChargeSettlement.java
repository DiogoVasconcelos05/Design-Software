package pt.lisboapedala.payment_invoicing.internal;

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pt.lisboapedala.payment_invoicing.persistence.ChargeEntity;
import pt.lisboapedala.payment_invoicing.persistence.PaymentMethodEntity;
import pt.lisboapedala.payment_invoicing.persistence.PaymentMethodRepository;

/**
 * Uma tentativa de liquidar uma cobrança registada (ARCHITECTURE.md §7.4.2): PSP, fatura e AT.
 * O PSP é chamado fora de qualquer transação e com o tripId como chave de idempotência, por isso uma
 * tentativa repetida ou concorrente da mesma viagem nunca cobra duas vezes.
 */
@Component
public class ChargeSettlement {

    private static final Logger log = LoggerFactory.getLogger(ChargeSettlement.class);

    private final PaymentMethodRepository methods;
    private final PaymentGateway gateway;
    private final PaymentLedger ledger;
    private final InvoiceSubmitter submitter;

    public ChargeSettlement(PaymentMethodRepository methods, PaymentGateway gateway, PaymentLedger ledger,
                            InvoiceSubmitter submitter) {
        this.methods = methods;
        this.gateway = gateway;
        this.ledger = ledger;
        this.submitter = submitter;
    }

    void attempt(ChargeEntity charge) {
        if (charge.amountCents() == 0) {
            ledger.settle(charge, null);
            log.info("Viagem {} sem valor a cobrar", charge.tripId());
            return;
        }
        Optional<PaymentMethodEntity> method = methods.findByUser(charge.userId());
        if (method.isEmpty()) {
            fail(charge, "Utilizador sem meio de pagamento registado", null);
            return;
        }
        String pspReference;
        try {
            pspReference = gateway.collect(new PaymentOrder(charge.tripId(), method.get().kind(),
                    method.get().token(), charge.amountCents()));
        } catch (RuntimeException e) {
            fail(charge, reasonOf(e), e);
            return;
        }
        log.info("Viagem {} cobrada: {} cêntimos ({})", charge.tripId(), charge.amountCents(), pspReference);
        ledger.settle(charge, pspReference).ifPresent(submitter::submit);
    }

    private void fail(ChargeEntity charge, String reason, RuntimeException cause) {
        ledger.recordChargeFailure(charge.tripId(), reason);
        log.warn("Cobrança da viagem {} falhou (tentativa {}): {}", charge.tripId(), charge.attempts() + 1, reason,
                cause);
    }

    static String reasonOf(RuntimeException e) {
        return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    }
}
