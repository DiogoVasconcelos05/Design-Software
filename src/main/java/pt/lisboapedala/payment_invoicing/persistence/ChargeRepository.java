package pt.lisboapedala.payment_invoicing.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acesso à tabela {@code payment_charges}. Privado a payment_invoicing.
 */
public interface ChargeRepository {

    /**
     * Grava a cobrança se ainda não existir nenhuma para a viagem (ADR-004 §4).
     *
     * @return false se a viagem já tinha cobrança
     */
    boolean insertIfAbsent(ChargeEntity charge);

    Optional<ChargeEntity> findByTripId(UUID tripId);

    /** Marca PAID e conta a tentativa; não faz nada se já estiver PAID. */
    boolean markPaid(UUID tripId, String pspReference, Instant at);

    /** Marca FAILED e conta a tentativa; não faz nada se já estiver PAID. */
    void markFailed(UUID tripId, String error, Instant at);

    /** Cobranças PENDING ou FAILED com menos de {@code maxAttempts} tentativas, paradas desde antes de {@code cutoff}. */
    List<ChargeEntity> findRetryable(int maxAttempts, Instant cutoff, int limit);
}
