package pt.lisboapedala.payment_invoicing.persistence;

import java.util.Optional;

/**
 * Acesso à tabela {@code payment_methods}. Privado a payment_invoicing.
 */
public interface PaymentMethodRepository {

    /** Grava o meio de pagamento do utilizador, substituindo o anterior. */
    void save(PaymentMethodEntity method);

    Optional<PaymentMethodEntity> findByUser(String userId);
}
