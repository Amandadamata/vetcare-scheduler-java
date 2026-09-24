package com.amandadamata.vetcare.service;

import com.amandadamata.vetcare.model.AppointmentType;
import com.amandadamata.vetcare.model.TriagePriority;

import java.time.LocalDate;
import java.util.Map;

public record DailySummary(
        LocalDate date,
        long total,
        long scheduled,
        long cancelled,
        Map<TriagePriority, Long> byPriority,
        Map<AppointmentType, Long> byType) {
}

