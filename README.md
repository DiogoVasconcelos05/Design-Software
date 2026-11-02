# Design-Software

Membros do Grupo:

* Diogo Vasconcelos nº61809
* Rodrigo Santos nº61825

## Executar as fitness functions

É necessário ter o JDK 21 e o Maven instalados. Na raiz do projeto, executar:

```shell
mvn verify
```

O Maven compila o código de produção em `src/main/java` e descobre os testes de arquitetura em `tests/java`.

## Executar a aplicação

Com o JDK 21 e o Maven instalados, iniciar a aplicação com:

```shell
mvn spring-boot:run
```

O Spring inicializa o esquema em `src/main/resources/db/` e guarda a base de dados SQLite em `data/lisboapedala.db`. O estado do Actuator está disponível em `http://localhost:8080/actuator/health`.

Para crítica da especificação foi usado o Gemini flash 3.6 e Claude Opus 5.5.