package pt.lisboapedala.payment_invoicing.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acesso à tabela {@code invoices}. Privado a payment_invoicing. Não há operação de remoção: as faturas
 * são guardadas durante 10 anos (FF-23).
 */
public interface InvoiceRepository {

    /**
     * Grava a fatura se a viagem ainda não tiver nenhuma (ADR-004 §4).
     *
     * @return false se a viagem já estava faturada
     */
    boolean insertIfAbsent(InvoiceEntity invoice);

    Optional<InvoiceEntity> findByTripId(UUID tripId);

    /** Faturas do utilizador, mais recente primeiro. */
    List<InvoiceEntity> findByUser(String userId, int limit, int offset);

    void markSubmitted(UUID invoiceId, String atReference, Instant at);

    void markSubmissionFailed(UUID invoiceId, String error, Instant at);

    /** Faturas PENDING cuja última tentativa (ou emissão) foi antes de {@code cutoff}. */
    List<InvoiceEntity> findPendingSubmission(Instant cutoff, int limit);
}
