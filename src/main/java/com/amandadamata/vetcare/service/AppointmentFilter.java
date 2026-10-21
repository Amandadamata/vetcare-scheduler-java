package com.amandadamata.vetcare.service;

import com.amandadamata.vetcare.model.AppointmentStatus;
import com.amandadamata.vetcare.model.AppointmentType;
import com.amandadamata.vetcare.model.TriagePriority;

import java.time.LocalDate;
import java.util.UUID;

/** Optional filters; {@code null} means "any". */
public record AppointmentFilter(
        LocalDate date,
        AppointmentStatus status,
        AppointmentType type,
        TriagePriority priority,
        UUID resourceId) {

    public static AppointmentFilter all() {
        return new AppointmentFilter(null, null, null, null, null);
    }

    public static AppointmentFilter forResource(UUID resourceId, LocalDate date) {
        return new AppointmentFilter(date, null, null, null, resourceId);
    }
}
