package pt.lisboapedala.maintenance_orders.port;

/**
 * Tipo de avaria de uma ordem de manutenção. As elétricas alertam falhas de bateria e travões (US-2);
 * os reportes de utilizadores podem ser de outro tipo.
 */
public enum FaultType {
    BATTERY,
    BRAKES,
    OTHER
}
