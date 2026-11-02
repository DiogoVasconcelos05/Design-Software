package pt.lisboapedala.api.rest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pt.lisboapedala.payment_invoicing.port.InvoiceView;
import pt.lisboapedala.payment_invoicing.port.PaymentMethodKind;
import pt.lisboapedala.payment_invoicing.port.PaymentService;

/** Endpoints 3 e 7 de ARCHITECTURE.md §7.3 (US-1, Req. 8b): meio de pagamento e faturas, em payment_invoicing. */
@RestController
@RequestMapping("/users/{userId}")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /** 3. {@code PUT /users/{userId}/payment-method} → 204. */
    @PutMapping("/payment-method")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void registerPaymentMethod(@PathVariable String userId, @Valid @RequestBody PaymentMethodRequest body) {
        paymentService.registerPaymentMethod(userId, body.kind(), body.token());
    }

    /** 7. {@code GET /users/{userId}/invoices?limit=20&offset=0} → 200, mais recente primeiro. */
    @GetMapping("/invoices")
    public List<InvoiceView> invoices(@PathVariable String userId,
                                      @RequestParam(defaultValue = "20") int limit,
                                      @RequestParam(defaultValue = "0") int offset) {
        return paymentService.invoicesOf(userId, limit, offset);
    }

    /** Corpo do endpoint 3. O {@code token} é a referência opaca do PSP, nunca o número do cartão. */
    public record PaymentMethodRequest(@NotNull PaymentMethodKind kind, @NotBlank String token) {
    }
}
