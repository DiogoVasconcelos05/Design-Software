package canary.tariff_pricing.internal;

/** Canário: I/O (java.io), relógio do servidor e estado estático mutável. */
public class ImpureTariff {
    static int calls;

    public long price() {
        calls++;
        boolean exists = new java.io.File("tarifas.csv").exists();
        return exists ? java.time.Instant.now().getEpochSecond() : 0;
    }
}
