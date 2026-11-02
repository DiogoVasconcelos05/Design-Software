package pt.lisboapedala.identity_access.query;

import pt.lisboapedala.identity_access.port.UserProfileView;

import java.util.Optional;

/**
 * Interface pública só de leitura para consulta de perfis de utilizador.
 */
public interface IdentityQuery {

    /**
     * Procura o perfil do utilizador pelo seu identificador único.
     *
     * @param userId Identificador do utilizador.
     * @return Perfil se encontrado.
     */
    Optional<UserProfileView> findById(String userId);

    /**
     * Procura o perfil do utilizador pelo número do cartão Navegante associado.
     *
     * @param naveganteCardNo Número do cartão Navegante.
     * @return Perfil se encontrado.
     */
    Optional<UserProfileView> findByNaveganteCardNo(String naveganteCardNo);
}