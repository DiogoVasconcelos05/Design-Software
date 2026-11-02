package pt.lisboapedala.payment_invoicing.internal;

/**
 * Autoridade Tributária (docs/diagramas C4 nível 1 a 3): recebe a comunicação das faturas emitidas.
 */
public interface TaxAuthorityGateway {

    /**
     * Comunica a fatura. Idempotente por {@link TaxDocument#invoiceNumber()}.
     *
     * @return referência atribuída pela AT
     * @throws RuntimeException se a AT recusar ou não responder
     */
    String submit(TaxDocument document);
}
