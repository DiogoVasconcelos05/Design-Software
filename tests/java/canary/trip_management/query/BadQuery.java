package canary.trip_management.query;

/** Canário: consulta que é um comando (void) e devolve um record com dados pessoais. */
public interface BadQuery {
    void save();

    record BadUsage(String userId, String route) { }
}
