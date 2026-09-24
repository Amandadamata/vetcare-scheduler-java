package com.amandadamata.vetcare.service;

import com.amandadamata.vetcare.model.AppointmentStatus;
import com.amandadamata.vetcare.model.AppointmentType;
import com.amandadamata.vetcare.model.TriagePriority;

import java.time.LocalDate;

public record AppointmentFilter(
        LocalDate date,
        AppointmentStatus status,
        AppointmentType type,
        TriagePriority priority) {

    public static AppointmentFilter all() {
        return new AppointmentFilter(null, null, null, null);
    }
}

