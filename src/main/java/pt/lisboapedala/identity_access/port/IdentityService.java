package pt.lisboapedala.identity_access.port;

/**
 * Porta pública de escrita para a gestão de identidades dos utilizadores.
 */
public interface IdentityService {

    /**
     * Regista um novo utilizador no sistema.
     *
     * @param command Dados do comando de registo.
     * @return Perfil criado.
     */
    UserProfileView register(RegisterUserCommand command);
}