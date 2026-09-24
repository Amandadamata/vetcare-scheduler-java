package com.amandadamata.vetcare.service;

import com.amandadamata.vetcare.exception.ValidationException;
import com.amandadamata.vetcare.model.Appointment;
import com.amandadamata.vetcare.model.AppointmentStatus;
import com.amandadamata.vetcare.model.AppointmentType;
import com.amandadamata.vetcare.model.TriagePriority;
import com.amandadamata.vetcare.repository.ClinicRepository;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class ReportService {
    private final ClinicRepository repository;

    public ReportService(ClinicRepository repository) {
        this.repository = repository;
    }

    public DailySummary dailySummary(LocalDate date) {
        if (date == null) {
            throw new ValidationException("A date is required for the daily summary.");
        }

        List<Appointment> appointments = repository.allAppointments().stream()
                .filter(appointment -> appointment.getStartTime().toLocalDate().equals(date))
                .toList();
        Map<TriagePriority, Long> byPriority = initializedPriorityCounts();
        Map<AppointmentType, Long> byType = initializedTypeCounts();

        appointments.forEach(appointment -> {
            byPriority.compute(appointment.getPriority(), (key, value) -> value + 1);
            byType.compute(appointment.getType(), (key, value) -> value + 1);
        });

        long cancelled = appointments.stream()
                .filter(appointment -> appointment.getStatus() == AppointmentStatus.CANCELLED)
                .count();
        return new DailySummary(
                date,
                appointments.size(),
                appointments.size() - cancelled,
                cancelled,
                Map.copyOf(byPriority),
                Map.copyOf(byType));
    }

    private Map<TriagePriority, Long> initializedPriorityCounts() {
        Map<TriagePriority, Long> counts = new EnumMap<>(TriagePriority.class);
        for (TriagePriority priority : TriagePriority.values()) {
            counts.put(priority, 0L);
        }
        return counts;
    }

    private Map<AppointmentType, Long> initializedTypeCounts() {
        Map<AppointmentType, Long> counts = new EnumMap<>(AppointmentType.class);
        for (AppointmentType type : AppointmentType.values()) {
            counts.put(type, 0L);
        }
        return counts;
    }
}
