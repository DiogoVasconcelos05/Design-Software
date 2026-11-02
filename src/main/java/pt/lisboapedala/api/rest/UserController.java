package pt.lisboapedala.api.rest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import pt.lisboapedala.common.exception.EntityNotFoundException;
import pt.lisboapedala.identity_access.port.IdentityService;
import pt.lisboapedala.identity_access.port.RegisterUserCommand;
import pt.lisboapedala.identity_access.port.UserProfileView;
import pt.lisboapedala.identity_access.query.IdentityQuery;

/** Endpoints 1 e 2 de ARCHITECTURE.md §7.3 (Req. 1): registo e perfil do utilizador, em identity_access. */
@RestController
@RequestMapping("/users")
public class UserController {

    private final IdentityService identityService;
    private final IdentityQuery identityQuery;

    public UserController(IdentityService identityService, IdentityQuery identityQuery) {
        this.identityService = identityService;
        this.identityQuery = identityQuery;
    }

    /** 1. {@code POST /users} → 201 com o perfil criado. */
    @PostMapping
    public ResponseEntity<UserProfileView> register(@RequestBody RegisterUserCommand command) {
        UserProfileView profile = identityService.register(command);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest()
                        .path("/{userId}").buildAndExpand(profile.userId()).toUri())
                .body(profile);
    }

    /** 2. {@code GET /users/{userId}} → 200 com o perfil, ou 404. */
    @GetMapping("/{userId}")
    public UserProfileView getProfile(@PathVariable String userId) {
        return identityQuery.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("Utilizador", userId));
    }
}
