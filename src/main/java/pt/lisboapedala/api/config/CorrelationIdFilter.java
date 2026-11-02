package pt.lisboapedala.api.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Filtro transversal da api (ARCHITECTURE.md §7.3): lê {@code X-Correlation-Id} (ou gera um UUID), põe-no em
 * MDC para todos os logs do pedido e devolve-o no cabeçalho da resposta. Corre antes de qualquer outro filtro,
 * para que também os erros e os logs dos módulos de domínio levem o identificador.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    /** Um valor recebido fora deste formato é substituído, para não injetar texto arbitrário nos logs. */
    private static final Pattern ACCEPTED = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String received = request.getHeader(HEADER);
        String correlationId = received != null && ACCEPTED.matcher(received).matches()
                ? received
                : UUID.randomUUID().toString();
        MDC.put(MDC_KEY, correlationId);
        response.setHeader(HEADER, correlationId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
