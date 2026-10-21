package com.amandadamata.vetcare.persistence;

import com.amandadamata.vetcare.model.Appointment;
import com.amandadamata.vetcare.model.ScheduleResource;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Converts data written by older versions of the application to the current format.
 *
 * <p>Version 1 (files without a {@code schemaVersion} field) had a single, implicit schedule, so
 * its appointments have no {@code resourceId}. They are all assigned to one new schedule named
 * {@value #MAIN_SCHEDULE_NAME}, which preserves the old behavior: they still conflict with each
 * other exactly as before. Nothing else changes; old statuses (scheduled, cancelled) are still
 * valid.
 */
final class ClinicDataMigration {
    static final String MAIN_SCHEDULE_NAME = "Main schedule";

    private ClinicDataMigration() {
    }

    static boolean isOutdated(ClinicSnapshot snapshot) {
        return snapshot.schemaVersion() < ClinicSnapshot.CURRENT_SCHEMA_VERSION;
    }

    static ClinicSnapshot toCurrentVersion(ClinicSnapshot old) {
        List<ScheduleResource> resources = new ArrayList<>(old.resources());
        List<Appointment> appointments = new ArrayList<>();

        boolean needsMainSchedule = old.appointments().stream()
                .anyMatch(appointment -> appointment.getResourceId() == null);
        UUID mainScheduleId = null;
        if (needsMainSchedule) {
            ScheduleResource mainSchedule = new ScheduleResource(UUID.randomUUID(), MAIN_SCHEDULE_NAME);
            resources.add(mainSchedule);
            mainScheduleId = mainSchedule.getId();
        }
        for (Appointment appointment : old.appointments()) {
            appointments.add(appointment.getResourceId() == null
                    ? appointment.assignedTo(mainScheduleId)
                    : appointment);
        }
        return new ClinicSnapshot(ClinicSnapshot.CURRENT_SCHEMA_VERSION,
                resources, old.owners(), old.animals(), appointments);
    }
}
