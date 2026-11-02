package pt.lisboapedala.api.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import pt.lisboapedala.api.config.CorrelationIdFilter;
import pt.lisboapedala.common.exception.DomainException;
import pt.lisboapedala.common.exception.ErrorCode;

/**
 * Único tratador de erros da api (ARCHITECTURE.md §7.3): todas as falhas saem como
 * {@code application/problem+json} (RFC 7807) com {@code type}, {@code title}, {@code status}, {@code detail},
 * a extensão {@code code} (= {@link ErrorCode}) e o {@code correlationId} do pedido.
 *
 * <p>Os erros do próprio Spring MVC (JSON inválido, parâmetro em falta ou mal formado, caminho inexistente…)
 * são tratados pela classe-base e recebem aqui o {@code code} correspondente ao seu estado HTTP.
 */
@RestControllerAdvice
public class ProblemDetailsHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ProblemDetailsHandler.class);

    @ExceptionHandler(DomainException.class)
    ResponseEntity<ProblemDetail> handleDomain(DomainException ex) {
        HttpStatus status = statusOf(ex.getErrorCode());
        if (status.is5xxServerError()) {
            log.error("Pedido falhou com {}: {}", ex.getErrorCode(), ex.getMessage(), ex);
        } else {
            log.info("Pedido recusado com {}: {}", ex.getErrorCode(), ex.getMessage());
        }
        return problem(status, ex.getErrorCode(), ex.getMessage());
    }

    /** Os records dos contratos validam os argumentos no construtor com {@code IllegalArgumentException}. */
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ProblemDetail> handleInvalidArgument(IllegalArgumentException ex) {
        log.info("Pedido recusado com {}: {}", ErrorCode.VALIDATION, ex.getMessage());
        return problem(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION, ex.getMessage());
    }

    /** Falhas inesperadas: o detalhe interno fica só no log, ligado ao cliente pelo correlationId. */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
        log.error("Erro inesperado no pedido", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL, "Erro interno inesperado.");
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (response != null && response.getBody() instanceof ProblemDetail problem) {
            decorate(problem, codeOf(statusCode));
        }
        return response;
    }

    static HttpStatus statusOf(ErrorCode code) {
        return switch (code) {
            case VALIDATION -> HttpStatus.BAD_REQUEST;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT, DOCK_UNAVAILABLE -> HttpStatus.CONFLICT;
            case PASS_INVALID -> HttpStatus.FORBIDDEN;
            case PAYMENT_FAILED, TAX_SUBMISSION_FAILED -> HttpStatus.BAD_GATEWAY;
            case INTERNAL -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    private static ErrorCode codeOf(HttpStatusCode status) {
        if (status.value() == HttpStatus.NOT_FOUND.value()) return ErrorCode.NOT_FOUND;
        if (status.value() == HttpStatus.CONFLICT.value()) return ErrorCode.CONFLICT;
        if (status.is4xxClientError()) return ErrorCode.VALIDATION;
        return ErrorCode.INTERNAL;
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, ErrorCode code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        decorate(problem, code);
        return ResponseEntity.status(status).body(problem);
    }

    private static void decorate(ProblemDetail problem, ErrorCode code) {
        problem.setProperty("code", code.name());
        problem.setProperty("correlationId", MDC.get(CorrelationIdFilter.MDC_KEY));
    }
}
