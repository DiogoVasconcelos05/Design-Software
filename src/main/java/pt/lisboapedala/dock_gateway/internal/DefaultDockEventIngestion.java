package pt.lisboapedala.dock_gateway.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;
import org.springframework.stereotype.Service;
import pt.lisboapedala.common.exception.BusinessRuleException;
import pt.lisboapedala.common.exception.ErrorCode;
import pt.lisboapedala.dock_gateway.port.DockEventIngestion;
import pt.lisboapedala.dock_gateway.port.DockEventSubmission;
import pt.lisboapedala.dock_gateway.port.IngestOutcome;

/**
 * Entrada de eventos das docas (US-1, US-3). Sem transação própria: cada evento é processado pelo
 * {@link DockEventProcessor} na sua transação, para que um lote grande não bloqueie o único escritor do SQLite
 * e um evento problemático não desfaça os anteriores.
 */
@Service
public class DefaultDockEventIngestion implements DockEventIngestion {

    /** Um buffer de 24 h de uma doca cabe folgadamente neste limite. */
    static final int MAX_BATCH_SIZE = 1000;

    private final DockEventProcessor processor;

    public DefaultDockEventIngestion(DockEventProcessor processor) {
        this.processor = processor;
    }

    @Override
    public IngestOutcome ingest(DockEventSubmission submission) {
        if (submission == null) {
            throw new BusinessRuleException(ErrorCode.VALIDATION, "O evento da doca é obrigatório");
        }
        return processor.process(submission);
    }

    /**
     * Os eventos são aplicados por ordem de {@code occurredAt}, para que um reenvio desordenado não aplique um
     * BIKE_DOCKED antes do BIKE_RELEASED da mesma viagem. Se um evento falhar, a exceção propaga-se: os já
     * processados ficam registados e no reenvio do lote serão DUPLICATE.
     */
    @Override
    public List<IngestOutcome> ingestBatch(List<DockEventSubmission> submissions) {
        if (submissions == null || submissions.isEmpty() || submissions.stream().anyMatch(Objects::isNull)) {
            throw new BusinessRuleException(ErrorCode.VALIDATION, "O lote tem de ter pelo menos um evento e nenhum nulo");
        }
        if (submissions.size() > MAX_BATCH_SIZE) {
            throw new BusinessRuleException(ErrorCode.VALIDATION,
                    "O lote tem " + submissions.size() + " eventos (máx. " + MAX_BATCH_SIZE + ")");
        }
        List<IngestOutcome> outcomes = new ArrayList<>(Collections.nCopies(submissions.size(), null));
        IntStream.range(0, submissions.size()).boxed()
                .sorted(Comparator.comparing(i -> submissions.get(i).event().occurredAt()))
                .forEach(i -> outcomes.set(i, processor.process(submissions.get(i))));
        return List.copyOf(outcomes);
    }
}
