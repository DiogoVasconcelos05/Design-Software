package pt.lisboapedala.identity_access.port;

import pt.lisboapedala.common.valueobject.Credential;
import pt.lisboapedala.common.valueobject.PassValidation;

/**
 * Porta pública de escrita para a gestão de identidades e validação de acessos.
 */
public interface IdentityService {

    /**
     * Regista um novo utilizador no sistema.
     *
     * @param command Dados do comando de registo.
     * @return Perfil criado.
     */
    UserProfileView register(RegisterUserCommand command);

    /**
     * Valida a credencial apresentada (QR Code ou Navegante) na abertura/desbloqueio.
     *
     * @param credential Credencial apresentada na doca.
     * @return Resultado da validação do passe.
     */
    PassValidation validateCredential(Credential credential);
}