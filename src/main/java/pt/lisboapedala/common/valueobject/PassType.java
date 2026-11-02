package pt.lisboapedala.common.valueobject;

/**
 * Tipo de passe do utilizador, usado no cálculo tarifário (assinante anual ou ocasional).
 * Partilhado entre identity_access, trip_management, payment_invoicing e tariff_pricing.
 */
public enum PassType {
    ANNUAL,
    OCCASIONAL
}
