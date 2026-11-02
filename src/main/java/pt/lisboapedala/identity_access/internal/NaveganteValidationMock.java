package pt.lisboapedala.identity_access.internal;

import org.springframework.stereotype.Component;
import pt.lisboapedala.common.valueobject.PassValidation;
import pt.lisboapedala.common.valueobject.PassValidationStatus;
import pt.lisboapedala.common.valueobject.PassType;

/**
 * Componente interno privado para simulação/mock da validação de elegibilidade do cartão Navegante.
 */
@Component
public class NaveganteValidationMock {

    public PassValidation validateCard(String userId, String naveganteCardNo, PassType currentPassType) {
        if (naveganteCardNo == null || naveganteCardNo.isBlank()) {
            return new PassValidation(userId, PassValidationStatus.NOT_FOUND, null, "Cartão Navegante não associado");
        }

        // Mock: Cartões começados por "999" simulam cartões expirados
        if (naveganteCardNo.startsWith("999")) {
            return new PassValidation(userId, PassValidationStatus.EXPIRED, null, "Passe Navegante expirado no gateway municipal");
        }

        // Mock: Cartões começados por "888" simulam falta de saldo/subscrição inativa
        if (naveganteCardNo.startsWith("888")) {
            return new PassValidation(userId, PassValidationStatus.INSUFFICIENT_BALANCE, null, "Saldo ou subscrição Navegante insuficiente");
        }

        return new PassValidation(userId, PassValidationStatus.VALID, currentPassType, "Passe Navegante válido");
    }
}