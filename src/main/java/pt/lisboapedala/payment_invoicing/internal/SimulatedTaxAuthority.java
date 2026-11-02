package pt.lisboapedala.payment_invoicing.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * AT simulada (ARCHITECTURE.md §0.3 e §7.4.2): aceita todas as faturas, sem cliente HTTP.
 * A referência deriva do número da fatura, por isso repetir a comunicação devolve a mesma referência.
 */
@Component
public class SimulatedTaxAuthority implements TaxAuthorityGateway {

    private static final Logger log = LoggerFactory.getLogger(SimulatedTaxAuthority.class);

    @Override
    public String submit(TaxDocument document) {
        String reference = "AT-" + document.invoiceNumber().replace(' ', '-');
        log.info("AT simulada: fatura {} comunicada ({})", document.invoiceNumber(), reference);
        return reference;
    }
}
