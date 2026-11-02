package pt.lisboapedala.identity_access.port;

import pt.lisboapedala.common.valueobject.Credential;
import pt.lisboapedala.common.valueobject.PassValidation;

/**
 * Porta publicada por identity_access para validar a credencial apresentada no desbloqueio (US-1, passo 1).
 * A implementação (incluindo o "mock" do cartão Navegante) é privada a identity_access.
 */
public interface CredentialValidator {

    /**
     * Valida a credencial e devolve o resultado; nunca devolve null.
     * Uma credencial desconhecida resulta em {@code PassValidationStatus.NOT_FOUND}, não numa exceção.
     */
    PassValidation validateCredential(Credential credential);
}
