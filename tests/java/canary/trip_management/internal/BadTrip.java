package canary.trip_management.internal;

import canary.municipal_reporting.internal.Report;

/** Canário: trip_management não pode usar municipal_reporting (nem a sua internal). */
public class BadTrip {
    Report report = new Report();
}
