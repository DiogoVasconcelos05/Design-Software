package canary.payment_invoicing.port;

public interface Charger {
    void charge(String tripId);
}
