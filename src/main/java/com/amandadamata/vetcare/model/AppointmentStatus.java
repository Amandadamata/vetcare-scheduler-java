package com.amandadamata.vetcare.model;

import java.util.Set;

/**
 * Appointment life cycle.
 *
 * <pre>
 *                 +--> COMPLETED   (attended; keeps the slot occupied)
 *   SCHEDULED ----+--> NO_SHOW     (patient did not come; frees the slot)
 *                 +--> CANCELLED   (frees the slot)
 * </pre>
 *
 * Only {@code SCHEDULED} has outgoing transitions; the other three are final.
 */
public enum AppointmentStatus {
    SCHEDULED("Scheduled", true),
    COMPLETED("Completed", true),
    CANCELLED("Cancelled", false),
    NO_SHOW("No-show", false);

    private final String displayName;
    private final boolean occupiesSchedule;

    AppointmentStatus(String displayName, boolean occupiesSchedule) {
        this.displayName = displayName;
        this.occupiesSchedule = occupiesSchedule;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Whether the appointment still blocks its time slot. A completed appointment did use the
     * resource; a cancelled or no-show appointment leaves the remaining time free.
     */
    public boolean occupiesSchedule() {
        return occupiesSchedule;
    }

    public boolean isFinal() {
        return this != SCHEDULED;
    }

    public boolean canTransitionTo(AppointmentStatus target) {
        return this == SCHEDULED && Set.of(COMPLETED, NO_SHOW, CANCELLED).contains(target);
    }
}
