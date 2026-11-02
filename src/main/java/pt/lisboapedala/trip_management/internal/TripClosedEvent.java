package pt.lisboapedala.trip_management.internal;

import pt.lisboapedala.common.valueobject.TripSummary;

/**
 * Evento interno publicado no fecho da viagem; só é tratado depois do commit (ver {@link TripBillingDispatcher}).
 */
record TripClosedEvent(TripSummary summary) {
}
