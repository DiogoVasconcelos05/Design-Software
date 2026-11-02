package pt.lisboapedala.identity_access.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.lisboapedala.common.valueobject.Credential;
import pt.lisboapedala.common.valueobject.PassValidation;
import pt.lisboapedala.common.valueobject.PassValidationStatus;
import pt.lisboapedala.identity_access.persistence.UserEntity;
import pt.lisboapedala.identity_access.persistence.UserJdbcRepository;
import pt.lisboapedala.identity_access.port.CredentialValidator;
import pt.lisboapedala.identity_access.port.IdentityService;
import pt.lisboapedala.identity_access.port.RegisterUserCommand;
import pt.lisboapedala.identity_access.port.UserProfileView;
import pt.lisboapedala.identity_access.query.IdentityQuery;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Serviço principal do módulo identity_access que implementa o registo, a consulta
 * e a validação de credenciais via CredentialValidator.
 * Respeita a injeção obrigatória do Clock (FF-16) e logging via SLF4J (FF-19).
 */
@Service
public class DefaultIdentityService implements IdentityService, CredentialValidator, IdentityQuery {

    private static final Logger log = LoggerFactory.getLogger(DefaultIdentityService.class);

    private final UserJdbcRepository userRepository;
    private final NaveganteValidationMock naveganteValidationMock;
    private final Clock clock;

    public DefaultIdentityService(UserJdbcRepository userRepository,
                                  NaveganteValidationMock naveganteValidationMock,
                                  Clock clock) {
        this.userRepository = userRepository;
        this.naveganteValidationMock = naveganteValidationMock;
        this.clock = clock;
    }

    @Override
    @Transactional
    public UserProfileView register(RegisterUserCommand command) {
        log.info("A registar novo utilizador com email: {}", command.email());

        if (this.userRepository.findByEmail(command.email()).isPresent()) {
            throw new IllegalArgumentException("Utilizador com o email fornecido já existe");
        }

        String userId = "usr_" + UUID.randomUUID();
        Instant now = this.clock.instant();

        UserEntity entity = new UserEntity(
                userId,
                command.name(),
                command.email(),
                command.naveganteCardNo(),
                command.passType(),
                true,
                now
        );

        this.userRepository.save(entity);

        return toView(entity);
    }

    @Override
    public PassValidation validateCredential(Credential credential) {
        Objects.requireNonNull(credential, "credential não pode ser nulo");
        log.info("A validar credencial do tipo: {}", credential.kind());

        return switch (credential.kind()) {
            case QR_CODE -> {
                Optional<UserEntity> userOpt = this.userRepository.findById(credential.value());
                if (userOpt.isEmpty()) {
                    yield new PassValidation(credential.value(), PassValidationStatus.NOT_FOUND, null, "Utilizador não encontrado");
                }

                UserEntity user = userOpt.get();
                if (!user.active()) {
                    yield new PassValidation(user.id(), PassValidationStatus.SUSPENDED, null, "Conta suspensa");
                }

                yield new PassValidation(user.id(), PassValidationStatus.VALID, user.passType(), "Acesso QR Code autorizado");
            }
            case NAVEGANTE -> {
                Optional<UserEntity> userOpt = this.userRepository.findByNaveganteCardNo(credential.value());
                if (userOpt.isEmpty()) {
                    yield new PassValidation("unknown", PassValidationStatus.NOT_FOUND, null, "Cartão Navegante não associado a nenhuma conta");
                }

                UserEntity user = userOpt.get();
                if (!user.active()) {
                    yield new PassValidation(user.id(), PassValidationStatus.SUSPENDED, null, "Conta associada suspensa");
                }

                yield this.naveganteValidationMock.validateCard(user.id(), user.naveganteCardNo(), user.passType());
            }
        };
    }

    @Override
    public Optional<UserProfileView> findById(String userId) {
        return this.userRepository.findById(userId).map(this::toView);
    }

    @Override
    public Optional<UserProfileView> findByNaveganteCardNo(String naveganteCardNo) {
        return this.userRepository.findByNaveganteCardNo(naveganteCardNo).map(this::toView);
    }

    private UserProfileView toView(UserEntity entity) {
        return new UserProfileView(
                entity.id(),
                entity.name(),
                entity.email(),
                entity.naveganteCardNo(),
                entity.passType(),
                entity.active(),
                entity.createdAt()
        );
    }
}